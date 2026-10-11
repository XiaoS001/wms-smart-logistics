package com.lijunjie.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 库存盘点实体，对应表 {@code biz_stocktake}。
 *
 * <p>每条记录对应“某货位上的某商品”的一次盘点：账面数量取自库存表，
 * 实盘数量由人工录入，差异数量 = 实盘 − 账面。调整后写回库存表。</p>
 */
@TableName("biz_stocktake")
public class BizStocktake implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态：待盘点 */
    public static final int STATUS_PENDING = 0;

    /** 状态：已盘点（已录入实盘数量，尚未调整库存） */
    public static final int STATUS_COUNTED = 1;

    /** 状态：已调整（差异已写回库存） */
    public static final int STATUS_ADJUSTED = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String stocktakeNo;

    private Long warehouseId;

    private Long locationId;

    private Long productId;

    private BigDecimal bookQuantity;

    private BigDecimal actualQuantity;

    private BigDecimal diffQuantity;

    private Integer status;

    private Long operatorId;

    private Date stocktakeTime;

    private String remark;

    private Date createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getStocktakeNo() {
        return stocktakeNo;
    }

    public void setStocktakeNo(String stocktakeNo) {
        this.stocktakeNo = stocktakeNo;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    public Long getLocationId() {
        return locationId;
    }

    public void setLocationId(Long locationId) {
        this.locationId = locationId;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public BigDecimal getBookQuantity() {
        return bookQuantity;
    }

    public void setBookQuantity(BigDecimal bookQuantity) {
        this.bookQuantity = bookQuantity;
    }

    public BigDecimal getActualQuantity() {
        return actualQuantity;
    }

    public void setActualQuantity(BigDecimal actualQuantity) {
        this.actualQuantity = actualQuantity;
    }

    public BigDecimal getDiffQuantity() {
        return diffQuantity;
    }

    public void setDiffQuantity(BigDecimal diffQuantity) {
        this.diffQuantity = diffQuantity;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public Date getStocktakeTime() {
        return stocktakeTime;
    }

    public void setStocktakeTime(Date stocktakeTime) {
        this.stocktakeTime = stocktakeTime;
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
