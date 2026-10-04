"""
把 artwork/app_icon.svg 里的 path 数据真实采样出来并栅格化成 PNG。

目的不是做一个通用 SVG 渲染器，而是**验证图标几何是否正确**：
  · 咬痕有没有被正确"挖"进饼干轮廓（而不是凸出来一块）
  · 所有元素是否都落在自适应图标的安全区（21..87）内
  · 两条弧的方向标注（sweep / large-arc）对不对

支持的命令：M m L l A a C c Z z —— 恰好覆盖本图标用到的全部 path 数据。
"""

import math
import re
import sys

from PIL import Image, ImageDraw

VIEWPORT = 108.0
SS = 8  # 超采样倍数，保证曲线平滑


# --------------------------------------------------------------------------
# 一个极简 SVG path 采样器
# --------------------------------------------------------------------------

TOKEN_RE = re.compile(r"[MmLlAaCcZzHhVv]|-?\d*\.?\d+(?:[eE][-+]?\d+)?")


def _arc_points(x0, y0, rx, ry, phi_deg, large_arc, sweep, x1, y1, steps=64):
    """按 SVG 规范 F.6.5 把端点式圆弧转成圆心式，再等角采样。"""
    if rx == 0 or ry == 0:
        return [(x1, y1)]

    phi = math.radians(phi_deg)
    cos_p, sin_p = math.cos(phi), math.sin(phi)

    # 步骤 1：把端点变换到椭圆坐标系
    dx2 = (x0 - x1) / 2.0
    dy2 = (y0 - y1) / 2.0
    x1p = cos_p * dx2 + sin_p * dy2
    y1p = -sin_p * dx2 + cos_p * dy2

    rx, ry = abs(rx), abs(ry)
    lam = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry)
    if lam > 1:
        s = math.sqrt(lam)
        rx *= s
        ry *= s

    # 步骤 2：求圆心
    num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p
    den = rx * rx * y1p * y1p + ry * ry * x1p * x1p
    coef = math.sqrt(max(num / den, 0.0))
    if large_arc == sweep:
        coef = -coef
    cxp = coef * rx * y1p / ry
    cyp = -coef * ry * x1p / rx

    cx = cos_p * cxp - sin_p * cyp + (x0 + x1) / 2.0
    cy = sin_p * cxp + cos_p * cyp + (y0 + y1) / 2.0

    # 步骤 3：起始角与扫过角
    def angle(ux, uy, vx, vy):
        dot = ux * vx + uy * vy
        norm = math.hypot(ux, uy) * math.hypot(vx, vy)
        if norm == 0:
            return 0.0
        a = math.acos(max(-1.0, min(1.0, dot / norm)))
        return -a if (ux * vy - uy * vx) < 0 else a

    theta1 = angle(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry)
    dtheta = angle((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)

    if not sweep and dtheta > 0:
        dtheta -= 2 * math.pi
    elif sweep and dtheta < 0:
        dtheta += 2 * math.pi

    pts = []
    for i in range(1, steps + 1):
        t = theta1 + dtheta * i / steps
        ex = rx * math.cos(t)
        ey = ry * math.sin(t)
        pts.append((
            cos_p * ex - sin_p * ey + cx,
            sin_p * ex + cos_p * ey + cy,
        ))
    return pts


def sample_path(d, steps=64):
    """返回若干条子路径，每条是 (x, y) 点列。"""
    tokens = TOKEN_RE.findall(d)
    subs, cur = [], []
    i = 0
    x = y = 0.0
    start_x = start_y = 0.0
    cmd = None

    def num():
        nonlocal i
        v = float(tokens[i])
        i += 1
        return v

    while i < len(tokens):
        t = tokens[i]
        if re.match(r"[A-Za-z]", t):
            cmd = t
            i += 1
        # 否则沿用上一个命令（隐式重复）

        if cmd in ("M", "m"):
            if cmd == "m":
                x += num(); y += num()
            else:
                x = num(); y = num()
            if cur:
                subs.append(cur)
            cur = [(x, y)]
            start_x, start_y = x, y
            cmd = "L" if cmd == "M" else "l"

        elif cmd in ("L", "l"):
            if cmd == "l":
                x += num(); y += num()
            else:
                x = num(); y = num()
            cur.append((x, y))

        elif cmd in ("H", "h"):
            v = num()
            x = x + v if cmd == "h" else v
            cur.append((x, y))

        elif cmd in ("V", "v"):
            v = num()
            y = y + v if cmd == "v" else v
            cur.append((x, y))

        elif cmd in ("C", "c"):
            if cmd == "c":
                x1, y1 = x + num(), y + num()
                x2, y2 = x + num(), y + num()
                ex, ey = x + num(), y + num()
            else:
                x1, y1 = num(), num()
                x2, y2 = num(), num()
                ex, ey = num(), num()
            for k in range(1, steps + 1):
                u = k / steps
                m = 1 - u
                cur.append((
                    m**3 * x + 3 * m**2 * u * x1 + 3 * m * u**2 * x2 + u**3 * ex,
                    m**3 * y + 3 * m**2 * u * y1 + 3 * m * u**2 * y2 + u**3 * ey,
                ))
            x, y = ex, ey

        elif cmd in ("A", "a"):
            rx, ry = num(), num()
            rot = num()
            large = int(num())
            sweep = int(num())
            if cmd == "a":
                ex, ey = x + num(), y + num()
            else:
                ex, ey = num(), num()
            cur.extend(_arc_points(x, y, rx, ry, rot, large, sweep, ex, ey, steps))
            x, y = ex, ey

        elif cmd in ("Z", "z"):
            if cur:
                cur.append((start_x, start_y))
                subs.append(cur)
                cur = []
            x, y = start_x, start_y

        else:
            raise ValueError(f"unsupported command: {cmd}")

    if cur:
        subs.append(cur)
    return subs


def scale(pts, k):
    return [(px * k, py * k) for px, py in pts]


# --------------------------------------------------------------------------
# 复刻 SVG 里的图形定义
# --------------------------------------------------------------------------

COOKIE_D = "M76,58.88 A9,9 0 0,0 82.95,69.78 A14,14 0 1,1 76,58.88 Z"
GLOBE_CIRCLE_D = "M23,46 a21,21 0 1,0 42,0 a21,21 0 1,0 -42,0 Z"
EQUATOR_D = "M23,46 L65,46"
MERIDIAN_D = ("M34,46 C34,36.6 39,25 44,25 C49,25 54,36.6 54,46 "
              "C54,55.4 49,67 44,67 C39,67 34,55.4 34,46 Z")
CHIPS = [(63, 66, 2.6), (74, 68, 2.6), (62, 78, 2.6), (73, 77, 2.6)]


def lerp(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def render(size=512, out="app_icon_preview.png", with_safezone=False):
    W = H = int(VIEWPORT * SS)

    img = Image.new("RGB", (W, H), (255, 255, 255))
    d = ImageDraw.Draw(img)

    # ---- 背景渐变（对角 #2B3A67 -> #1E4E9C -> #1565C0）----
    c0, c1, c2 = (43, 58, 103), (30, 78, 156), (21, 101, 192)
    for yy in range(H):
        for xx in range(W):
            t = (xx / W + yy / H) / 2.0
            if t < 0.55:
                col = lerp(c0, c1, t / 0.55)
            else:
                col = lerp(c1, c2, (t - 0.55) / 0.45)
            img.putpixel((xx, yy), col)

    white = (255, 255, 255)

    # ---- 地球（描边）----
    for dd in (GLOBE_CIRCLE_D, EQUATOR_D, MERIDIAN_D):
        for sub in sample_path(dd):
            d.line(scale(sub, SS), fill=white, width=max(1, int(4 * SS)), joint="curve")

    # ---- 饼干：填充（渐变近似为纯色块，只为看形状）----
    cookie_subs = sample_path(COOKIE_D)
    for sub in cookie_subs:
        d.polygon(scale(sub, SS), fill=(224, 154, 56))
    # 饼干描边
    for sub in cookie_subs:
        d.line(scale(sub, SS), fill=white, width=max(1, int(3 * SS)), joint="curve")

    # ---- 巧克力豆 ----
    for cx, cy, r in CHIPS:
        d.ellipse(
            [(cx - r) * SS, (cy - r) * SS, (cx + r) * SS, (cy + r) * SS],
            fill=(107, 62, 29),
        )

    # ---- 安全区参考线（可选）----
    if with_safezone:
        d.rectangle([21 * SS, 21 * SS, 87 * SS, 87 * SS], outline=(255, 0, 0), width=SS)

    # ---- 缩回目标尺寸 ----
    img = img.resize((size, size), Image.LANCZOS)
    img.save(out)

    # ---- 报告几何统计 ----
    print(f"saved {out}  ({size}x{size})")
    allpts = []
    for dd in (COOKIE_D, GLOBE_CIRCLE_D, EQUATOR_D, MERIDIAN_D):
        for sub in sample_path(dd):
            allpts.extend(sub)
    xs = [p[0] for p in allpts]
    ys = [p[1] for p in allpts]
    print(f"geometry bbox: x {min(xs):.2f}..{max(xs):.2f}   y {min(ys):.2f}..{max(ys):.2f}")
    print("adaptive-icon safe zone: 21..87")
    out_of = [(p) for p in allpts if not (21 <= p[0] <= 87 and 21 <= p[1] <= 87)]
    print(f"points outside safe zone: {len(out_of)}")
    if out_of:
        print("  ", out_of[:5])

    # 咬痕验证：若干探针点是否落在饼干内
    def inside(px, py, subs):
        cnt = 0
        for sub in subs:
            inside_sub = False
            j = len(sub) - 1
            for i2 in range(len(sub)):
                xi, yi = sub[i2]
                xj, yj = sub[j]
                if (yi > py) != (yj > py):
                    xint = (xj - xi) * (py - yi) / (yj - yi) + xi
                    if px < xint:
                        inside_sub = not inside_sub
                j = i2
            if inside_sub:
                cnt += 1
        return cnt % 2 == 1

    probes = {
        "cookie centre (69,71)": (69, 71),
        "bite centre (84.7,61)": (84.75, 60.97),
        "notch interior (79,63)": (79, 63),
        "cookie lower-left (62,80)": (62, 80),
        "outside (95,40)": (95, 40),
    }
    print("cookie fill probes (True = filled):")
    for name, (px, py) in probes.items():
        print(f"   {name:28s} -> {inside(px, py, cookie_subs)}")


if __name__ == "__main__":
    render(512, "app_icon_preview.png", with_safezone=False)
    render(512, "app_icon_preview_safezone.png", with_safezone=True)
