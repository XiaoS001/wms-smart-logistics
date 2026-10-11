package com.lijunjie.wms.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 货位推荐结果（算法与业务的结合点）。
 *
 * <p>推荐过程分两步：</p>
 * <ol>
 *   <li><b>全局分配</b>：以“全部货位 + 全部商品”为算例，用选定的货位分配策略求解，
 *       得到目标函数值与平均加权行走距离；</li>
 *   <li><b>本次推荐</b>：对本次入库的商品，按出库频次降序优先挑选距离最近的空闲货位，
 *       并给出可读的推荐理由。</li>
 * </ol>
 *
 * <p>这样做的原因：入库通常是分批进行的，而货位分配是一个全局问题。
 * 用全局求解给出目标函数值（可与论文实验数据对照），用贪心挑选保证单次入库的响应时间。</p>
 */
public class SlottingRecommendResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 使用的策略编码 */
    private String strategy;

    /** 策略显示名称 */
    private String strategyLabel;

    /** 全局分配的目标函数值（米·次），可与论文实验数据对照 */
    private double objective;

    /** 平均加权行走距离 = objective / 频次合计（米） */
    private double averageDistance;

    /** 参与全局分配的商品数 */
    private int itemCount;

    /** 参与全局分配的货位数 */
    private int slotCount;

    /** 算法耗时（毫秒） */
    private long elapsedMillis;

    /** 当前空闲货位数 */
    private int freeSlotCount;

    /** 本次推荐明细 */
    private List<Recommendation> recommendations = new ArrayList<Recommendation>();

    /** 单条推荐 */
    public static class Recommendation implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long productId;

        private String productCode;

        private String productName;

        private Integer pickFreq;

        private String abcClass;

        private Long locationId;

        private String locationCode;

        private Double distance;

        /** 推荐理由，如“A 类商品优先分配近端货位” */
        private String reason;

        public Long getProductId() {
            return productId;
        }

        public void setProductId(Long productId) {
            this.productId = productId;
        }

        public String getProductCode() {
            return productCode;
        }

        public void setProductCode(String productCode) {
            this.productCode = productCode;
        }

        public String getProductName() {
            return productName;
        }

        public void setProductName(String productName) {
            this.productName = productName;
        }

        public Integer getPickFreq() {
            return pickFreq;
        }

        public void setPickFreq(Integer pickFreq) {
            this.pickFreq = pickFreq;
        }

        public String getAbcClass() {
            return abcClass;
        }

        public void setAbcClass(String abcClass) {
            this.abcClass = abcClass;
        }

        public Long getLocationId() {
            return locationId;
        }

        public void setLocationId(Long locationId) {
            this.locationId = locationId;
        }

        public String getLocationCode() {
            return locationCode;
        }

        public void setLocationCode(String locationCode) {
            this.locationCode = locationCode;
        }

        public Double getDistance() {
            return distance;
        }

        public void setDistance(Double distance) {
            this.distance = distance;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }

    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public String getStrategyLabel() {
        return strategyLabel;
    }

    public void setStrategyLabel(String strategyLabel) {
        this.strategyLabel = strategyLabel;
    }

    public double getObjective() {
        return objective;
    }

    public void setObjective(double objective) {
        this.objective = objective;
    }

    public double getAverageDistance() {
        return averageDistance;
    }

    public void setAverageDistance(double averageDistance) {
        this.averageDistance = averageDistance;
    }

    public int getItemCount() {
        return itemCount;
    }

    public void setItemCount(int itemCount) {
        this.itemCount = itemCount;
    }

    public int getSlotCount() {
        return slotCount;
    }

    public void setSlotCount(int slotCount) {
        this.slotCount = slotCount;
    }

    public long getElapsedMillis() {
        return elapsedMillis;
    }

    public void setElapsedMillis(long elapsedMillis) {
        this.elapsedMillis = elapsedMillis;
    }

    public int getFreeSlotCount() {
        return freeSlotCount;
    }

    public void setFreeSlotCount(int freeSlotCount) {
        this.freeSlotCount = freeSlotCount;
    }

    public List<Recommendation> getRecommendations() {
        return recommendations;
    }

    public void setRecommendations(List<Recommendation> recommendations) {
        this.recommendations = recommendations;
    }
}
