package com.lijunjie.wms.algorithm.strategy;

import com.lijunjie.wms.algorithm.PickPath;
import com.lijunjie.wms.algorithm.PickingRoute;
import com.lijunjie.wms.algorithm.PickingStrategy;
import com.lijunjie.wms.algorithm.WarehouseLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 最大间隙策略。
 *
 * <p>拣货员仍按巷道推进，但不盲目穿越整条巷道：在巷道内的参考点之间找出最大的那个间隙，
 * 在间隙处以内的区域折返，从而跳过最不值得穿越的那一段纵向距离。</p>
 *
 * <p>参考点集合 = {巷道近端 y = 1} ∪ {该巷道内各拣货点的 y} ∪ {巷道远端 y = rows}。
 * 折返判定规则与 Python 实现一致：</p>
 * <ul>
 *   <li>正向进入且近端侧拣货点不少于远端侧：先拣近端侧，折返；若远端侧仍有货则上行穿越后返回；</li>
 *   <li>正向进入但远端侧更多：直接穿越整条巷道；</li>
 *   <li>反向进入时为上述逻辑的镜像。</li>
 * </ul>
 */
public class LargestGapPickingStrategy implements PickingStrategy {

    @Override
    public String code() {
        return "largest-gap";
    }

    @Override
    public String label() {
        return "最大间隙策略";
    }

    @Override
    public PickingRoute solve(PickPath path, long seed) {
        long start = System.currentTimeMillis();
        WarehouseLayout layout = path.getLayout();
        List<int[]> picks = path.getPicks();

        List<Integer> aisles = SShapePickingStrategy.distinctAisles(picks);
        List<int[]> route = new ArrayList<int[]>();
        route.add(PickPath.io());

        boolean forward = true;
        for (int k = 0; k < aisles.size(); k++) {
            int aisle = aisles.get(k);
            List<Integer> ys = SShapePickingStrategy.sortedRows(picks, aisle, false);
            boolean isLast = (k == aisles.size() - 1);

            if (isLast) {
                List<Integer> seq = new ArrayList<Integer>(ys);
                if (!forward) {
                    Collections.reverse(seq);
                }
                for (Integer y : seq) {
                    route.add(new int[]{aisle, y});
                }
                continue;
            }

            // 参考点：近端 1、各拣货点、远端 rows
            List<Integer> refs = new ArrayList<Integer>();
            refs.add(1);
            refs.add(layout.getRows());
            for (Integer y : ys) {
                if (!refs.contains(y)) {
                    refs.add(y);
                }
            }
            Collections.sort(refs);
            if (refs.size() < 2) {
                forward = !forward;
                continue;
            }

            // 找最大间隙；并列时取第一个（与 Python 的 max 语义一致）
            int gapSize = -1;
            int gapLo = refs.get(0);
            int gapHi = refs.get(refs.size() - 1);
            for (int i = 0; i + 1 < refs.size(); i++) {
                int size = refs.get(i + 1) - refs.get(i);
                if (size > gapSize) {
                    gapSize = size;
                    gapLo = refs.get(i);
                    gapHi = refs.get(i + 1);
                }
            }

            // 间隙下方（近端侧）与上方（远端侧）的拣货点
            List<Integer> below = new ArrayList<Integer>();
            List<Integer> above = new ArrayList<Integer>();
            for (Integer y : ys) {
                if (y <= gapLo) {
                    below.add(y);
                }
                if (y >= gapHi) {
                    above.add(y);
                }
            }

            List<int[]> seq = new ArrayList<int[]>();
            if (forward) {
                if (below.size() >= above.size()) {
                    for (Integer y : below) {
                        seq.add(new int[]{aisle, y});
                    }
                    if (!above.isEmpty()) {
                        for (Integer y : above) {
                            seq.add(new int[]{aisle, y});
                        }
                        List<Integer> belowDesc = new ArrayList<Integer>(below);
                        Collections.reverse(belowDesc);
                        for (Integer y : belowDesc) {
                            seq.add(new int[]{aisle, y});
                        }
                    }
                } else {
                    for (Integer y : ys) {
                        seq.add(new int[]{aisle, y});
                    }
                }
            } else {
                if (above.size() >= below.size()) {
                    List<Integer> aboveDesc = new ArrayList<Integer>(above);
                    Collections.reverse(aboveDesc);
                    for (Integer y : aboveDesc) {
                        seq.add(new int[]{aisle, y});
                    }
                    if (!below.isEmpty()) {
                        List<Integer> belowDesc = new ArrayList<Integer>(below);
                        Collections.reverse(belowDesc);
                        for (Integer y : belowDesc) {
                            seq.add(new int[]{aisle, y});
                        }
                        for (Integer y : above) {
                            seq.add(new int[]{aisle, y});
                        }
                    }
                } else {
                    List<Integer> ysDesc = new ArrayList<Integer>(ys);
                    Collections.reverse(ysDesc);
                    for (Integer y : ysDesc) {
                        seq.add(new int[]{aisle, y});
                    }
                }
            }
            route.addAll(seq);
            forward = !forward;
        }

        route.add(PickPath.io());
        List<int[]> cleaned = PickPath.dedupeConsecutive(route);
        if (cleaned.isEmpty()
                || cleaned.get(cleaned.size() - 1)[0] != 0
                || cleaned.get(cleaned.size() - 1)[1] != 0) {
            cleaned.add(PickPath.io());
        }
        double distance = PickPath.pathLength(layout, cleaned);
        return new PickingRoute(label(), cleaned, distance, System.currentTimeMillis() - start);
    }
}
