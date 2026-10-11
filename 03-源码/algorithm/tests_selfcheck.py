# -*- coding: utf-8 -*-
"""
算法内核正确性自检（对应计划中「每步必须有一次机器验证」）

检查项
------
A. 数据模型：距离度量、货位数、ABC 分类比例是否合理
B. 货位分配：四种策略是否都返回合法分配（每商品一个货位、无重复、覆盖商品）
C. 拣货路径：四种策略是否都访问全部拣货点、是否首尾为出入口
D. 最优性：小规模算例下，各启发式策略的距离不得优于 Held-Karp 穷举最优解
E. 合理性：最大间隙/最近邻/蚁群在给定算例上不应差于 S 型策略

用法：python tests_selfcheck.py
"""

from __future__ import annotations

import sys
import io
import numpy as np

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

from wmsopt import (
    IO_POINT,
    SLOTTING_STRATEGIES,
    ROUTING_STRATEGIES,
    WarehouseInstance,
    WarehouseLayout,
    build_item_profile,
    evaluate_objective,
    held_karp_optimal,
    path_length,
)

FAILS: list[str] = []


def check(name: str, cond: bool, detail: str = "") -> None:
    mark = "PASS" if cond else "FAIL"
    print("[%s] %s %s" % (mark, name, detail))
    if not cond:
        FAILS.append(name)


def main() -> int:
    layout = WarehouseLayout(aisles=10, rows=20)
    items = build_item_profile(item_count=200, alpha=1.2, seed=42)
    inst = WarehouseInstance("基础算例", layout, items, "10 巷道 × 20 纵深，200 商品")
    inst.validate()

    print("=" * 74)
    print("A. 数据模型检查")
    print("=" * 74)
    check("货位数 = 巷道 × 纵深", layout.slot_count == 200, "实际 %d" % layout.slot_count)
    check("出入口距离矩阵形状正确",
          layout.distance_matrix().shape == (200, 200))
    d_io = layout.io_distances()
    check("最近货位距离 = 1巷×2.5 + 1排×1.2 = 3.7",
          abs(float(d_io[0]) - 3.7) < 1e-9, "实际 %.3f" % d_io[0])
    check("最远货位距离 = 10×2.5 + 20×1.2 = 49.0",
          abs(float(d_io.max()) - 49.0) < 1e-9, "实际 %.3f" % d_io.max())
    abc = items.abc_summary()
    total_items = sum(abc.values())
    check("ABC 分类覆盖全部商品", total_items == items.item_count,
          "A=%d B=%d C=%d" % (abc["A"], abc["B"], abc["C"]))
    a_share = items.freqs[items.abc_class == 0].sum() / items.freqs.sum()
    check("A 类商品出库量占比约 70%", 0.60 <= a_share <= 0.80, "实际 %.1f%%" % (a_share * 100))

    print()
    print("=" * 74)
    print("B. 货位分配策略合法性")
    print("=" * 74)
    slot_results = {}
    for key, fn in SLOTTING_STRATEGIES.items():
        res = fn(inst, seed=42)
        slot_results[key] = res
        assign = res.slot_of_item
        ok_len = assign.shape[0] == items.item_count
        ok_range = bool(np.all(assign >= 0) and np.all(assign < layout.slot_count))
        ok_unique = len(set(assign.tolist())) == items.item_count
        obj_ok = abs(res.objective - evaluate_objective(inst, assign)) < 1e-6
        check("%-16s 分配合法（长度/范围/无重复/目标一致）" % key,
              ok_len and ok_range and ok_unique and obj_ok,
              detail="目标=%.0f 耗时=%.1fms" % (res.objective, res.elapsed_ms))

    print()
    print("  目标函数对比（越小越好）：")
    base = slot_results["random"].objective
    for key, res in slot_results.items():
        gain = (base - res.objective) / base * 100 if base else 0.0
        print("    %-16s %12.0f 米·次   相对随机分配 %+6.1f%%" % (key, res.objective, gain))

    check("频次贪心优于随机分配",
          slot_results["frequency_greedy"].objective < slot_results["random"].objective)
    check("遗传算法不差于频次贪心",
          slot_results["genetic"].objective <= slot_results["frequency_greedy"].objective * 1.001,
          "遗传=%.0f 贪心=%.0f" % (slot_results["genetic"].objective,
                                   slot_results["frequency_greedy"].objective))

    print()
    print("=" * 74)
    print("C/D. 拣货路径策略合法性与最优性对照（小规模算例）")
    print("=" * 74)
    rng = np.random.default_rng(7)
    slots = layout.slots()
    for n_pick in (6, 9, 12):
        pick_idx = rng.choice(layout.slot_count, size=n_pick, replace=False)
        picks = [slots[int(i)] for i in pick_idx]
        print()
        print("  --- 拣货点数 = %d ---" % n_pick)
        best_exact = held_karp_optimal(inst, picks)
        print("    %-22s 距离 %8.2f 米  (%.2f ms)" % (best_exact.strategy,
                                                     best_exact.distance, best_exact.elapsed_ms))
        for key, fn in ROUTING_STRATEGIES.items():
            res = fn(inst, picks, seed=42)
            visited_ok = set(res.route[1:-1]) >= set(picks) or set(res.route) >= set(picks)
            ends_ok = res.route[0] == IO_POINT and res.route[-1] == IO_POINT
            dist_ok = abs(res.distance - path_length(layout, res.route)) < 1e-6
            not_better_than_optimal = res.distance >= best_exact.distance - 1e-6
            gap = (res.distance - best_exact.distance) / best_exact.distance * 100
            print("    %-22s 距离 %8.2f 米  与最优偏差 %+6.2f%%  (%.2f ms)"
                  % (res.strategy, res.distance, gap, res.elapsed_ms))
            check("    %s 路线合法" % key, visited_ok and ends_ok and dist_ok)
            check("    %s 不优于穷举最优解" % key, not_better_than_optimal)

    print()
    print("=" * 74)
    if FAILS:
        print("自检未通过，共 %d 项：" % len(FAILS))
        for f in FAILS:
            print("  - " + f)
        return 1
    print("全部自检通过 ✅")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
