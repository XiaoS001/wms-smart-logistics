package com.lijunjie.wms.algorithm.strategy;

import com.lijunjie.wms.algorithm.SlottingAssignment;
import com.lijunjie.wms.algorithm.SlottingInstance;
import com.lijunjie.wms.algorithm.SlottingStrategy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * 基于 ABC 分类的分区贪心分配策略。
 *
 * <p>先把货位按到出入口距离切成近、中、远三个分区，然后按 A、B、C 类的顺序分配：
 * A 类商品先占最近的货位，B 类占用剩余货位中最近的，C 类占用最后的货位。
 * 每类内部按出库频次降序处理。</p>
 *
 * <p>该实现采用“逐类占用剩余最近货位”的方式，天然处理某类商品数超过分区容量的情形
 * （溢出部分顺延到后续分区），不会出现容量不匹配。</p>
 */
public class AbcGreedyStrategy implements SlottingStrategy {

    @Override
    public String code() {
        return "abc-greedy";
    }

    @Override
    public String label() {
        return "ABC 分区贪心";
    }

    @Override
    public SlottingAssignment solve(SlottingInstance instance, long seed) {
        long start = System.currentTimeMillis();
        int n = instance.itemCount();
        int slotCount = instance.getLayout().slotCount();
        double[] freqs = instance.getFrequencies();
        double[] ioDistances = instance.getLayout().ioDistances();
        int[] abc = instance.getAbcClass();

        // 货位按距离升序（单一键，与 Python 一致）
        Integer[] slotOrder = new Integer[slotCount];
        for (int i = 0; i < slotCount; i++) {
            slotOrder[i] = i;
        }
        final double[] d = ioDistances;
        Arrays.sort(slotOrder, Comparator.comparingDouble((Integer i) -> d[i]));

        int[] assignment = new int[n];
        Arrays.fill(assignment, -1);
        boolean[] usedSlot = new boolean[slotCount];

        // 按 A、B、C 顺序逐类分配
        for (int cls = 0; cls <= 2; cls++) {
            List<Integer> members = new ArrayList<Integer>();
            for (int i = 0; i < n; i++) {
                if (abc[i] == cls) {
                    members.add(i);
                }
            }
            if (members.isEmpty()) {
                continue;
            }
            // 类内按频次降序
            final double[] f = freqs;
            members.sort(Comparator.comparingDouble((Integer i) -> -f[i]));

            // 取剩余货位中最近的若干个
            int assigned = 0;
            for (Integer slot : slotOrder) {
                if (assigned >= members.size()) {
                    break;
                }
                if (!usedSlot[slot]) {
                    assignment[members.get(assigned)] = slot;
                    usedSlot[slot] = true;
                    assigned++;
                }
            }
        }

        // 兜底：理论上不会出现未分配的商品
        for (int i = 0; i < n; i++) {
            if (assignment[i] < 0) {
                for (int s = 0; s < slotCount; s++) {
                    if (!usedSlot[s]) {
                        assignment[i] = s;
                        usedSlot[s] = true;
                        break;
                    }
                }
            }
        }

        double objective = SlottingStrategy.evaluate(instance, assignment);
        return new SlottingAssignment(label(), assignment, objective, System.currentTimeMillis() - start);
    }
}
