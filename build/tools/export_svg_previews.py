# -*- coding: utf-8 -*-
"""导出前端两个核心可视化的独立 SVG 预览图。

目的：仓库平面图与拣货路径是纯 SVG 渲染，可以直接在浏览器或图片查看器中打开。
把它们导出为独立文件，答辩或给别人看效果时无需启动整套系统。

数据来源与前端一致：
  - 货位坐标与距离由仓库布局算出（distance = aisleGap * x + rowGap * y）
  - 拣货路径取自后端实际的算法规划结果（见脚本内的示例顺序）

用法：
    python build/tools/export_svg_previews.py
"""
import io
import os

ROOT = r"D:\DeepSeekXM\XM01"
OUT_DIR = os.path.join(ROOT, "04-测试与实验", "05-可视化预览")

AISLES, ROWS = 10, 20
AISLE_GAP, ROW_GAP = 2.5, 1.2

CELL, GAP, PAD = 24, 5, 42
STEP = CELL + GAP


def distance(x: int, y: int) -> float:
    return AISLE_GAP * x + ROW_GAP * y


def color_for(d: float, dmin: float, dmax: float) -> str:
    """与前端实现一致的 HSL 插值：近端绿 -> 中段蓝 -> 远端橙"""
    ratio = 0.0 if dmax == dmin else (d - dmin) / (dmax - dmin)
    if ratio < 0.5:
        hue = 152 + ratio * 2 * (210 - 152)
    else:
        hue = 210 + (ratio - 0.5) * 2 * (390 - 210)
    hue %= 360
    sat = 62 + ratio * 8
    light = 62 - ratio * 12
    return "hsl(%d, %d%%, %d%%)" % (hue, sat, light)


def svg_header(width, height, title):
    return [
        '<svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" viewBox="0 0 %d %d" '
        'font-family="Segoe UI, Microsoft YaHei, sans-serif">' % (width, height, width, height),
        '<defs>',
        '<linearGradient id="pathGradient" x1="0" y1="0" x2="1" y2="1">',
        '<stop offset="0%%" stop-color="#2563eb"/><stop offset="100%%" stop-color="#22d3ee"/>',
        '</linearGradient>',
        '</defs>',
        '<rect width="%d" height="%d" fill="#ffffff"/>' % (width, height),
        '<text x="16" y="26" font-size="15" font-weight="600" fill="#1f2937">%s</text>' % title,
    ]


def build_heat_map() -> str:
    width = PAD * 2 + (AISLES + 1) * STEP
    height = PAD * 2 + (ROWS + 1) * STEP + 30
    dmin, dmax = distance(1, 1), distance(AISLES, ROWS)

    out = svg_header(width, height, "货位距离热力图（颜色越绿越靠近出入口）")
    out.append('<g font-size="10" fill="#a8abb2" font-family="Consolas, monospace">')

    # 坐标标签
    for x in range(1, AISLES + 1):
        left = PAD + x * STEP + CELL / 2
        out.append('<text x="%.1f" y="%d" text-anchor="middle">A%d</text>' % (left, PAD + 20, x))
    for y in range(1, ROWS + 1, 2):
        top = PAD + y * STEP + CELL / 2 + 4
        out.append('<text x="%d" y="%.1f" text-anchor="end">%d</text>' % (PAD - 10, top, y))
    out.append('</g>')

    # 货位格子
    for x in range(1, AISLES + 1):
        for y in range(1, ROWS + 1):
            d = distance(x, y)
            left = PAD + x * STEP
            top = PAD + y * STEP + 12
            out.append(
                '<rect x="%.1f" y="%.1f" width="%d" height="%d" rx="4" fill="%s">'
                '<title>%s 坐标(%d,%d) 距离 %.2f 米</title></rect>'
                % (
                    left,
                    top,
                    CELL,
                    CELL,
                    color_for(d, dmin, dmax),
                    "A%02d-%02d" % (x, y),
                    x,
                    y,
                    d,
                )
            )

    # 出入口
    io_left, io_top = PAD, PAD + 12
    out.append(
        '<rect x="%.1f" y="%.1f" width="%d" height="%d" rx="5" fill="#1f2937"/>'
        % (io_left, io_top, CELL, CELL)
    )
    out.append(
        '<text x="%.1f" y="%.1f" text-anchor="middle" font-size="9" font-weight="700" '
        'fill="#ffffff" font-family="Consolas, monospace">I/O</text>'
        % (io_left + CELL / 2, io_top + CELL / 2 + 3)
    )

    # 图例
    legend_y = height - 14
    out.append(
        '<text x="16" y="%d" font-size="11" fill="#909399">距出入口：近</text>' % legend_y
    )
    lx = 118
    for i in range(5):
        d = dmin + (dmax - dmin) * i / 4
        out.append(
            '<rect x="%d" y="%d" width="22" height="11" rx="2" fill="%s"/>'
            % (lx, legend_y - 9, color_for(d, dmin, dmax))
        )
        lx += 24
    out.append('<text x="%d" y="%d" font-size="11" fill="#909399">远（%.1f 米）</text>' % (lx + 4, legend_y, dmax))
    out.append(
        '<text x="%d" y="%d" font-size="11" fill="#606266">货位总数 %d 个</text>'
        % (width - 130, legend_y, AISLES * ROWS)
    )

    out.append("</svg>")
    return "\n".join(out)


