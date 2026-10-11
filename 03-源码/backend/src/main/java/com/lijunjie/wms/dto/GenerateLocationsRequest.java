package com.lijunjie.wms.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 批量生成货位的请求参数。
 *
 * <p>货位坐标与到出入口距离是货位分配算法的输入，因此生成规则必须明确、可复现：
 * 距离按 {@code distance = aisleGap × x + rowGap × y} 计算，
 * 库区按距离阈值切分（默认 15 米内为 A 区、30 米内为 B 区、其余为 C 区）。</p>
 */
public class GenerateLocationsRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 巷道数 X；为空时取仓库自身的布局参数 */
    private Integer aisleCount;

    /** 纵深数 Y；为空时取仓库自身的布局参数 */
    private Integer rowCount;

    /** A 区最大距离（米），默认 15 */
    private BigDecimal aisleDistance;

    /** B 区最大距离（米），默认 30 */
    private BigDecimal rowDistance;

    /** 货位容量，默认 100 */
    private BigDecimal capacity;

    /** 是否覆盖已有货位：true 先清空该仓库货位再生成；false 跳过已存在的坐标 */
    private Boolean overwrite;

    public Integer getAisleCount() {
        return aisleCount;
    }

    public void setAisleCount(Integer aisleCount) {
        this.aisleCount = aisleCount;
    }

    public Integer getRowCount() {
        return rowCount;
    }

    public void setRowCount(Integer rowCount) {
        this.rowCount = rowCount;
    }

    public BigDecimal getAisleDistance() {
        return aisleDistance;
    }

    public void setAisleDistance(BigDecimal aisleDistance) {
        this.aisleDistance = aisleDistance;
    }

    public BigDecimal getRowDistance() {
        return rowDistance;
    }

    public void setRowDistance(BigDecimal rowDistance) {
        this.rowDistance = rowDistance;
    }

    public BigDecimal getCapacity() {
        return capacity;
    }

    public void setCapacity(BigDecimal capacity) {
        this.capacity = capacity;
    }

    public Boolean getOverwrite() {
        return overwrite;
    }

    public void setOverwrite(Boolean overwrite) {
        this.overwrite = overwrite;
    }
}
