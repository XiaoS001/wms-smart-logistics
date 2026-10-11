package com.lijunjie.wms.algorithm.strategy;

import com.lijunjie.wms.algorithm.SlottingAssignment;
import com.lijunjie.wms.algorithm.SlottingInstance;
import com.lijunjie.wms.algorithm.SlottingStrategy;

import java.util.Arrays;
import java.util.Random;

/**
 * 随机分配策略（对照组基线）。
 *
 * <p>在全部货位中随机为每个商品指定货位，不利用任何频次信息。
 * 使用固定种子的 {@link Random}，保证同一算例结果可复现。</p>
 */
public class RandomSlottingStrategy implements SlottingStrategy {

    @Override
    public String code() {
        return "random";
    }

    @Override
    public String label() {
        return "随机分配";
    }

    @Override
    public SlottingAssignment solve(SlottingInstance instance, long seed) {
        long start = System.currentTimeMillis();
        int n = instance.itemCount();
        int slots = instance.getLayout().slotCount();

        int[] pool = new int[slots];
        for (int i = 0; i < slots; i++) {
            pool[i] = i;
        }
        // Fisher-Yates 部分洗牌，取前 n 个作为分配结果
        Random random = new Random(seed);
        for (int i = 0; i < n; i++) {
            int j = i + random.nextInt(slots - i);
            int tmp = pool[i];
            pool[i] = pool[j];
            pool[j] = tmp;
        }
        int[] assignment = Arrays.copyOf(pool, n);
        double objective = SlottingStrategy.evaluate(instance, assignment);
        return new SlottingAssignment(label(), assignment, objective, System.currentTimeMillis() - start);
    }
}
