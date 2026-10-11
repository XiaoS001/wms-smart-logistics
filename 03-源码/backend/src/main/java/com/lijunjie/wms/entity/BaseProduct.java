package com.lijunjie.wms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 商品实体，对应表 {@code base_product}。
 *
 * <p>{@code pickFreq}（累计出库频次）与 {@code abcClass}（ABC 分类）是货位分配算法的
 * 两个核心输入：算法按频次与距离配对求解，ABC 分类则用于分区存储约束。</p>
 */
@TableName("base_product")
public class BaseProduct implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 商品编码（SKU，唯一） */
    private String productCode;

    /** 商品名称 */
    private String productName;

    /** 分类 ID */
    private Long categoryId;

    /** 规格 */
    private String spec;

    /** 计量单位 */
    private String unit;

    /** 重量（kg） */
    private BigDecimal weight;

    /** 体积（m³） */
    private BigDecimal volume;

    /** 累计出库频次（货位分配算法的输入） */
    private Integer pickFreq;

    /** ABC 分类：A / B / C */
    private String abcClass;

    /** 安全库存 */
    private BigDecimal safetyStock;

    /** 状态：1 启用 / 0 停用 */
    private Integer status;

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

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getSpec() {
        return spec;
    }

    public void setSpec(String spec) {
        this.spec = spec;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public void setWeight(BigDecimal weight) {
        this.weight = weight;
    }

    public BigDecimal getVolume() {
        return volume;
    }

    public void setVolume(BigDecimal volume) {
        this.volume = volume;
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

    public BigDecimal getSafetyStock() {
        return safetyStock;
    }

    public void setSafetyStock(BigDecimal safetyStock) {
        this.safetyStock = safetyStock;
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
}
