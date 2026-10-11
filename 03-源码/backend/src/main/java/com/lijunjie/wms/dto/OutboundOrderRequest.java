package com.lijunjie.wms.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 新建出库单的请求参数。
 */
public class OutboundOrderRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long warehouseId;

    /** 客户 */
    private String customer;

    private String remark;

    private List<Item> items = new ArrayList<Item>();

    /** 单条明细 */
    public static class Item implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long productId;

        private BigDecimal quantity;

        public Long getProductId() {
            return productId;
        }

        public void setProductId(Long productId) {
            this.productId = productId;
        }

        public BigDecimal getQuantity() {
            return quantity;
        }

        public void setQuantity(BigDecimal quantity) {
            this.quantity = quantity;
        }
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

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public List<Item> getItems() {
        return items;
    }

    public void setItems(List<Item> items) {
        this.items = items;
    }
}
