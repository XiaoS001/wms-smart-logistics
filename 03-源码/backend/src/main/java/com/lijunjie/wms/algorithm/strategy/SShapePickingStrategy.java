package com.lijunjie.wms.algorithm.strategy;

import com.lijunjie.wms.algorithm.PickPath;
import com.lijunjie.wms.algorithm.PickingRoute;
import com.lijunjie.wms.algorithm.PickingStrategy;
import com.lijunjie.wms.algorithm.WarehouseLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * S 型穿越策略（传统仓储作业的默认做法，作为对比基线）。
 *
 * <p>拣货员依次穿过含拣货点的巷道，方向交替，形成“S”形轨迹；
 * 进入巷道后按行进方向依次经过该巷道内的全部拣货点，最后一个巷道走到最远拣货点后返回出入口。</p>
 *
 * <p>与 Python 实现保持一致：巷道编号升序、每过一条巷道切换方向、
 * 最后一条巷道不再折返、相邻重复点去重。</p>
 */
public class SShapePickingStrategy implements PickingStrategy {

    @Override
    public String code() {
        return "s-shape";
    }

    @Override
    public String label() {
        return "S 型穿越策略";
    }

    @Override
    public PickingRoute solve(PickPath path, long seed) {
        long start = System.currentTimeMillis();
        WarehouseLayout layout = path.getLayout();
        List<int[]> picks = path.getPicks();

        List<Integer> aisles = distinctAisles(picks);
        List<int[]> route = new ArrayList<int[]>();
        route.add(PickPath.io());

        boolean forward = true;
        for (int k = 0; k < aisles.size(); k++) {
            int aisle = aisles.get(k);
            boolean isLast = (k == aisles.size() - 1);
            if (isLast) {
                // 最后一条巷道：按当前方向经过全部拣货点后直接返回出入口
                List<Integer> ys = sortedRows(picks, aisle, false);
                if (!forward) {
                    Collections.reverse(ys);
                }
                for (Integer y : ys) {
                    route.add(new int[]{aisle, y});
                }
            } else {
                route.addAll(routerForAisle(path, aisle, forward));
                forward = !forward;
            }
        }
        route.add(PickPath.io());

        List<int[]> cleaned = PickPath.dedupeConsecutive(route);
        double distance = PickPath.pathLength(layout, cleaned);
        return new PickingRoute(label(), cleaned, distance, System.currentTimeMillis() - start);
    }

    /** 含拣货点的巷道编号，升序去重 */
    static List<Integer> distinctAisles(List<int[]> picks) {
        List<Integer> aisles = new ArrayList<Integer>();
        for (int[] p : picks) {
            if (!aisles.contains(p[0])) {
                aisles.add(p[0]);
            }
        }
        Collections.sort(aisles);
        return aisles;
    }

    /** 某巷道内的拣货点纵坐标，升序；reverse 为 true 时降序 */
    static List<Integer> sortedRows(List<int[]> picks, int aisle, boolean reverse) {
        List<Integer> ys = new ArrayList<Integer>();
        for (int[] p : picks) {
            if (p[0] == aisle && !ys.contains(p[1])) {
                ys.add(p[1]);
            }
        }
        Collections.sort(ys);
        if (reverse) {
            Collections.reverse(ys);
        }
        return ys;
    }

    /** 进入巷道后按行进方向经过全部拣货点，并返回巷道口方向（穿越式，故往返经过全部点） */
    static List<int[]> routerForAisle(PickPath path, int aisle, boolean forward) {
        List<int[]> out = new ArrayList<int[]>();
        List<Integer> ys = sortedRows(path.getPicks(), aisle, false);
        if (ys.isEmpty()) {
            return out;
        }
        if (forward) {
            for (Integer y : ys) {
                out.add(new int[]{aisle, y});
            }
        } else {
            for (int i = ys.size() - 1; i >= 0; i--) {
                out.add(new int[]{aisle, ys.get(i)});
            }
        }
        return out;
    }
}
