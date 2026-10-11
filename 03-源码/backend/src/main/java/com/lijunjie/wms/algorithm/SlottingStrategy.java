package com.lijunjie.wms.algorithm;

/**
 * 货位分配策略接口。
 *
 * <p>实现类对应论文 K2 中的三类求解算法：随机分配、基于出库频次的贪心分配、
 * 基于 ABC 分类的分区贪心，以及基于遗传算法的智能分配。</p>
 *
 * <p><b>实现约定</b>：确定性策略（贪心类）必须与 Python 算法原型
 * （{@code 03-源码/algorithm/wmsopt/slotting.py}）在同一算例上给出完全一致的
 * 目标函数值，以便系统与论文实验数据相互印证。</p>
 */
public interface SlottingStrategy {

    /** 策略标识，如 frequency-greedy */
    String code();

    /** 策略显示名称 */
    String label();

    /**
     * 求解货位分配方案。
     *
     * @param instance 算例（布局 + 商品频次）
     * @param seed     随机种子，确定性策略忽略该参数
     * @return 分配方案与目标函数值
     */
    SlottingAssignment solve(SlottingInstance instance, long seed);

    /**
     * 计算给定分配方案的目标函数值。
     *
     * <p>所有策略共用此口径，保证横向可比。</p>
     *
     * @param instance   算例
     * @param slotOfItem 商品到货位的分配数组
     * @return 加权拣货行走距离
     */
    static double evaluate(SlottingInstance instance, int[] slotOfItem) {
        double[] ioDistances = instance.getLayout().ioDistances();
        double[] freqs = instance.getFrequencies();
        double sum = 0.0;
        for (int i = 0; i < slotOfItem.length; i++) {
            sum += freqs[i] * ioDistances[slotOfItem[i]];
        }
        return sum;
    }
}
