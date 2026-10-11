package com.lijunjie.wms.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 批量生成货位的结果。
 */
public class GenerateLocationsResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 仓库 ID */
    private Long warehouseId;

    /** 本次生成的货位数 */
    private int created;

    /** 因坐标已存在而跳过的数量 */
    private int skipped;

    /** 覆盖模式下清除的旧货位数 */
    private int removed;

    /** 生成后该仓库的货位总数 */
    private long total;

    /** 各库区的货位数量，如 {"A": 30, "B": 98, "C": 72} */
    private Map<String, Integer> zoneCounts = new LinkedHashMap<String, Integer>();

    /** 最短距离（米） */
    private BigDecimal minDistance;

    /** 最长距离（米） */
    private BigDecimal maxDistance;

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    public int getCreated() {
        return created;
    }

    public void setCreated(int created) {
        this.created = created;
    }

    public int getSkipped() {
        return skipped;
    }

    public void setSkipped(int skipped) {
        this.skipped = skipped;
    }

    public int getRemoved() {
        return removed;
    }

    public void setRemoved(int removed) {
        this.removed = removed;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public Map<String, Integer> getZoneCounts() {
        return zoneCounts;
    }

    public void setZoneCounts(Map<String, Integer> zoneCounts) {
        this.zoneCounts = zoneCounts;
    }

    public BigDecimal getMinDistance() {
        return minDistance;
    }

    public void setMinDistance(BigDecimal minDistance) {
        this.minDistance = minDistance;
    }

    public BigDecimal getMaxDistance() {
        return maxDistance;
    }

    public void setMaxDistance(BigDecimal maxDistance) {
        this.maxDistance = maxDistance;
    }
}