def build_pick_route(route, picks, distance_m, title) -> str:
    width = PAD * 2 + (AISLES + 1) * STEP
    height = PAD * 2 + (ROWS + 1) * STEP + 30

    def center(x, y):
        return PAD + x * STEP + CELL / 2, PAD + y * STEP + 12 + CELL / 2

    out = svg_header(width, height, title)

    # 底色网格
    for x in range(1, AISLES + 1):
        for y in range(1, ROWS + 1):
            left = PAD + x * STEP
            top = PAD + y * STEP + 12
            fill = "#eef2f7"
            stroke = "#dbe1ea"
            if (x, y) in picks:
                fill, stroke = "#fef3c7", "#f59e0b"
            out.append(
                '<rect x="%.1f" y="%.1f" width="%d" height="%d" rx="4" fill="%s" '
                'stroke="%s" stroke-width="1"/>' % (left, top, CELL, CELL, fill, stroke)
            )

    # 路径
    pts = " ".join("%.1f,%.1f" % center(x, y) for x, y in route)
    out.append(
        '<polyline points="%s" fill="none" stroke="#ffffff" stroke-width="7" '
        'stroke-linecap="round" stroke-linejoin="round" opacity="0.92"/>' % pts
    )
    out.append(
        '<polyline points="%s" fill="none" stroke="url(#pathGradient)" stroke-width="3" '
        'stroke-linecap="round" stroke-linejoin="round" stroke-dasharray="6 4"/>' % pts
    )

    # 访问序号
    seen = {}
    seq = 0
    for p in route:
        if p == (0, 0) or p in seen:
            continue
        seq += 1
        seen[p] = seq
    for (x, y), s in seen.items():
        cx, cy = center(x, y)
        out.append(
            '<circle cx="%.1f" cy="%.1f" r="8.5" fill="#2563eb"/>'
            '<text x="%.1f" y="%.1f" text-anchor="middle" font-size="10" font-weight="700" '
            'fill="#ffffff" font-family="Consolas, monospace">%d</text>' % (cx, cy, cx, cy + 3.5, s)
        )

    # 出入口与端点
    cx, cy = center(0, 0)
    out.append('<rect x="%.1f" y="%.1f" width="%d" height="%d" rx="5" fill="#1f2937"/>'
               % (PAD, PAD + 12, CELL, CELL))
    out.append('<text x="%.1f" y="%.1f" text-anchor="middle" font-size="9" font-weight="700" '
               'fill="#ffffff" font-family="Consolas, monospace">I/O</text>' % (cx, cy + 3))
    out.append('<circle cx="%.1f" cy="%.1f" r="4.5" fill="#22d3ee" stroke="#ffffff" stroke-width="1.6"/>'
               % (cx, cy))

    # 图例
    legend_y = height - 14
    out.append('<text x="16" y="%d" font-size="11" fill="#606266">'
               '■ 出入口　■ 拣货点　━ 行走路径　● 访问序号</text>' % legend_y)
    out.append(
        '<text x="%d" y="%d" font-size="12" font-weight="700" fill="#2563eb">'
        '路径长度 %.2f 米 · 拣货点 %d 个</text>' % (width - 250, legend_y, distance_m, len(picks))
    )

    out.append("</svg>")
    return "\n".join(out)


def main():
    os.makedirs(OUT_DIR, exist_ok=True)

    # 1. 热力图
    p1 = os.path.join(OUT_DIR, "货位距离热力图.svg")
    with io.open(p1, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(build_heat_map())
    print("saved:", p1)

    # 2. 拣货路径：使用后端实际规划结果（出库单 3 个拣货点）
    route = [(0, 0), (1, 3), (1, 4), (1, 1), (0, 0)]
    picks = {(1, 3), (1, 4), (1, 1)}
    d = 0.0
    for i in range(len(route) - 1):
        (x1, y1), (x2, y2) = route[i], route[i + 1]
        d += AISLE_GAP * abs(x1 - x2) + ROW_GAP * abs(y1 - y2)
    p2 = os.path.join(OUT_DIR, "拣货路径可视化.svg")
    with io.open(p2, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(build_pick_route(route, picks, d, "拣货路径可视化（蚁群算法规划结果）"))
    print("saved:", p2)
    print("route distance: %.2f m" % d)


if __name__ == "__main__":
    main()
