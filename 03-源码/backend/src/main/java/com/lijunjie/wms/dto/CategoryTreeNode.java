package com.lijunjie.wms.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 商品分类树节点。
 *
 * <p>分类数量有限（通常几十个），因此采用一次性读出全部记录后在内存中组装的方式，
 * 避免递归查询数据库带来的 N+1 问题。</p>
 */
public class CategoryTreeNode implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long parentId;

    private String categoryName;

    private Integer sort;

    /** 该分类下的商品数量 */
    private int productCount;

    /** 子分类 */
    private List<CategoryTreeNode> children = new ArrayList<CategoryTreeNode>();

    public CategoryTreeNode() {
    }

    public CategoryTreeNode(Long id, Long parentId, String categoryName, Integer sort, int productCount) {
        this.id = id;
        this.parentId = parentId;
        this.categoryName = categoryName;
        this.sort = sort;
        this.productCount = productCount;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public Integer getSort() {
        return sort;
    }

    public void setSort(Integer sort) {
        this.sort = sort;
    }

    public int getProductCount() {
        return productCount;
    }

    public void setProductCount(int productCount) {
        this.productCount = productCount;
    }

    public List<CategoryTreeNode> getChildren() {
        return children;
    }

    public void setChildren(List<CategoryTreeNode> children) {
        this.children = children;
    }
}
