package com.lijunjie.wms.algorithm.strategy;

import com.lijunjie.wms.algorithm.PickPath;
import com.lijunjie.wms.algorithm.PickingRoute;
import com.lijunjie.wms.algorithm.PickingStrategy;
import com.lijunjie.wms.algorithm.WarehouseLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * 最近邻（Nearest Neighbor）策略。
 *
 * <p>从出入口出发，每次选择距离当前位置最近的未访问拣货点，直至全部访问完，最后返回出入口。
 * 属典型的贪婪构造算法，时间复杂度 O(n²)，实现简单但可能陷入局部最优。</p>
 *
 * <p>平局处理与 Python 实现一致：距离相同时取坐标字典序较小的拣货点，
 * 保证结果确定可复现。</p>
 */
public class NearestNeighborPickingStrategy implements PickingStrategy {

    @Override
    public String code() {
        return "nearest-neighbor";
    }

    @Override
    public String label() {
        return "最近邻策略";
    }

    @Override
    public PickingRoute solve(PickPath path, long seed) {
        long start = System.currentTimeMillis();
        WarehouseLayout layout = path.getLayout();
        List<int[]> remaining = path.getPicks();

        List<int[]> route = new ArrayList<int[]>();
        int[] current = PickPath.io();
        route.add(current);

        while (!remaining.isEmpty()) {
            int[] best = null;
            double bestDistance = Double.MAX_VALUE;
            for (int[] candidate : remaining) {
                double d = path.distance(current, candidate);
                if (d < bestDistance - 1e-12
                        || (Math.abs(d - bestDistance) <= 1e-12 && best != null && lexicographicallyBefore(candidate, best))) {
                    bestDistance = d;
                    best = candidate;
                }
            }
            route.add(best);
            remaining.remove(best);
            current = best;
        }
        route.add(PickPath.io());

        double distance = PickPath.pathLength(layout, route);
        return new PickingRoute(label(), route, distance, System.currentTimeMillis() - start);
    }

    /** 按 (x, y) 字典序比较，模拟 Python 元组比较的确定性平局处理 */
    private static boolean lexicographicallyBefore(int[] a, int[] b) {
        return a[0] != b[0] ? a[0] < b[0] : a[1] < b[1];
    }
}
