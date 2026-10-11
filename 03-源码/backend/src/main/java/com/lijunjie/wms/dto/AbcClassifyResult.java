package com.lijunjie.wms.dto;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ABC 分类计算结果。
 *
 * <p>分类规则：商品按出库频次降序排列，累计频次占比达到 70% 的划为 A 类，
 * 70% 至 90% 之间的划为 B 类，其余为 C 类。该规则与算法内核
 * （{@code 03-源码/algorithm/wmsopt/model.py} 的 build_item_profile）保持一致。</p>
 */
public class AbcClassifyResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 参与分类的商品总数 */
    private int totalProducts;

    /** 出库频次合计 */
    private long totalFrequency;

    /** 本次实际更新的商品数量 */
    private int updatedCount;

    /** 各类商品数量，如 {"A": 5, "B": 6, "C": 4} */
    private Map<String, Integer> classCounts = new LinkedHashMap<String, Integer>();

    /** 各类出库频次合计 */
    private Map<String, Long> classFrequencies = new LinkedHashMap<String, Long>();

    /** 各类频次占比（百分比，保留两位小数），用于验证"少数商品贡献多数出库量" */
    private Map<String, Double> classFrequencyRatio = new LinkedHashMap<String, Double>();

    public int getTotalProducts() {
        return totalProducts;
    }

    public void setTotalProducts(int totalProducts) {
        this.totalProducts = totalProducts;
    }

    public long getTotalFrequency() {
        return totalFrequency;
    }

    public void setTotalFrequency(long totalFrequency) {
        this.totalFrequency = totalFrequency;
    }

    public int getUpdatedCount() {
        return updatedCount;
    }

    public void setUpdatedCount(int updatedCount) {
        this.updatedCount = updatedCount;
    }

    public Map<String, Integer> getClassCounts() {
        return classCounts;
    }

    public void setClassCounts(Map<String, Integer> classCounts) {
        this.classCounts = classCounts;
    }

    public Map<String, Long> getClassFrequencies() {
        return classFrequencies;
    }

    public void setClassFrequencies(Map<String, Long> classFrequencies) {
        this.classFrequencies = classFrequencies;
    }

    public Map<String, Double> getClassFrequencyRatio() {
        return classFrequencyRatio;
    }

    public void setClassFrequencyRatio(Map<String, Double> classFrequencyRatio) {
        this.classFrequencyRatio = classFrequencyRatio;
    }
}
