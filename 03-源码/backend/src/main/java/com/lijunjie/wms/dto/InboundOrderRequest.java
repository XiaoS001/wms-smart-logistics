package com.lijunjie.wms.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 新建入库单的请求参数。
 */
public class InboundOrderRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 仓库 ID */
    private Long warehouseId;

    /** 供应商 */
    private String supplier;

    /**
     * 是否使用货位分配算法推荐货位。
     *
     * <p>为 true 时，服务端会为每个明细行调用算法推荐目标货位并写入推荐理由；
     * 为 false 时仅保存单据，货位留待人工指定。</p>
     */
    private Boolean useSlottingSuggest;

    /** 货位推荐所用策略：random / frequency-greedy / abc-greedy / genetic */
    private String strategy;

    private String remark;

    /** 明细行 */
    private List<Item> items = new ArrayList<Item>();

    /** 单条明细 */
    public static class Item implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long productId;

        private BigDecimal planQuantity;

        private BigDecimal unitPrice;

        private String batchNo;

        /** 人工指定的货位（可选，指定后不再使用算法推荐） */
        private Long locationId;

        public Long getProductId() {
            return productId;
        }

        public void setProductId(Long productId) {
            this.productId = productId;
        }

        public BigDecimal getPlanQuantity() {
            return planQuantity;
        }

        public void setPlanQuantity(BigDecimal planQuantity) {
            this.planQuantity = planQuantity;
        }

        public BigDecimal getUnitPrice() {
            return unitPrice;
        }

        public void setUnitPrice(BigDecimal unitPrice) {
            this.unitPrice = unitPrice;
        }

        public String getBatchNo() {
            return batchNo;
        }

        public void setBatchNo(String batchNo) {
            this.batchNo = batchNo;
        }

        public Long getLocationId() {
            return locationId;
        }

        public void setLocationId(Long locationId) {
            this.locationId = locationId;
        }
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    public String getSupplier() {
        return supplier;
    }

    public void setSupplier(String supplier) {
        this.supplier = supplier;
    }

    public Boolean getUseSlottingSuggest() {
        return useSlottingSuggest;
    }

    public void setUseSlottingSuggest(Boolean useSlottingSuggest) {
        this.useSlottingSuggest = useSlottingSuggest;
    }

    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
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
