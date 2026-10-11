# -*- coding: utf-8 -*-
"""用 Pillow 直接绘制两张核心可视化的 PNG 预览图。

为什么用 Pillow 直接绘制而不是把前端 SVG 转成 PNG：
本机没有可用的 SVG 光栅化工具（无 cairosvg / Inkscape / ImageMagick）。
Pillow 已随运行时提供，直接按同一套坐标与配色规则绘制 PNG，
既能作为交付物预览，也能与前端 SVG 的效果互相印证。

数据口径与前端、后端完全一致：
    distance = aisleGap * aisleNo + rowGap * rowNo
    AISLE_GAP = 2.5 米，ROW_GAP = 1.2 米

用法：
    python build/tools/render_preview_png.py
"""
import io
import os

from PIL import Image, ImageDraw, ImageFont

ROOT = r"D:\DeepSeekXM\XM01"
OUT_DIR = os.path.join(ROOT, "04-测试与实验", "05-可视化预览")

AISLES, ROWS = 10, 20
AISLE_GAP, ROW_GAP = 2.5, 1.2

CELL, GAP, PAD = 26, 6, 56
STEP = CELL + GAP
TITLE_H = 52
LEGEND_H = 74


def distance(x: int, y: int) -> float:
    return AISLE_GAP * x + ROW_GAP * y


def hsl_to_rgb(h: float, s: float, l: float):
    """HSL -> RGB（h 取 0..360，s/l 取 0..1）"""
    c = (1 - abs(2 * l - 1)) * s
    hp = (h % 360) / 60.0
    x = c * (1 - abs(hp % 2 - 1))
    if hp < 1:
        r, g, b = c, x, 0
    elif hp < 2:
        r, g, b = x, c, 0
    elif hp < 3:
        r, g, b = 0, c, x
    elif hp < 4:
        r, g, b = 0, x, c
    elif hp < 5:
        r, g, b = x, 0, c
    else:
        r, g, b = c, 0, x
    m = l - c / 2
    return (
        int(round((r + m) * 255)),
        int(round((g + m) * 255)),
        int(round((b + m) * 255)),
    )


def color_for(d: float, dmin: float, dmax: float):
    """与前端一致的配色：近端绿 -> 中段蓝 -> 远端橙"""
    ratio = 0.0 if dmax == dmin else (d - dmin) / (dmax - dmin)
    if ratio < 0.5:
        hue = 152 + ratio * 2 * (210 - 152)
    else:
        hue = 210 + (ratio - 0.5) * 2 * (390 - 210)
    return hsl_to_rgb(hue, (62 + ratio * 8) / 100.0, (62 - ratio * 12) / 100.0)


def load_font(size: int, bold: bool = False):
    """按优先级挑选可用的中文字体"""
    candidates = [
        (r"C:\Windows\Fonts\msyhbd.ttc" if bold else r"C:\Windows\Fonts\msyh.ttc"),
        r"C:\Windows\Fonts\msyh.ttc",
        r"C:\Windows\Fonts\simhei.ttf",
        r"C:\Windows\Fonts\simsun.ttc",
    ]
    for path in candidates:
        if os.path.exists(path):
            try:
                return ImageFont.truetype(path, size)
            except Exception:
                continue
    return ImageFont.load_default()


FONT_TITLE = load_font(20, bold=True)
FONT_LABEL = load_font(11)
FONT_SMALL = load_font(13)
FONT_SEQ = load_font(13, bold=True)


def canvas_size():
    width = PAD * 2 + (AISLES + 1) * STEP
    height = TITLE_H + PAD + (ROWS + 1) * STEP + LEGEND_H
    return width, height


