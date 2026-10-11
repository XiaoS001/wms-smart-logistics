package com.lijunjie.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 出库单明细实体，对应表 {@code biz_outbound_item}。
 *
 * <p>{@code pickSeq} 记录该拣货点在最优路径中的访问顺序，
 * 是拣货路径规划算法落到业务数据上的直接体现。</p>
 */
@TableName("biz_outbound_item")
public class BizOutboundItem implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long productId;

    /** 出库货位（由库存所在货位决定） */
    private Long locationId;

    private BigDecimal quantity;

    /** 拣货顺序，从 1 开始；0 表示尚未规划 */
    private Integer pickSeq;

    /** 是否已拣：1 已拣 / 0 未拣 */
    private Integer picked;

    private Date createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Long getLocationId() {
        return locationId;
    }

    public void setLocationId(Long locationId) {
        this.locationId = locationId;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public Integer getPickSeq() {
        return pickSeq;
    }

    public void setPickSeq(Integer pickSeq) {
        this.pickSeq = pickSeq;
    }

    public Integer getPicked() {
        return picked;
    }

    public void setPicked(Integer picked) {
        this.picked = picked;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
}
