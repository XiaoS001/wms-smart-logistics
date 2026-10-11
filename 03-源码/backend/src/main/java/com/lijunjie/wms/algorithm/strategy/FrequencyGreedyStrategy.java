package com.lijunjie.wms.algorithm.strategy;

import com.lijunjie.wms.algorithm.SlottingAssignment;
import com.lijunjie.wms.algorithm.SlottingInstance;
import com.lijunjie.wms.algorithm.SlottingStrategy;

import java.util.Arrays;
import java.util.Comparator;

/**
 * 基于出库频次的贪心分配策略。
 *
 * <p>把商品按出库频次降序排列、货位按到出入口距离升序排列，然后逐一对齐：
 * 频次最高的商品放入最近的货位。在仅考虑距离目标的模型下该贪心解即为最优解，
 * 因此它既是工程上最常用的启发式，也是遗传算法收敛的下界参考。</p>
 *
 * <p>排序规则与 Python 实现完全一致（仅按频次/距离的单一键排序），
 * 以保证两条实现的目标函数值逐个相同。</p>
 */
public class FrequencyGreedyStrategy implements SlottingStrategy {

    @Override
    public String code() {
        return "frequency-greedy";
    }

    @Override
    public String label() {
        return "频次贪心分配";
    }

    @Override
    public SlottingAssignment solve(SlottingInstance instance, long seed) {
        long start = System.currentTimeMillis();
        int n = instance.itemCount();
        double[] freqs = instance.getFrequencies();
        double[] ioDistances = instance.getLayout().ioDistances();

        // 商品按频次降序
        Integer[] itemOrder = new Integer[n];
        for (int i = 0; i < n; i++) {
            itemOrder[i] = i;
        }
        final double[] f = freqs;
        Arrays.sort(itemOrder, Comparator.comparingDouble((Integer i) -> -f[i]));

        // 货位按距离升序
        int slots = ioDistances.length;
        Integer[] slotOrder = new Integer[slots];
        for (int i = 0; i < slots; i++) {
            slotOrder[i] = i;
        }
        final double[] d = ioDistances;
        Arrays.sort(slotOrder, Comparator.comparingDouble((Integer i) -> d[i]));

        int[] assignment = new int[n];
        for (int k = 0; k < n; k++) {
            assignment[itemOrder[k]] = slotOrder[k];
        }
        double objective = SlottingStrategy.evaluate(instance, assignment);
        return new SlottingAssignment(label(), assignment, objective, System.currentTimeMillis() - start);
    }
}
