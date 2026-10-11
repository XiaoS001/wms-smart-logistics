package com.lijunjie.wms.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 提交盘点结果的请求参数。
 */
public class StocktakeSubmitRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 盘点单号 */
    private String stocktakeNo;

    /** 是否在录入实盘数量后立即调整库存（写入差异并使货位状态保持一致） */
    private Boolean adjustStock;

    private String remark;

    private List<Entry> entries = new ArrayList<Entry>();

    /** 单条实盘记录 */
    public static class Entry implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 盘点记录 ID */
        private Long id;

        /** 实盘数量 */
        private BigDecimal actualQuantity;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public BigDecimal getActualQuantity() {
            return actualQuantity;
        }

        public void setActualQuantity(BigDecimal actualQuantity) {
            this.actualQuantity = actualQuantity;
        }
    }

    public String getStocktakeNo() {
        return stocktakeNo;
    }

    public void setStocktakeNo(String stocktakeNo) {
        this.stocktakeNo = stocktakeNo;
    }

    public Boolean getAdjustStock() {
        return adjustStock;
    }

    public void setAdjustStock(Boolean adjustStock) {
        this.adjustStock = adjustStock;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public List<Entry> getEntries() {
        return entries;
    }

    public void setEntries(List<Entry> entries) {
        this.entries = entries;
    }
}
