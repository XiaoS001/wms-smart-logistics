package com.lijunjie.wms.algorithm;

import java.io.Serializable;
import java.util.Arrays;

/**
 * 货位分配算例：仓库布局 + 商品出库频次画像。
 *
 * <p>与 Python 算法原型中的 WarehouseInstance 对应。算法的目标是
 * 最小化加权拣货行走距离：{@code F = Σ f_i × d(IO, slot(i))}。</p>
 */
public class SlottingInstance implements Serializable {

    private static final long serialVersionUID = 1L;

    private final WarehouseLayout layout;

    /** 各商品出库频次，索引即商品编号 */
    private final double[] frequencies;

    /** 各商品 ABC 分类：0 = A、1 = B、2 = C（可空，按需计算） */
    private final int[] abcClass;

    public SlottingInstance(WarehouseLayout layout, double[] frequencies) {
        this(layout, frequencies, null);
    }

    public SlottingInstance(WarehouseLayout layout, double[] frequencies, int[] abcClass) {
        if (layout == null) {
            throw new IllegalArgumentException("布局参数不能为空");
        }
        if (frequencies == null || frequencies.length == 0) {
            throw new IllegalArgumentException("商品出库频次不能为空");
        }
        if (frequencies.length > layout.slotCount()) {
            throw new IllegalArgumentException(
                    "商品数(" + frequencies.length + ") 超过货位数(" + layout.slotCount() + ")，本模型假设一货位一商品");
        }
        this.layout = layout;
        this.frequencies = Arrays.copyOf(frequencies, frequencies.length);
        this.abcClass = abcClass == null ? deriveAbcClass(this.frequencies) : Arrays.copyOf(abcClass, abcClass.length);
    }

    public WarehouseLayout getLayout() {
        return layout;
    }

    public double[] getFrequencies() {
        return Arrays.copyOf(frequencies, frequencies.length);
    }

    public int[] getAbcClass() {
        return Arrays.copyOf(abcClass, abcClass.length);
    }

    public int itemCount() {
        return frequencies.length;
    }

    /**
     * 按累计频次占比划分 ABC 分类。
     *
     * <p>规则与 Python 算法内核<b>完全一致</b>（见 model.py 的 build_item_profile）：
     * 商品按频次降序排列后累加，累计占比首次<b>超过</b> 70% 的那个商品归入 A 类，
     * 首次超过 90% 的归入 B 类，其余为 C 类。</p>
     *
     * <p>注意边界语义：Python 使用 {@code searchsorted(cumulative, 0.70) + 1}，
     * 即“首次跨过阈值的位置再加一”。若写成 {@code ratio <= 0.70}，在临界点上会
     * 与 Python 相差一个商品，因此此处严格按 searchsorted 的语义实现。</p>
     *
     * @param freqs 出库频次数组
     * @return 与输入同序的分类数组，取值 0 = A、1 = B、2 = C
     */
    public static int[] deriveAbcClass(double[] freqs) {
        int n = freqs.length;
        int[] result = new int[n];
        if (n == 0) {
            return result;
        }

        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        final double[] f = freqs;
        Arrays.sort(order, (a, b) -> Double.compare(f[b], f[a]));   // 频次降序

        double total = 0;
        for (double v : freqs) {
            total += v;
        }
        if (total <= 0) {
            return result;
        }

        // 按降序累加，记录每个位置上的累计占比
        double[] cumulative = new double[n];
        double running = 0;
        for (int i = 0; i < n; i++) {
            running += freqs[order[i]];
            cumulative[i] = running / total;
        }

        int aEnd = firstIndexExceeding(cumulative, 0.70) + 1;   // A 类商品个数
        int bEnd = firstIndexExceeding(cumulative, 0.90) + 1;   // A + B 类商品个数
        aEnd = Math.min(aEnd, n);
        bEnd = Math.min(Math.max(bEnd, aEnd), n);

        for (int i = 0; i < n; i++) {
            int item = order[i];
            result[item] = i < aEnd ? 0 : (i < bEnd ? 1 : 2);
        }
        return result;
    }

    /**
     * 返回累计占比数组中首个<b>大于</b>阈值的下标，等价于 Python 的
     * {@code np.searchsorted(cumulative, threshold, side='left')}。
     *
     * @return 首个超过阈值的下标；若都不超过则返回数组长度
     */
    private static int firstIndexExceeding(double[] cumulative, double threshold) {
        for (int i = 0; i < cumulative.length; i++) {
            if (cumulative[i] > threshold) {
                return i;
            }
        }
        return cumulative.length;
    }
}
