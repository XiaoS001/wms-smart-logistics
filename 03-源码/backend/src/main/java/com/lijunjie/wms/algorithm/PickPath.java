package com.lijunjie.wms.algorithm;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 拣货路径规划算例。
 *
 * <p>坐标约定与货位分配模块一致：巷道号 x 沿横向、纵深号 y 沿纵向，
 * 出入口 I/O 位于 (0, 0)，行走距离采用曼哈顿度量
 * {@code d = aisleGap × |Δx| + rowGap × |Δy|}。</p>
 *
 * <p>拣货路径规划问题本身是一个带返回起点的旅行商问题（TSP）变体：
 * 给定订单涉及的若干拣货点，求从出入口出发遍历全部拣货点再回到出入口的最短顺序。</p>
 */
public class PickPath implements Serializable {

    private static final long serialVersionUID = 1L;

    private final WarehouseLayout layout;

    /** 拣货点列表，每个元素为 {x, y} */
    private final List<int[]> picks;

    public PickPath(WarehouseLayout layout, List<int[]> picks) {
        if (layout == null) {
            throw new IllegalArgumentException("布局参数不能为空");
        }
        if (picks == null || picks.isEmpty()) {
            throw new IllegalArgumentException("拣货点不能为空");
        }
        this.layout = layout;
        this.picks = new ArrayList<int[]>(picks.size());
        for (int[] p : picks) {
            this.picks.add(new int[]{p[0], p[1]});
        }
    }

    public WarehouseLayout getLayout() {
        return layout;
    }

    public List<int[]> getPicks() {
        List<int[]> copy = new ArrayList<int[]>(picks.size());
        for (int[] p : picks) {
            copy.add(new int[]{p[0], p[1]});
        }
        return copy;
    }

    public int pickCount() {
        return picks.size();
    }

    /** 两点间行走距离（曼哈顿度量） */
    public double distance(int[] a, int[] b) {
        return layout.getAisleGap() * Math.abs(a[0] - b[0])
                + layout.getRowGap() * Math.abs(a[1] - b[1]);
    }

    /** 出入口坐标 */
    public static int[] io() {
        return new int[]{0, 0};
    }

    /** 按顺序累加路径长度 */
    public static double pathLength(WarehouseLayout layout, List<int[]> route) {
        double total = 0;
        for (int i = 0; i + 1 < route.size(); i++) {
            int[] a = route.get(i);
            int[] b = route.get(i + 1);
            total += layout.getAisleGap() * Math.abs(a[0] - b[0])
                    + layout.getRowGap() * Math.abs(a[1] - b[1]);
        }
        return total;
    }

    /** 去除相邻重复点，避免距离计算出现零长度段 */
    public static List<int[]> dedupeConsecutive(List<int[]> route) {
        List<int[]> out = new ArrayList<int[]>(route.size());
        for (int[] p : route) {
            if (out.isEmpty()) {
                out.add(p);
                continue;
            }
            int[] last = out.get(out.size() - 1);
            if (last[0] != p[0] || last[1] != p[1]) {
                out.add(p);
            }
        }
        return out;
    }
}
