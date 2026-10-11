# -*- coding: utf-8 -*-
"""
拣货路径规划策略（K3 多策略实现 + K5 穷举最优解基准）

对应论文：第 3 章 3.x 拣货路径建模、第 4 章 4.x 路径规划算法设计与对比

问题定义
--------
给定一张订单涉及的若干拣货点（货位），求从出入口出发、遍历全部拣货点、
再回到出入口的行走距离最短的访问顺序（带返回起点的 TSP 变体）。

实现的策略
----------
1. s_shape         S 型（S-shape）穿越策略 —— 行业传统策略，作为对比基线
2. largest_gap     最大间隙（Largest Gap）策略
3. nearest_neighbor 最近邻（Nearest Neighbor）策略 —— 贪婪构造
4. ant_colony      蚁群算法（ACO）—— 元启发式，对应 K4

评价与基准
----------
- held_karp_optimal：小规模算例的**穷举最优解**（Held-Karp 动态规划，n ≤ 16），
  用于计算各启发式策略与理论最优解之间的偏差（K5 的量化评估方法）
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Dict, List, Sequence, Tuple

import numpy as np

from .model import IO_POINT, WarehouseInstance


@dataclass
class RoutingResult:
    """一次路径规划的结果。"""

    strategy: str
    route: List[Tuple[int, int]]      # 行走顺序（含起终点出入口）
    distance: float                   # 总行走距离（米）
    elapsed_ms: float                 # 计算耗时（毫秒）
    exact: bool = False               # 是否为最优解
    extra: Dict[str, object] = field(default_factory=dict)

    def summary(self) -> Dict[str, object]:
        d = {
            "策略": self.strategy,
            "行走距离(米)": round(self.distance, 2),
            "耗时(ms)": round(self.elapsed_ms, 3),
            "拣货点数": len(self.route) - 2,
        }
        if self.exact:
            d["是否最优解"] = "是（穷举基准）"
        d.update(self.extra)
        return d


# ----------------------------------------------------------------------------
# 公共工具
# ----------------------------------------------------------------------------
def path_length(layout, route: Sequence[Tuple[int, int]]) -> float:
    """按曼哈顿度量累加路径长度。"""
    total = 0.0
    for a, b in zip(route, route[1:]):
        total += layout.distance(a, b)
    return total


def _aisle_span(layout, picks: Sequence[Tuple[int, int]], aisle: int) -> Tuple[int, int] | None:
    """某巷道内拣货点的纵深范围 (min_y, max_y)，无拣货点则返回 None。"""
    ys = [y for (x, y) in picks if x == aisle]
    if not ys:
        return None
    return min(ys), max(ys)


def _router_for_aisle(layout, picks: Sequence[Tuple[int, int]], aisle: int,
                      forward: bool) -> List[Tuple[int, int]]:
    """
    生成单条巷道内的行走点序列（拣货点在序列中按经过顺序排列）。

    forward=True 表示从巷道近端（y 小）走向远端（y 大），否则相反。
    返回的点序列中，每个元素都是拣货员真实经过的位置（含全部拣货点），
    因此它可以直接用于计算行走距离——这是保证策略之间可比的前提。
    """
    ys = sorted({y for (x, y) in picks if x == aisle})
    if not ys:
        return []
    if forward:
        return [(aisle, y) for y in ys]
    return [(aisle, y) for y in reversed(ys)]


# ----------------------------------------------------------------------------
# 策略 1：S 型（S-shape）穿越策略
# ----------------------------------------------------------------------------
def s_shape(instance: WarehouseInstance, picks: Sequence[Tuple[int, int]],
            seed: int = 42) -> RoutingResult:
    """
    S 型策略：拣货员依次穿过**含拣货点的巷道**，方向交替，形成"S"形轨迹。
    进入巷道后按行进方向依次经过该巷道内的全部拣货点（最后一个巷道走到最远拣货点后返回）。

    这是传统仓储作业的默认做法：不需要额外计算，但存在无效行走。
    注意：为保证各策略可比，返回的路线包含**全部拣货点**。
    """
    import time
    t0 = time.perf_counter()
    layout = instance.layout
    picks = list(picks)
    aisle_list = sorted({x for (x, _) in picks})

    route: List[Tuple[int, int]] = [IO_POINT]
    forward = True
    for k, aisle in enumerate(aisle_list):
        is_last = (k == len(aisle_list) - 1)
        if is_last:
            # 最后一条巷道：走到最远拣货点后直接返回出入口（不再折返）
            ys = sorted({y for (x, y) in picks if x == aisle})
            seq = ys if forward else list(reversed(ys))
            route.extend((aisle, y) for y in seq)
        else:
            route.extend(_router_for_aisle(layout, picks, aisle, forward))
            forward = not forward
    route.append(IO_POINT)
    route = _dedupe_consecutive(route)

    elapsed = (time.perf_counter() - t0) * 1000.0
    return RoutingResult("S型穿越策略", route, path_length(layout, route), elapsed,
                         extra={"说明": "传统穿越式策略，作为对比基线",
                               "经过巷道数": len(aisle_list)})


def _dedupe_consecutive(route: Sequence[Tuple[int, int]]) -> List[Tuple[int, int]]:
    """去掉相邻重复点，避免距离计算时出现零长度段。"""
    out: List[Tuple[int, int]] = []
    for p in route:
        if not out or out[-1] != p:
            out.append(p)
    return out


# ----------------------------------------------------------------------------
# 策略 2：最大间隙（Largest Gap）策略
# ----------------------------------------------------------------------------
def largest_gap(instance: WarehouseInstance, picks: Sequence[Tuple[int, int]],
                seed: int = 42) -> RoutingResult:
    """
    最大间隙策略：拣货员仍按巷道推进，但**不盲目穿越整条巷道**，
    而是在巷道内相邻参考点之间找出最大的那个间隙，在间隙处以内的区域折返，
    从而跳过最不值得穿越的那一段纵向距离。

    参考点集 = {巷道近端 y=1, 各拣货点纵坐标, 巷道远端 y=rows}

    折返判定（单侧穿越）
    --------------------
    - 若最大间隙位于巷道下半段（近端一侧拣货点更多），则从近端进入：
      依次经过近端拣货点后折返；若最大间隙上方仍有拣货点，则继续上行穿越该间隙
      直至最远拣货点再折返（此时等价于穿越，说明该巷道不可避免要穿）。
    - 反之从远端进入，逻辑镜像。

    返回路线同样包含全部拣货点，保证与其它策略可比。
    """
    import time
    t0 = time.perf_counter()
    layout = instance.layout
    picks = list(picks)
    aisle_list = sorted({x for (x, _) in picks})

    route: List[Tuple[int, int]] = [IO_POINT]
    forward = True
    for k, aisle in enumerate(aisle_list):
        ys = sorted({y for (x, y) in picks if x == aisle})
        is_last = (k == len(aisle_list) - 1)
        if is_last:
            seq = ys if forward else list(reversed(ys))
            route.extend((aisle, y) for y in seq)
            continue

        refs = sorted(set([1, layout.rows] + ys))
        gaps = [(refs[i + 1] - refs[i], refs[i], refs[i + 1]) for i in range(len(refs) - 1)]
        if not gaps:
            forward = not forward
            continue
        _, gap_lo, gap_hi = max(gaps, key=lambda t: t[0])

        below = [y for y in ys if y <= gap_lo]     # 间隙下方（近端侧）的拣货点
        above = [y for y in ys if y >= gap_hi]     # 间隙上方（远端侧）的拣货点

        seq: List[Tuple[int, int]] = []
        if forward:
            if len(below) >= len(above):
                # 近端侧为主：先拣下方，折返；若上方仍有货则上行穿越后返回
                seq.extend((aisle, y) for y in sorted(below))
                if above:
                    seq.extend((aisle, y) for y in sorted(above))
                    seq.extend((aisle, y) for y in sorted(below, reverse=True))
            else:
                seq.extend((aisle, y) for y in sorted(ys))
        else:
            if len(above) >= len(below):
                seq.extend((aisle, y) for y in sorted(above, reverse=True))
                if below:
                    seq.extend((aisle, y) for y in sorted(below, reverse=True))
                    seq.extend((aisle, y) for y in sorted(above))
            else:
                seq.extend((aisle, y) for y in sorted(ys, reverse=True))

        route.extend(seq)
        forward = not forward

    route.append(IO_POINT)
    cleaned = _dedupe_consecutive(route)
    if cleaned[-1] != IO_POINT:
        cleaned.append(IO_POINT)

    elapsed = (time.perf_counter() - t0) * 1000.0
    return RoutingResult("最大间隙策略", cleaned, path_length(layout, cleaned), elapsed,
                         extra={"说明": "在巷道内最大间隙处折返，减少无效穿越",
                               "经过巷道数": len(aisle_list)})


# ----------------------------------------------------------------------------
# 策略 3：最近邻（Nearest Neighbor）策略
# ----------------------------------------------------------------------------
def nearest_neighbor(instance: WarehouseInstance, picks: Sequence[Tuple[int, int]],
                     seed: int = 42) -> RoutingResult:
    """
    最近邻策略：从出入口出发，每次选择距离当前位置最近的未访问拣货点，直至全部访问完，
    最后返回出入口。属于典型贪婪构造算法，时间复杂度 O(n²)。
    """
    import time
    t0 = time.perf_counter()
    layout = instance.layout
    remaining = list(picks)
    route: List[Tuple[int, int]] = [IO_POINT]
    current = IO_POINT
    while remaining:
        nxt = min(remaining, key=lambda p: (layout.distance(current, p), p))
        route.append(nxt)
        remaining.remove(nxt)
        current = nxt
    route.append(IO_POINT)
    elapsed = (time.perf_counter() - t0) * 1000.0
    return RoutingResult("最近邻策略", route, path_length(layout, route), elapsed,
                         extra={"说明": "O(n²) 贪婪构造，可能陷入局部最优"})


# ----------------------------------------------------------------------------
# 策略 4：蚁群算法（ACO）
# ----------------------------------------------------------------------------
def ant_colony(instance: WarehouseInstance, picks: Sequence[Tuple[int, int]],
               seed: int = 42, n_ants: int = 25, n_iter: int = 80,
               alpha: float = 1.0, beta: float = 3.0, rho: float = 0.4,
               q: float = 100.0, elite: int = 2,
               verbose: bool = False) -> RoutingResult:
    """
    蚁群算法求解拣货路径（对应论文 K4 的元启发式编码与参数设计）。

    要素设计
    --------
    - 解表示：访问顺序排列（与 TSP 同构），节点集合 = {出入口} ∪ 拣货点
    - 信息素 tau[i][j]：路径 (i, j) 上的信息素浓度，初始为常数 1
    - 启发式信息 eta[i][j] = 1 / d(i, j)：距离越短吸引力越大
    - 转移概率 p(j) ∝ tau[i][j]^alpha * eta[i][j]^beta
    - 信息素更新：tau ← (1-rho) * tau + Σ 蚂蚁沉积量（沉积量 = q / 路径长度）
    - 精英策略：每轮额外强化全局最优路径，加速收敛
    - 参数敏感性实验见 experiments 脚本（alpha / beta / rho 的取值影响）
    """
    import time
    t0 = time.perf_counter()
    layout = instance.layout

    nodes = [IO_POINT] + list(picks)
    n = len(nodes)
    if n <= 2:
        route = [IO_POINT, IO_POINT]
        elapsed = (time.perf_counter() - t0) * 1000.0
        return RoutingResult("蚁群算法", route, path_length(layout, route), elapsed)

    coords = np.array(nodes, dtype=float)
    xs = coords[:, 0] * layout.aisle_gap
    ys = coords[:, 1] * layout.row_gap
    D = np.abs(xs[:, None] - xs[None, :]) + np.abs(ys[:, None] - ys[None, :])
    np.fill_diagonal(D, np.inf)
    eta = 1.0 / D

    rng = np.random.default_rng(seed)
    tau = np.ones((n, n), dtype=float)
    best_route_idx: List[int] | None = None
    best_len = float("inf")
    history: List[float] = []

    def tour_length(order: Sequence[int]) -> float:
        total = 0.0
        for a, b in zip(order, order[1:]):
            total += D[a, b]
        total += D[order[-1], order[0]]
        return total

    for it in range(n_iter):
        tours: List[List[int]] = []
        lengths: List[float] = []
        for _ in range(n_ants):
            start = 0                                  # 必从出入口出发
            visited = np.zeros(n, dtype=bool)
            visited[start] = True
            order = [start]
            cur = start
            for _step in range(n - 1):
                prob = np.where(visited, 0.0, (tau[cur] ** alpha) * (eta[cur] ** beta))
                s = prob.sum()
                if s <= 0:
                    nxt = int(np.argmax(np.where(visited, -np.inf, eta[cur])))
                else:
                    nxt = int(rng.choice(n, p=prob / s))
                visited[nxt] = True
                order.append(nxt)
                cur = nxt
            L = tour_length(order)
            tours.append(order)
            lengths.append(L)
            if L < best_len:
                best_len, best_route_idx = L, list(order)

        # 信息素挥发 + 沉积
        tau *= (1.0 - rho)
        for order, L in zip(tours, lengths):
            deposit = q / L
            for a, b in zip(order, order[1:]):
                tau[a, b] += deposit
                tau[b, a] += deposit
            tau[order[-1], order[0]] += deposit
            tau[order[0], order[-1]] += deposit
        # 精英强化
        if best_route_idx and elite > 0:
            extra_deposit = elite * q / best_len
            for a, b in zip(best_route_idx, best_route_idx[1:]):
                tau[a, b] += extra_deposit
                tau[b, a] += extra_deposit
        history.append(best_len)
        if verbose and it % 20 == 0:
            print("  iter %3d  best=%.2f" % (it, best_len))

    route = [nodes[i] for i in best_route_idx] + [IO_POINT]
    elapsed = (time.perf_counter() - t0) * 1000.0
    return RoutingResult("蚁群算法", route, path_length(layout, route), elapsed, extra={
        "参数": {"蚂蚁数": n_ants, "迭代次数": n_iter, "alpha": alpha,
                 "beta": beta, "信息素挥发率": rho, "信息素强度": q, "精英数": elite},
        "收敛曲线首值": round(history[0], 2) if history else None,
        "收敛曲线末值": round(history[-1], 2) if history else None,
    })


# ----------------------------------------------------------------------------
# 穷举最优解基准：Held-Karp 动态规划（K5 量化评估的基准）
# ----------------------------------------------------------------------------
def held_karp_optimal(instance: WarehouseInstance, picks: Sequence[Tuple[int, int]],
                      max_nodes: int = 17) -> RoutingResult:
    """
    小规模算例的最优解（含返回起点的最短哈密顿回路）。

    算法：Held-Karp 动态规划，状态 dp[mask][j] 表示从起点出发、
    已访问集合 mask、当前停在节点 j 的最短距离。
    时间复杂度 O(2^n · n²)，因此仅用于 n ≤ max_nodes 的小规模算例，
    其解作为评价各启发式策略的**理论最优基准**。
    """
    import time
    t0 = time.perf_counter()
    layout = instance.layout

    nodes = [IO_POINT] + list(picks)
    n = len(nodes)
    if n > max_nodes:
        raise ValueError(
            "Held-Karp 仅适用于小规模算例：当前节点数 %d > 上限 %d" % (n, max_nodes)
        )
    coords = np.array(nodes, dtype=float)
    xs = coords[:, 0] * layout.aisle_gap
    ys = coords[:, 1] * layout.row_gap
    D = np.abs(xs[:, None] - xs[None, :]) + np.abs(ys[:, None] - ys[None, :])

    full = 1 << (n - 1)                 # 除起点外 n-1 个节点的访问状态
    INF = float("inf")
    dp = np.full((full, n - 1), INF)
    parent = np.full((full, n - 1), -1, dtype=int)

    # 起点 0 -> 各节点
    for j in range(n - 1):
        dp[1 << j][j] = D[0][j + 1]

    for mask in range(full):
        for j in range(n - 1):
            if dp[mask][j] == INF or not (mask >> j) & 1:
                continue
            base = dp[mask][j]
            for k in range(n - 1):
                if (mask >> k) & 1:
                    continue
                nmask = mask | (1 << k)
                cand = base + D[j + 1][k + 1]
                if cand < dp[nmask][k]:
                    dp[nmask][k] = cand
                    parent[nmask][k] = j

    # 回到起点
    best_len = INF
    last = -1
    for j in range(n - 1):
        cand = dp[full - 1][j] + D[j + 1][0]
        if cand < best_len:
            best_len = cand
            last = j

    # 回溯路径
    order_idx: List[int] = []
    mask = full - 1
    j = last
    while j >= 0:
        order_idx.append(j)
        pj = parent[mask][j]
        mask ^= (1 << j)
        j = pj
    order_idx.reverse()
    route = [nodes[0]] + [nodes[i + 1] for i in order_idx] + [nodes[0]]

    elapsed = (time.perf_counter() - t0) * 1000.0
    return RoutingResult("穷举最优解(Held-Karp)", route, path_length(layout, route), elapsed,
                         exact=True, extra={"节点数": n, "搜索状态数": int(full)})


# ----------------------------------------------------------------------------
# 策略注册表
# ----------------------------------------------------------------------------
ROUTING_STRATEGIES = {
    "s_shape": s_shape,
    "largest_gap": largest_gap,
    "nearest_neighbor": nearest_neighbor,
    "ant_colony": ant_colony,
}

ROUTING_LABELS = {
    "s_shape": "S型穿越策略",
    "largest_gap": "最大间隙策略",
    "nearest_neighbor": "最近邻策略",
    "ant_colony": "蚁群算法",
}
