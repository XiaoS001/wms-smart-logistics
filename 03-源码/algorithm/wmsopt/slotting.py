# -*- coding: utf-8 -*-
"""
货位分配策略（K1 数学建模 + K2 三类求解算法）

对应论文：第 3 章 3.x 货位分配模型、第 4 章 4.x 货位分配算法设计与对比

统一接口
--------
所有策略均为 `solve(instance, seed) -> SlottingResult`，
返回「商品 → 货位」的分配映射及目标函数值（加权拣货行走距离）。

实现的策略
----------
1. random_slotting   随机分配（对照组基线）
2. frequency_greedy  基于出库频次的贪心分配 —— 频次越高越靠近出入口
3. abc_greedy        基于 ABC 分类的分区贪心分配 —— A 类集中到近端分区
4. genetic_slotting  基于遗传算法的智能分配 —— 全局寻优，对应 K2 的第三类策略
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Callable, Dict, List, Tuple

import numpy as np

from .model import WarehouseInstance


@dataclass
class SlottingResult:
    """货位分配结果。"""

    strategy: str
    slot_of_item: np.ndarray      # 长度 = 商品数，值 = 货位编号
    objective: float              # 目标函数值：Σ f_i * d(IO, slot_of(i))，单位：米·次
    elapsed_ms: float             # 算法耗时（毫秒）
    extra: Dict[str, object] = None

    def summary(self) -> Dict[str, object]:
        d = {
            "策略": self.strategy,
            "目标函数值(米·次)": round(self.objective, 2),
            "耗时(ms)": round(self.elapsed_ms, 2),
        }
        if self.extra:
            d.update(self.extra)
        return d


# ----------------------------------------------------------------------------
# 目标函数：加权拣货行走距离
# ----------------------------------------------------------------------------
def evaluate_objective(instance: WarehouseInstance, slot_of_item: np.ndarray) -> float:
    """
    计算 Σ f_i * d(IO, slot_of(i))。

    该函数是全部货位分配策略的统一评价口径，保证对比公平。
    """
    io_d = instance.layout.io_distances()
    return float(np.sum(instance.items.freqs * io_d[slot_of_item]))


def _finish(instance: WarehouseInstance, slot_of_item: np.ndarray,
            strategy: str, elapsed_ms: float, extra: Dict[str, object] = None) -> SlottingResult:
    obj = evaluate_objective(instance, slot_of_item)
    return SlottingResult(strategy=strategy, slot_of_item=slot_of_item,
                          objective=obj, elapsed_ms=elapsed_ms, extra=extra or {})


# ----------------------------------------------------------------------------
# 策略 1：随机分配（基线）
# ----------------------------------------------------------------------------
def random_slotting(instance: WarehouseInstance, seed: int = 42) -> SlottingResult:
    """随机把商品铺到货位上，作为对比基线。"""
    import time
    t0 = time.perf_counter()
    n_items = instance.items.item_count
    n_slots = instance.layout.slot_count
    rng = np.random.default_rng(seed)
    chosen = rng.choice(n_slots, size=n_items, replace=False)
    elapsed = (time.perf_counter() - t0) * 1000.0
    return _finish(instance, chosen.astype(int), "随机分配", elapsed,
                   {"说明": "均匀随机分配，作为对照组基线"})


# ----------------------------------------------------------------------------
# 策略 2：基于出库频次的贪心分配
# ----------------------------------------------------------------------------
def frequency_greedy(instance: WarehouseInstance, seed: int = 42) -> SlottingResult:
    """
    贪心：按出库频次从高到低排序商品，按到出入口距离从近到远排序货位，逐一对齐。

    这是「频次越高越靠近出入口」原则的直接实现，也是工程上最常用的启发式。
    """
    import time
    t0 = time.perf_counter()
    freqs = instance.items.freqs
    io_d = instance.layout.io_distances()
    item_order = np.argsort(-freqs)          # 频次降序
    slot_order = np.argsort(io_d)            # 距离升序（近端优先）
    chosen = np.empty(freqs.shape[0], dtype=int)
    chosen[item_order] = slot_order[: freqs.shape[0]]
    elapsed = (time.perf_counter() - t0) * 1000.0
    return _finish(instance, chosen, "频次贪心分配", elapsed,
                   {"说明": "O(n log n) 贪心，频次与近端货位一一对齐"})


# ----------------------------------------------------------------------------
# 策略 3：基于 ABC 分类的分区贪心分配
# ----------------------------------------------------------------------------
def abc_greedy(instance: WarehouseInstance, seed: int = 42,
               zone_ratio: Tuple[float, float, float] = (0.30, 0.35, 0.35)) -> SlottingResult:
    """
    ABC 分区：把货位按到出入口的距离切成近/中/远三个分区（默认 30% / 35% / 35%），
    A 类商品只放近端分区、B 类放中区、C 类放远区；分区内部再按频次贪心。

    这样既保留 ABC 分区管理上的可操作性（同区集中、便于人工管理），
    又在每个分区内做了频次优先的局部最优。
    """
    import time
    t0 = time.perf_counter()
    n_items = instance.items.item_count
    freqs = instance.items.freqs
    abc = instance.items.abc_class
    io_d = instance.layout.io_distances()

    slot_order = np.argsort(io_d)                    # 距离升序
    n_slots = instance.layout.slot_count

    chosen = np.full(n_items, -1, dtype=int)
    used = np.zeros(n_slots, dtype=bool)
    # 逐类分配：A 类先占最近的货位，B 类占剩下的最近货位，C 类占最后的货位。
    # 这样天然处理了「某类商品数超过该分区容量」的情形——溢出部分顺延到后续分区，
    # 而不会像固定切区那样出现容量不匹配。
    prev_end = 0
    for cls in (0, 1, 2):
        members = np.where(abc == cls)[0]
        members = members[np.argsort(-freqs[members])]        # 分区内频次降序
        if members.size == 0:
            continue
        free = slot_order[~used[slot_order]]
        assign_to = free[: members.size]
        chosen[members] = assign_to
        used[assign_to] = True
        prev_end += members.size
    # 兜底：任何未分配商品（理论上不会发生）放入剩余货位
    if np.any(chosen < 0):
        free = np.where(~used)[0]
        chosen[chosen < 0] = free[: int(np.sum(chosen < 0))]
    n_near = int(round(n_slots * zone_ratio[0]))
    n_mid = int(round(n_slots * zone_ratio[1]))

    elapsed = (time.perf_counter() - t0) * 1000.0
    counts = instance.items.abc_summary()
    return _finish(instance, chosen, "ABC 分区贪心", elapsed,
                   {"说明": "按距离切三区，A/B/C 类分区分流后再区内贪心",
                    "分区货位数": [int(n_near), int(n_mid), int(n_slots - n_near - n_mid)],
                    "各类商品数": counts})


# ----------------------------------------------------------------------------
# 策略 4：遗传算法（K2 第三类策略，也是 K4 编码方案设计的落点）
# ----------------------------------------------------------------------------
def genetic_slotting(instance: WarehouseInstance, seed: int = 42,
                     pop_size: int = 80, generations: int = 300,
                     crossover_rate: float = 0.9, mutation_rate: float = 0.15,
                     tournament_k: int = 3, elitism: int = 2,
                     verbose: bool = False) -> SlottingResult:
    """
    遗传算法求解货位分配（最小化加权拣货行走距离）。

    编码方案（对应论文 K4）
    ----------------------
    采用**整数排列编码**：染色体长度为商品数，第 i 位表示商品 i 被放入的货位编号。
    由于商品数 ≤ 货位数，允许货位空闲，因此不做全排列约束，而用
    「货位集合去重」的方式维护合法性——重复位置在交叉/变异后通过修复算子消除。

    适应度函数
    ----------
        fitness = -Σ f_i * d(IO, slot_of(i))
    直接取目标函数取负，避免额外的尺度变换。

    选择、交叉、变异
    ----------------
    - 选择：锦标赛选择（规模 k），压力可控
    - 交叉：均匀交叉（Uniform Crossover），适合整数编码
    - 变异：随机重置若干基因位为未使用货位
    - 精英保留：每代保留最优 elitism 个个体，保证收敛不退化
    - 修复：若出现重复货位，把重复者改派到最近的空闲货位

    种群初始化：1 个「频次贪心」解 + 其余随机解，兼顾先验知识与多样性。
    """
    import time
    t0 = time.perf_counter()

    layout = instance.layout
    freqs = instance.items.freqs
    n_items = instance.items.item_count
    n_slots = layout.slot_count
    io_d = layout.io_distances()
    rng = np.random.default_rng(seed)

    def cost(chrom: np.ndarray) -> float:
        return float(np.sum(freqs * io_d[chrom]))

    def repair(chrom: np.ndarray) -> np.ndarray:
        """消除重复货位：重复者改派到最近的空闲货位。"""
        chrom = chrom.copy()
        seen = {}
        dups = []
        for i, s in enumerate(chrom):
            if s in seen:
                dups.append(i)
            else:
                seen[s] = i
        if not dups:
            return chrom
        used = np.zeros(n_slots, dtype=bool)
        used[list(seen.keys())] = True
        free = np.where(~used)[0]
        # 空闲货位按到出入口的距离升序，优先补给频次高的商品
        free = free[np.argsort(io_d[free])]
        dups = sorted(dups, key=lambda i: -freqs[i])
        for i, slot in zip(dups, free):
            chrom[i] = slot
        return chrom

    # ---- 种群初始化 ----
    pop = np.empty((pop_size, n_items), dtype=int)
    pop[0] = frequency_greedy(instance).slot_of_item      # 注入贪心先验解
    for p in range(1, pop_size):
        pop[p] = rng.choice(n_slots, size=n_items, replace=False)
    pop = np.array([repair(ind) for ind in pop])
    costs = np.array([cost(ind) for ind in pop])

    best_idx = int(np.argmin(costs))
    best_ind, best_cost = pop[best_idx].copy(), float(costs[best_idx])
    history: List[float] = [best_cost]

    for gen in range(generations):
        new_pop = [pop[i].copy() for i in np.argsort(costs)[:elitism]]   # 精英
        while len(new_pop) < pop_size:
            # 锦标赛选择
            def tournament() -> np.ndarray:
                cand = rng.integers(0, pop_size, size=tournament_k)
                return pop[cand[int(np.argmin(costs[cand]))]]
            p1, p2 = tournament(), tournament()
            # 均匀交叉
            if rng.random() < crossover_rate:
                mask = rng.random(n_items) < 0.5
                child = np.where(mask, p1, p2)
            else:
                child = p1.copy()
            # 变异：随机几个基因位重置
            if rng.random() < mutation_rate:
                n_mut = max(1, int(0.02 * n_items))
                pos = rng.choice(n_items, size=n_mut, replace=False)
                child[pos] = rng.choice(n_slots, size=n_mut, replace=False)
            new_pop.append(repair(child))
        pop = np.array(new_pop)
        costs = np.array([cost(ind) for ind in pop])
        gen_best = int(np.argmin(costs))
        if costs[gen_best] < best_cost:
            best_cost = float(costs[gen_best])
            best_ind = pop[gen_best].copy()
        history.append(best_cost)
        if verbose and gen % 50 == 0:
            print("  gen %3d  best=%.2f" % (gen, best_cost))

    elapsed = (time.perf_counter() - t0) * 1000.0
    return _finish(instance, best_ind, "遗传算法分配", elapsed, {
        "参数": {"种群规模": pop_size, "迭代代数": generations,
                 "交叉概率": crossover_rate, "变异概率": mutation_rate,
                 "锦标赛规模": tournament_k, "精英数": elitism},
        "收敛曲线首值": round(history[0], 2),
        "收敛曲线末值": round(history[-1], 2),
        "收敛历史": [round(v, 2) for v in history[:: max(1, len(history) // 20)]],
    })


# ----------------------------------------------------------------------------
# 策略注册表：便于实验脚本与系统接口统一调用
# ----------------------------------------------------------------------------
SLOTTING_STRATEGIES: Dict[str, Callable[..., SlottingResult]] = {
    "random": random_slotting,
    "frequency_greedy": frequency_greedy,
    "abc_greedy": abc_greedy,
    "genetic": genetic_slotting,
}

STRATEGY_LABELS = {
    "random": "随机分配",
    "frequency_greedy": "频次贪心分配",
    "abc_greedy": "ABC 分区贪心",
    "genetic": "遗传算法分配",
}
