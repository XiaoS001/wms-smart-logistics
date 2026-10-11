package com.lijunjie.wms.algorithm;

import java.io.Serializable;

/**
 * 仓库布局参数（算法侧模型）。
 *
 * <p>与 Python 算法原型 {@code 03-源码/algorithm/wmsopt/model.py} 的 WarehouseLayout 保持一致：
 * 仓库采用“巷道 × 货架”的网格化表示，货位坐标为 (x, y)，出入口 I/O 为 (0, 0)，
 * 行走距离采用曼哈顿度量。</p>
 *
 * <p>该模型是货位分配与拣货路径规划算法计算距离的基础，因此其字段含义与
 * {@code base_warehouse} 表一一对应。</p>
 */
public class WarehouseLayout implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 巷道数 X */
    private final int aisles;

    /** 每条巷道的纵深货位数 Y */
    private final int rows;

    /** 相邻巷道间距（米） */
    private final double aisleGap;

    /** 相邻货位纵深间距（米） */
    private final double rowGap;

    public WarehouseLayout(int aisles, int rows, double aisleGap, double rowGap) {
        if (aisles <= 0 || rows <= 0) {
            throw new IllegalArgumentException("巷道数与纵深数必须大于 0");
        }
        if (aisleGap <= 0 || rowGap <= 0) {
            throw new IllegalArgumentException("巷道间距与纵深间距必须大于 0");
        }
        this.aisles = aisles;
        this.rows = rows;
        this.aisleGap = aisleGap;
        this.rowGap = rowGap;
    }

    public int getAisles() {
        return aisles;
    }

    public int getRows() {
        return rows;
    }

    public double getAisleGap() {
        return aisleGap;
    }

    public double getRowGap() {
        return rowGap;
    }

    /** 货位总数 */
    public int slotCount() {
        return aisles * rows;
    }

    /**
     * 货位编号到坐标的映射。
     *
     * <p>编号规则与 Python 内核一致：按 (x, y) 字典序排列，
     * 即编号 k 对应 x = k / rows + 1、y = k % rows + 1。</p>
     *
     * @param slot 货位编号，从 0 开始
     * @return 长度为 2 的数组 [x, y]
     */
    public int[] slotCoordinate(int slot) {
        int x = slot / rows + 1;
        int y = slot % rows + 1;
        return new int[]{x, y};
    }

    /** 货位编码（与 {@code base_location.location_code} 规则一致，如 A01-05） */
    public String slotCode(int slot) {
        int[] p = slotCoordinate(slot);
        return String.format("A%02d-%02d", p[0], p[1]);
    }

    /**
     * 货位到出入口的行走距离。
     *
     * @param slot 货位编号，从 0 开始
     */
    public double distanceFromIo(int slot) {
        int[] p = slotCoordinate(slot);
        return aisleGap * p[0] + rowGap * p[1];
    }

    /**
     * 各货位到出入口的距离数组，索引即货位编号。
     */
    public double[] ioDistances() {
        double[] d = new double[slotCount()];
        for (int k = 0; k < d.length; k++) {
            d[k] = distanceFromIo(k);
        }
        return d;
    }
}
