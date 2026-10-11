package com.lijunjie.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 仓库实体，对应表 {@code base_warehouse}。
 *
 * <p>其中的布局参数（巷道数、纵深数、巷道间距、纵深间距）是货位分配与拣货路径规划
 * 算法计算行走距离的基础输入，因此修改这些字段会影响算法的空间模型。</p>
 */
@TableName("base_warehouse")
public class BaseWarehouse implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 仓库编码（唯一） */
    private String warehouseCode;

    /** 仓库名称 */
    private String warehouseName;

    /** 巷道数 X */
    private Integer aisleCount;

    /** 每条巷道的纵深货位数 Y */
    private Integer rowCount;

    /** 相邻巷道间距（米） */
    private BigDecimal aisleGap;

    /** 相邻货位纵深间距（米） */
    private BigDecimal rowGap;

    /** 状态：1 启用 / 0 停用 */
    private Integer status;

    /** 备注 */
    private String remark;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer deleted;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getWarehouseCode() {
        return warehouseCode;
    }

    public void setWarehouseCode(String warehouseCode) {
        this.warehouseCode = warehouseCode;
    }

    public String getWarehouseName() {
        return warehouseName;
    }

    public void setWarehouseName(String warehouseName) {
        this.warehouseName = warehouseName;
    }

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

    public BigDecimal getAisleGap() {
        return aisleGap;
    }

    public void setAisleGap(BigDecimal aisleGap) {
        this.aisleGap = aisleGap;
    }

    public BigDecimal getRowGap() {
        return rowGap;
    }

    public void setRowGap(BigDecimal rowGap) {
        this.rowGap = rowGap;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
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

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

    /** 货位总数 = 巷道数 × 纵深数，供业务层与前端直接使用 */
    public int totalSlots() {
        if (aisleCount == null || rowCount == null) {
            return 0;
        }
        return aisleCount * rowCount;
    }
}
