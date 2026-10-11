package com.lijunjie.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 出库单实体，对应表 {@code biz_outbound_order}。
 *
 * <p>{@code routeDistance} / {@code routeDetail} / {@code routeAlgo} 三个字段记录
 * 拣货路径规划算法的结果，使单据本身就能回答“按什么顺序拣、走了多远、用了什么算法”。</p>
 */
@TableName("biz_outbound_order")
public class BizOutboundOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态：待拣货 */
    public static final int STATUS_PENDING = 0;

    /** 状态：拣货中 */
    public static final int STATUS_PICKING = 1;

    /** 状态：已完成 */
    public static final int STATUS_DONE = 2;

    /** 状态：已取消 */
    public static final int STATUS_CANCELLED = 9;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private Long warehouseId;

    /** 客户 */
    private String customer;

    private BigDecimal totalQuantity;

    private Integer status;

    /** 拣货路径总长度（米） */
    private BigDecimal routeDistance;

    /** 拣货顺序明细（JSON 文本，记录坐标序列，供前端可视化） */
    private String routeDetail;

    /** 所用路径规划算法编码 */
    private String routeAlgo;

    private Long operatorId;

    private Date outboundTime;

    private String remark;

    private Date createTime;

    @TableLogic
    private Integer deleted;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    public String getCustomer() {
        return customer;
    }

    public void setCustomer(String customer) {
        this.customer = customer;
    }

    public BigDecimal getTotalQuantity() {
        return totalQuantity;
    }

    public void setTotalQuantity(BigDecimal totalQuantity) {
        this.totalQuantity = totalQuantity;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public BigDecimal getRouteDistance() {
        return routeDistance;
    }

    public void setRouteDistance(BigDecimal routeDistance) {
        this.routeDistance = routeDistance;
    }

    public String getRouteDetail() {
        return routeDetail;
    }

    public void setRouteDetail(String routeDetail) {
        this.routeDetail = routeDetail;
    }

    public String getRouteAlgo() {
        return routeAlgo;
    }

    public void setRouteAlgo(String routeAlgo) {
        this.routeAlgo = routeAlgo;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public Date getOutboundTime() {
        return outboundTime;
    }

    public void setOutboundTime(Date outboundTime) {
        this.outboundTime = outboundTime;
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

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }
}
