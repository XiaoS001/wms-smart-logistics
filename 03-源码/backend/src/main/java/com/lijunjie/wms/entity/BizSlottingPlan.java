package com.lijunjie.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 货位优化方案实体，对应表 {@code biz_slotting_plan}。
 *
 * <p>记录每次货位优化的策略、参数与效果：优化前后的加权拣货行走距离
 * 与降幅百分比，与《算法设计说明书》的实验口径一致。</p>
 */
@TableName("biz_slotting_plan")
public class BizSlottingPlan implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String planNo;

    private Long warehouseId;

    /** 优化策略编码 */
    private String strategy;

    private String strategyLabel;

    /** 随机种子，保证结果可复现 */
    private Long seed;

    private Integer productCount;

    private Integer slotCount;

    /** 优化前加权行走距离（米·次） */
    private BigDecimal currentObjective;

    /** 优化后加权行走距离（米·次） */
    private BigDecimal optimizedObjective;

    /** 降幅百分比 */
    private BigDecimal improvementPercent;

    private Long elapsedMillis;

    /** 是否已应用：1 是 / 0 否 */
    private Integer applied;

    private Date appliedTime;

    /** 应用时搬动的库存记录数 */
    private Integer movedRecords;

    /** 分配明细（JSON） */
    private String planDetail;

    private Long operatorId;

    private String remark;

    private Date createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPlanNo() {
        return planNo;
    }

    public void setPlanNo(String planNo) {
        this.planNo = planNo;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
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

    public Long getSeed() {
        return seed;
    }

    public void setSeed(Long seed) {
        this.seed = seed;
    }

    public Integer getProductCount() {
        return productCount;
    }

    public void setProductCount(Integer productCount) {
        this.productCount = productCount;
    }

    public Integer getSlotCount() {
        return slotCount;
    }

    public void setSlotCount(Integer slotCount) {
        this.slotCount = slotCount;
    }

    public BigDecimal getCurrentObjective() {
        return currentObjective;
    }

    public void setCurrentObjective(BigDecimal currentObjective) {
        this.currentObjective = currentObjective;
    }

    public BigDecimal getOptimizedObjective() {
        return optimizedObjective;
    }

    public void setOptimizedObjective(BigDecimal optimizedObjective) {
        this.optimizedObjective = optimizedObjective;
    }

    public BigDecimal getImprovementPercent() {
        return improvementPercent;
    }

    public void setImprovementPercent(BigDecimal improvementPercent) {
        this.improvementPercent = improvementPercent;
    }

    public Long getElapsedMillis() {
        return elapsedMillis;
    }

    public void setElapsedMillis(Long elapsedMillis) {
        this.elapsedMillis = elapsedMillis;
    }

    public Integer getApplied() {
        return applied;
    }

    public void setApplied(Integer applied) {
        this.applied = applied;
    }

    public Date getAppliedTime() {
        return appliedTime;
    }

    public void setAppliedTime(Date appliedTime) {
        this.appliedTime = appliedTime;
    }

    public Integer getMovedRecords() {
        return movedRecords;
    }

    public void setMovedRecords(Integer movedRecords) {
        this.movedRecords = movedRecords;
    }

    public String getPlanDetail() {
        return planDetail;
    }

    public void setPlanDetail(String planDetail) {
        this.planDetail = planDetail;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
}
