package com.lijunjie.wms.algorithm;

import java.io.Serializable;
import java.util.Arrays;

/**
 * 货位分配结果。
 *
 * <p>{@code slotOfItem[i]} 表示商品 i 被分配到的货位编号；
 * {@code objective} 为该方案的加权拣货行走距离
 * {@code F = Σ f_i × d(IO, slot(i))}，单位“米·次”。</p>
 */
public class SlottingAssignment implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String strategy;

    private final int[] slotOfItem;

    private final double objective;

    private final long elapsedMillis;

    public SlottingAssignment(String strategy, int[] slotOfItem, double objective, long elapsedMillis) {
        this.strategy = strategy;
        this.slotOfItem = Arrays.copyOf(slotOfItem, slotOfItem.length);
        this.objective = objective;
        this.elapsedMillis = elapsedMillis;
    }

    public String getStrategy() {
        return strategy;
    }

    public int[] getSlotOfItem() {
        return Arrays.copyOf(slotOfItem, slotOfItem.length);
    }

    public double getObjective() {
        return objective;
    }

    public long getElapsedMillis() {
        return elapsedMillis;
    }
}