def draw_grid_base(draw, occupied=None, picks=None, visited=None):
    """绘制货位网格底图，返回每个格子的中心坐标表"""
    occupied = occupied or set()
    picks = picks or set()
    visited = visited or {}
    dmin, dmax = distance(1, 1), distance(AISLES, ROWS)
    centers = {}

    # 坐标标签
    for x in range(1, AISLES + 1):
        left = PAD + x * STEP
        draw.text(
            (left + CELL / 2, TITLE_H + 18),
            "A%d" % x,
            font=FONT_LABEL,
            fill=(168, 171, 178),
            anchor="mm",
        )
    for y in range(1, ROWS + 1, 2):
        top = TITLE_H + PAD + y * STEP
        draw.text(
            (PAD - 12, top + CELL / 2),
            str(y),
            font=FONT_LABEL,
            fill=(168, 171, 178),
            anchor="rm",
        )

    # 货位
    for x in range(1, AISLES + 1):
        for y in range(1, ROWS + 1):
            left = PAD + x * STEP
            top = TITLE_H + PAD + y * STEP
            cx, cy = left + CELL / 2, top + CELL / 2
            centers[(x, y)] = (cx, cy)

            if (x, y) in visited:
                fill = (219, 234, 254)
                outline = (37, 99, 235)
                width = 2
            elif (x, y) in picks:
                fill = (254, 243, 199)
                outline = (245, 158, 11)
                width = 2
            else:
                fill = color_for(distance(x, y), dmin, dmax)
                outline = (31, 41, 55) if (x, y) in occupied else None
                width = 2

            draw.rounded_rectangle(
                [left, top, left + CELL, top + CELL],
                radius=4,
                fill=fill,
                outline=outline,
                width=width if outline else 0,
            )

            # 序号
            if (x, y) in visited:
                draw.text(
                    (cx, cy), str(visited[(x, y)]), font=FONT_SEQ, fill=(29, 78, 216), anchor="mm"
                )

    # 出入口
    io_left, io_top = PAD, TITLE_H + PAD
    draw.rounded_rectangle(
        [io_left, io_top, io_left + CELL, io_top + CELL], radius=5, fill=(31, 41, 55)
    )
    draw.text(
        (io_left + CELL / 2, io_top + CELL / 2),
        "I/O",
        font=FONT_LABEL,
        fill=(255, 255, 255),
        anchor="mm",
    )
    centers[(0, 0)] = (io_left + CELL / 2, io_top + CELL / 2)
    return centers


def render_heat_map():
    width, height = canvas_size()
    img = Image.new("RGB", (width, height), (255, 255, 255))
    draw = ImageDraw.Draw(img)

    draw.text((PAD, 20), "货位距离热力图", font=FONT_TITLE, fill=(31, 41, 55))
    draw.text(
        (PAD + 172, 26),
        "颜色越绿表示离出入口越近 —— 货位分配算法会把高频商品放到这些格子里",
        font=FONT_SMALL,
        fill=(144, 147, 153),
    )

    dmin, dmax = distance(1, 1), distance(AISLES, ROWS)
    centers = draw_grid_base(draw)

    # 图例
    legend_y = height - LEGEND_H + 16
    draw.text((PAD, legend_y), "距出入口：近", font=FONT_SMALL, fill=(96, 98, 102))
    lx = PAD + 108
    for i in range(5):
        d = dmin + (dmax - dmin) * i / 4
        draw.rounded_rectangle(
            [lx, legend_y - 2, lx + 26, legend_y + 12], radius=2, fill=color_for(d, dmin, dmax)
        )
        lx += 28
    draw.text((lx + 6, legend_y), "远（%.1f 米）" % dmax, font=FONT_SMALL, fill=(96, 98, 102))
    draw.text(
        (PAD, legend_y + 22),
        "货位总数 %d 个 · 最短距离 %.2f 米 · 最长距离 %.2f 米"
        % (AISLES * ROWS, dmin, dmax),
        font=FONT_SMALL,
        fill=(144, 147, 153),
    )

    out = os.path.join(OUT_DIR, "货位距离热力图.png")
    img.save(out, "PNG")
    print("saved:", out, "(%dx%d)" % (width, height))
    return out


