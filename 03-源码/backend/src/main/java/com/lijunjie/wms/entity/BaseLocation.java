package com.lijunjie.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 货位实体，对应表 {@code base_location}。
 *
 * <p>货位坐标 (aisleNo, rowNo) 与到出入口距离是货位分配算法的输入。
 * {@code distance} 为冗余字段，由生成时按仓库布局参数计算并存储，
 * 使“按距离取近端货位”这一高频操作可以直接命中索引。</p>
 */
@TableName("base_location")
public class BaseLocation implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long warehouseId;

    /** 所属库区 ID（按距离划分的 A/B/C 区） */
    private Long zoneId;

    /** 货位编码，如 A01-05 */
    private String locationCode;

    /** 巷道号 x（从 1 开始） */
    private Integer aisleNo;

    /** 纵深号 y（从 1 开始） */
    private Integer rowNo;

    /** 到出入口的行走距离（米） */
    private BigDecimal distance;

    /** 容量 */
    private BigDecimal capacity;

    /** 状态：1 空闲 / 2 占用 / 0 停用 */
    private Integer status;

    private Date createTime;

    @TableLogic
    private Integer deleted;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    public Long getZoneId() {
        return zoneId;
    }

    public void setZoneId(Long zoneId) {
        this.zoneId = zoneId;
    }

    public String getLocationCode() {
        return locationCode;
    }

    public void setLocationCode(String locationCode) {
        this.locationCode = locationCode;
    }

    public Integer getAisleNo() {
        return aisleNo;
    }

    public void setAisleNo(Integer aisleNo) {
        this.aisleNo = aisleNo;
    }

    public Integer getRowNo() {
        return rowNo;
    }

    public void setRowNo(Integer rowNo) {
        this.rowNo = rowNo;
    }

    public BigDecimal getDistance() {
        return distance;
    }

    public void setDistance(BigDecimal distance) {
        this.distance = distance;
    }

    public BigDecimal getCapacity() {
        return capacity;
    }

    public void setCapacity(BigDecimal capacity) {
        this.capacity = capacity;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }
}
