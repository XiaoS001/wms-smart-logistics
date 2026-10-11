# -*- coding: utf-8 -*-
"""
仓储优化算法内核 —— 数据模型层

课题：面向智慧物流的仓储管理系统设计与实现——以货位分配与拣货路径优化为核心
对应论文：第 3 章 问题建模（K1 货位分配建模、K3 拣货路径建模）

坐标约定
--------
仓库采用「巷道 × 货架」的网格化表示：

    y（货架纵深位置，1 ~ rows）
    │
    │   ○ ○ ○ ○ ○ ○ ○ ○ ○ ○      ← 第 1 排货架（x = 1）
    │   ○ ○ ○ ○ ○ ○ ○ ○ ○ ○      ← 第 2 排货架（x = 2）
    │   ...
    └──── x（巷道/通道编号，1 ~ aisles）
   I/O 出入口位于巷道最前端（y = 0）

- 货位（slot）用整数坐标 (x, y) 表示，x ∈ [1, aisles]，y ∈ [1, rows]
- 出入口 I/O 点为 (0, 0)
- 物理距离：横向每跨一个巷道间距 aisle_gap，纵向每跨一个货架间距 row_gap
- 行走距离采用曼哈顿距离度量（仓储拣货路径研究中的标准简化）：
      d(a, b) = aisle_gap * |xa - xb| + row_gap * |ya - yb|

货位分配决策变量
----------------
    X[i][k] ∈ {0, 1}  表示商品 i 是否分配到货位 k（每个商品恰好占一个货位）
目标函数（最小化加权拣货行走距离，K1）：
    min  Σ_i f_i * d(IO, slot_of(i))
其中 f_i 为商品 i 的出库频次（拣货次数）。
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Dict, List, Sequence, Tuple

import numpy as np

IO_POINT: Tuple[int, int] = (0, 0)


@dataclass(frozen=True)
class WarehouseLayout:
    """仓库布局参数。默认值对应一个中小型人工拣货仓库。"""

    aisles: int = 10          # 巷道（通道）数
    rows: int = 20            # 每巷道的货架纵深位置数
    aisle_gap: float = 2.5    # 相邻巷道间距（米）
    row_gap: float = 1.2      # 相邻货位纵深间距（米）

    @property
    def slot_count(self) -> int:
        return self.aisles * self.rows

    def slots(self) -> List[Tuple[int, int]]:
        """全部货位坐标，按 (x, y) 字典序排列，索引即货位编号。"""
        return [(x, y) for x in range(1, self.aisles + 1) for y in range(1, self.rows + 1)]

    def distance(self, a: Tuple[int, int], b: Tuple[int, int]) -> float:
        return self.aisle_gap * abs(a[0] - b[0]) + self.row_gap * abs(a[1] - b[1])

    def io_distances(self) -> np.ndarray:
        """各货位到出入口的行走距离，顺序与 slots() 一致。"""
        return np.array([self.distance(IO_POINT, s) for s in self.slots()], dtype=float)

    def distance_matrix(self) -> np.ndarray:
        """货位两两之间的行走距离矩阵，便于拣货路径算法复用。"""
        pts = self.slots()
        xs = np.array([p[0] for p in pts], dtype=float) * self.aisle_gap
        ys = np.array([p[1] for p in pts], dtype=float) * self.row_gap
        return np.abs(xs[:, None] - xs[None, :]) + np.abs(ys[:, None] - ys[None, :])


@dataclass(frozen=True)
class ItemProfile:
    """
    商品出库频次画像。

    按 ABC 分类思想，真实仓库中少数商品贡献大部分出库量（帕累托分布），
    因此默认用 Zipf 分布生成频次：f_i ∝ 1 / i^alpha。
    """

    freqs: np.ndarray          # 各商品出库频次（归一化前的原始次数）
    abc_class: np.ndarray      # 各商品的 ABC 等级：0=A（累计前 70%）、1=B（70%~90%）、2=C（其余）

    @property
    def item_count(self) -> int:
        return int(self.freqs.shape[0])

    def normalized(self) -> np.ndarray:
        total = float(self.freqs.sum())
        return self.freqs / total if total > 0 else self.freqs

    def abc_summary(self) -> Dict[str, int]:
        return {
            "A": int(np.sum(self.abc_class == 0)),
            "B": int(np.sum(self.abc_class == 1)),
            "C": int(np.sum(self.abc_class == 2)),
        }


def build_item_profile(item_count: int = 200, alpha: float = 1.2, seed: int = 42) -> ItemProfile:
    """
    生成商品出库频次画像（K1 的输入数据）。

    alpha 越大，出库越集中在少数商品上（ABC 分化越明显）。
    为贴近真实数据，在 Zipf 基础上叠加轻量随机扰动，但保持排序关系不变。
    """
    idx = np.arange(1, item_count + 1, dtype=float)
    base = 1.0 / np.power(idx, alpha)
    rng = np.random.default_rng(seed)
    jitter = 1.0 + 0.05 * (rng.random(item_count) - 0.5)   # ±2.5%
    freqs = base * jitter
    # 换算成整数次拣货量（便于论文中描述"出库频次"）
    freqs = np.maximum(1, np.round(freqs * 1000)).astype(float)

    order = np.argsort(-freqs)
    cumulative = np.cumsum(freqs[order]) / freqs.sum()
    abc = np.empty(item_count, dtype=int)
    a_end = int(np.searchsorted(cumulative, 0.70) + 1)
    b_end = int(np.searchsorted(cumulative, 0.90) + 1)
    abc[order[:a_end]] = 0
    abc[order[a_end:b_end]] = 1
    abc[order[b_end:]] = 2
    return ItemProfile(freqs=freqs, abc_class=abc)


@dataclass
class WarehouseInstance:
    """一个完整的算例：布局 + 商品画像 + 与布局规模校验。"""

    name: str
    layout: WarehouseLayout
    items: ItemProfile
    notes: str = ""

    def validate(self) -> None:
        if self.items.item_count > self.layout.slot_count:
            raise ValueError(
                "商品数(%d) 超过货位数(%d)，本模型假设一货位一商品"
                % (self.items.item_count, self.layout.slot_count)
            )

    def summary(self) -> Dict[str, object]:
        return {
            "算例": self.name,
            "巷道数": self.layout.aisles,
            "纵深位数": self.layout.rows,
            "货位数": self.layout.slot_count,
            "商品数": self.items.item_count,
            "ABC分布": self.items.abc_summary(),
            "备注": self.notes,
        }