def render_pick_route(route, picks, distance_m):
    width, height = canvas_size()
    img = Image.new("RGB", (width, height), (255, 255, 255))
    draw = ImageDraw.Draw(img)

    draw.text((PAD, 20), "拣货路径可视化", font=FONT_TITLE, fill=(31, 41, 55))
    draw.text(
        (PAD + 152, 26),
        "蚁群算法规划结果：从出入口出发遍历全部拣货点再返回",
        font=FONT_SMALL,
        fill=(144, 147, 153),
    )

    # 计算访问序号
    visited = {}
    seq = 0
    for p in route:
        if p == (0, 0) or p in visited:
            continue
        seq += 1
        visited[p] = seq

    centers = draw_grid_base(draw, picks=set(picks), visited=visited)

    # 先画白底描边，再画彩色路径，形成清晰的路径线
    points = [centers[p] for p in route if p in centers]
    if len(points) > 1:
        draw.line(points, fill=(255, 255, 255), width=9, joint="curve")
        draw.line(points, fill=(37, 99, 235), width=4, joint="curve")

    # 访问序号圆点盖在路径之上
    for (x, y), s in sorted(visited.items(), key=lambda kv: kv[1]):
        cx, cy = centers[(x, y)]
        r = 10
        draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(37, 99, 235), outline=(255, 255, 255), width=2)
        draw.text((cx, cy), str(s), font=FONT_SEQ, fill=(255, 255, 255), anchor="mm")

    # 起点标记
    io_cx, io_cy = centers[(0, 0)]
    draw.ellipse([io_cx - 5, io_cy - 5, io_cx + 5, io_cy + 5], fill=(34, 211, 238),
                 outline=(255, 255, 255), width=2)

    # 图例：第一行放图例项，第二行单独放汇总数据，避免文字重叠
    legend_y = height - LEGEND_H + 20
    draw.rounded_rectangle([PAD, legend_y - 2, PAD + 14, legend_y + 12], radius=3, fill=(31, 41, 55))
    draw.text((PAD + 20, legend_y), "出入口", font=FONT_SMALL, fill=(96, 98, 102))
    draw.rounded_rectangle([PAD + 84, legend_y - 2, PAD + 98, legend_y + 12], radius=3,
                           fill=(254, 243, 199), outline=(245, 158, 11))
    draw.text((PAD + 104, legend_y), "拣货点", font=FONT_SMALL, fill=(96, 98, 102))
    draw.line([PAD + 168, legend_y + 5, PAD + 194, legend_y + 5], fill=(37, 99, 235), width=4)
    draw.text((PAD + 200, legend_y), "行走路径", font=FONT_SMALL, fill=(96, 98, 102))

    draw.text(
        (PAD, legend_y + 24),
        "路径长度 %.2f 米 · 拣货点 %d 个 · 顺序 %s"
        % (distance_m, len(picks), " → ".join("(%d,%d)" % p for p in route)),
        font=load_font(13, bold=True),
        fill=(37, 99, 235),
    )

    out = os.path.join(OUT_DIR, "拣货路径可视化.png")
    img.save(out, "PNG")
    print("saved:", out, "(%dx%d)" % (width, height))
    return out


def main():
    os.makedirs(OUT_DIR, exist_ok=True)

    render_heat_map()

    # 与后端实际规划结果一致：出库单 3 个拣货点，蚁群算法给出的顺序
    route = [(0, 0), (1, 3), (1, 4), (1, 1), (0, 0)]
    picks = [(1, 3), (1, 4), (1, 1)]
    d = 0.0
    for i in range(len(route) - 1):
        (x1, y1), (x2, y2) = route[i], route[i + 1]
        d += AISLE_GAP * abs(x1 - x2) + ROW_GAP * abs(y1 - y2)
    render_pick_route(route, picks, d)
    print("route distance: %.2f m" % d)


if __name__ == "__main__":
    main()
