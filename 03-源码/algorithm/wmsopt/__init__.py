# -*- coding: utf-8 -*-
"""
wmsopt —— 仓储货位分配与拣货路径优化算法内核

课题：面向智慧物流的仓储管理系统设计与实现——以货位分配与拣货路径优化为核心

模块结构
--------
- model      数据模型：仓库布局、商品出库频次画像、算例
- slotting   货位分配策略：随机 / 频次贪心 / ABC 分区贪心 / 遗传算法
- picking    拣货路径策略：S 型 / 最大间隙 / 最近邻 / 蚁群 + 穷举最优解基准

设计原则
--------
1. **纯算法、无 Web 依赖**：内核不引用任何 Web 框架，可被单元测试与实验脚本直接调用，
   系统侧只通过 service 门面调用（对应计划中的"算法独立服务层"，降低 R5 风险）。
2. **统一评价口径**：货位分配统一用加权拣货行走距离，路径规划统一用总行走距离，
   保证策略之间横向可比。
3. **可复现**：所有随机过程均接受 seed 参数，实验数据可由脚本重现（降低 R6 风险）。
"""

from .model import IO_POINT, ItemProfile, WarehouseInstance, WarehouseLayout, build_item_profile
from .slotting import (
    SLOTTING_STRATEGIES,
    STRATEGY_LABELS,
    SlottingResult,
    abc_greedy,
    evaluate_objective,
    frequency_greedy,
    genetic_slotting,
    random_slotting,
)
from .picking import (
    ROUTING_LABELS,
    ROUTING_STRATEGIES,
    RoutingResult,
    ant_colony,
    held_karp_optimal,
    largest_gap,
    nearest_neighbor,
    path_length,
    s_shape,
)

__all__ = [
    "IO_POINT",
    "WarehouseLayout",
    "ItemProfile",
    "WarehouseInstance",
    "build_item_profile",
    "SlottingResult",
    "SLOTTING_STRATEGIES",
    "STRATEGY_LABELS",
    "random_slotting",
    "frequency_greedy",
    "abc_greedy",
    "genetic_slotting",
    "evaluate_objective",
    "RoutingResult",
    "ROUTING_STRATEGIES",
    "ROUTING_LABELS",
    "s_shape",
    "largest_gap",
    "nearest_neighbor",
    "ant_colony",
    "held_karp_optimal",
    "path_length",
]

__version__ = "0.1.0"
