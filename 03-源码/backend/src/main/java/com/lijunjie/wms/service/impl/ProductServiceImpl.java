package com.lijunjie.wms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lijunjie.wms.common.exception.BusinessException;
import com.lijunjie.wms.dto.AbcClassifyResult;
import com.lijunjie.wms.dto.CategoryTreeNode;
import com.lijunjie.wms.entity.BaseCategory;
import com.lijunjie.wms.entity.BaseProduct;
import com.lijunjie.wms.mapper.BaseCategoryMapper;
import com.lijunjie.wms.mapper.BaseProductMapper;
import com.lijunjie.wms.service.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品与分类业务实现。
 *
 * <p>本模块维护商品主数据与出库频次。出库频次与 ABC 分类是货位分配算法的两个核心输入：
 * 算法按频次与距离配对求解，ABC 分类用于分区存储约束。因此 ABC 分类的划分规则
 * 必须与算法内核保持一致（累计频次 70% / 90% 两个分界点）。</p>
 */
@Service
public class ProductServiceImpl implements ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductServiceImpl.class);

    /** A 类累计频次占比分界点 */
    private static final double ABC_A_THRESHOLD = 0.70;

    /** B 类累计频次占比分界点 */
    private static final double ABC_B_THRESHOLD = 0.90;

    /** 默认安全库存 */
    private static final BigDecimal DEFAULT_SAFETY_STOCK = BigDecimal.ZERO;

    private final BaseProductMapper productMapper;
    private final BaseCategoryMapper categoryMapper;

    public ProductServiceImpl(BaseProductMapper productMapper, BaseCategoryMapper categoryMapper) {
        this.productMapper = productMapper;
        this.categoryMapper = categoryMapper;
    }

    // ------------------------------------------------------------------
    // 商品
    // ------------------------------------------------------------------

    @Override
    public List<BaseProduct> listProducts(String keyword, Long categoryId, String abcClass,
                                          Integer status, int limit) {
        LambdaQueryWrapper<BaseProduct> q = new LambdaQueryWrapper<BaseProduct>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            String k = keyword.trim();
            q.and(w -> w.like(BaseProduct::getProductCode, k)
                        .or().like(BaseProduct::getProductName, k));
        }
        if (categoryId != null) {
            q.eq(BaseProduct::getCategoryId, categoryId);
        }
        if (abcClass != null && !abcClass.trim().isEmpty()) {
            q.eq(BaseProduct::getAbcClass, abcClass.trim().toUpperCase());
        }
        if (status != null) {
            q.eq(BaseProduct::getStatus, status);
        }
        q.orderByDesc(BaseProduct::getPickFreq).orderByAsc(BaseProduct::getId);
        q.last("LIMIT " + Math.max(1, Math.min(limit, 5000)));
        return productMapper.selectList(q);
    }

    @Override
    public BaseProduct getProduct(Long id) {
        return productMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseProduct createProduct(BaseProduct product) {
        validateProduct(product, null);
        product.setId(null);
        if (product.getPickFreq() == null) {
            product.setPickFreq(0);
        }
        if (product.getAbcClass() == null || product.getAbcClass().trim().isEmpty()) {
            product.setAbcClass("C");
        }
        if (product.getSafetyStock() == null) {
            product.setSafetyStock(DEFAULT_SAFETY_STOCK);
        }
        if (product.getStatus() == null) {
            product.setStatus(1);
        }
        productMapper.insert(product);
        log.info("product created: id={}, code={}", product.getId(), product.getProductCode());
        return productMapper.selectById(product.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseProduct updateProduct(Long id, BaseProduct product) {
        BaseProduct existing = productMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "商品不存在");
        }
        validateProduct(product, id);
        product.setId(id);
        productMapper.updateById(product);
        log.info("product updated: id={}", id);
        return productMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteProduct(Long id) {
        BaseProduct existing = productMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "商品不存在");
        }
        productMapper.deleteById(id);
        log.info("product deleted: id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int increasePickFreq(Long productId, int delta) {
        if (delta <= 0) {
            throw new BusinessException(400, "出库数量必须大于 0");
        }
        BaseProduct existing = productMapper.selectById(productId);
        if (existing == null) {
            throw new BusinessException(404, "商品不存在");
        }
        productMapper.increasePickFreq(productId, delta);
        int after = (existing.getPickFreq() == null ? 0 : existing.getPickFreq()) + delta;
        log.info("product {} pick frequency increased by {} -> {}", productId, delta, after);
        return after;
    }

    // ------------------------------------------------------------------
    // 分类
    // ------------------------------------------------------------------

    @Override
    public List<CategoryTreeNode> categoryTree() {
        List<BaseCategory> all = categoryMapper.selectList(new LambdaQueryWrapper<BaseCategory>()
                .orderByAsc(BaseCategory::getSort).orderByAsc(BaseCategory::getId));

        // 商品数量按分类汇总，避免逐个分类查询数据库
        Map<Long, Integer> countMap = new LinkedHashMap<Long, Integer>();
        for (Map<String, Object> row : categoryMapper.countProductsByCategory()) {
            Object idObj = row.get("categoryId");
            Object cntObj = row.get("productCount");
            if (idObj != null) {
                countMap.put(Long.valueOf(String.valueOf(idObj)),
                        cntObj == null ? 0 : Integer.parseInt(String.valueOf(cntObj)));
            }
        }

        // 先建立全部节点，再按 parentId 挂接
        Map<Long, CategoryTreeNode> nodes = new LinkedHashMap<Long, CategoryTreeNode>();
        for (BaseCategory c : all) {
            int count = countMap.containsKey(c.getId()) ? countMap.get(c.getId()) : 0;
            nodes.put(c.getId(), new CategoryTreeNode(c.getId(), c.getParentId(),
                    c.getCategoryName(), c.getSort(), count));
        }

        List<CategoryTreeNode> roots = new ArrayList<CategoryTreeNode>();
        for (CategoryTreeNode node : nodes.values()) {
            Long pid = node.getParentId();
            if (pid == null || pid == 0L || !nodes.containsKey(pid)) {
                roots.add(node);
            } else {
                nodes.get(pid).getChildren().add(node);
            }
        }
        return roots;
    }

    @Override
    public List<BaseCategory> listCategories() {
        return categoryMapper.selectList(new LambdaQueryWrapper<BaseCategory>()
                .orderByAsc(BaseCategory::getSort).orderByAsc(BaseCategory::getId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseCategory createCategory(BaseCategory category) {
        if (category == null || category.getCategoryName() == null
                || category.getCategoryName().trim().isEmpty()) {
            throw new BusinessException(400, "分类名称不能为空");
        }
        category.setId(null);
        if (category.getParentId() == null) {
            category.setParentId(0L);
        }
        if (category.getSort() == null) {
            category.setSort(0);
        }
        categoryMapper.insert(category);
        log.info("category created: id={}, name={}", category.getId(), category.getCategoryName());
        return categoryMapper.selectById(category.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseCategory updateCategory(Long id, BaseCategory category) {
        BaseCategory existing = categoryMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "分类不存在");
        }
        if (category.getCategoryName() == null || category.getCategoryName().trim().isEmpty()) {
            throw new BusinessException(400, "分类名称不能为空");
        }
        // 禁止把自己挂到自己的子分类下，避免形成环
        if (category.getParentId() != null && category.getParentId().equals(id)) {
            throw new BusinessException(400, "不能将分类的父级设置为自身");
        }
        category.setId(id);
        categoryMapper.updateById(category);
        return categoryMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCategory(Long id) {
        BaseCategory existing = categoryMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(404, "分类不存在");
        }
        Long childCount = categoryMapper.selectCount(new LambdaQueryWrapper<BaseCategory>()
                .eq(BaseCategory::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw new BusinessException(409, "该分类下仍有 " + childCount + " 个子分类，请先删除子分类");
        }
        Long productCount = productMapper.selectCount(new LambdaQueryWrapper<BaseProduct>()
                .eq(BaseProduct::getCategoryId, id));
        if (productCount != null && productCount > 0) {
            throw new BusinessException(409, "该分类下仍有 " + productCount + " 个商品，请先转移或删除商品");
        }
        categoryMapper.deleteById(id);
        log.info("category deleted: id={}", id);
    }

    // ------------------------------------------------------------------
    // ABC 分类
    // ------------------------------------------------------------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AbcClassifyResult classifyAbc() {
        List<Map<String, Object>> rows = productMapper.selectIdAndFreqOrderByFreqDesc();

        AbcClassifyResult result = new AbcClassifyResult();
        result.setTotalProducts(rows.size());

        long totalFreq = 0L;
        for (Map<String, Object> row : rows) {
            totalFreq += toLong(row.get("pickFreq"));
        }
        result.setTotalFrequency(totalFreq);

        Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
        Map<String, Long> freqs = new LinkedHashMap<String, Long>();
        for (String cls : new String[]{"A", "B", "C"}) {
            counts.put(cls, 0);
            freqs.put(cls, 0L);
        }

        if (rows.isEmpty() || totalFreq <= 0) {
            result.setClassCounts(counts);
            result.setClassFrequencies(freqs);
            result.setClassFrequencyRatio(new LinkedHashMap<String, Double>());
            log.info("ABC classification skipped: no product frequency data");
            return result;
        }

        // 按频次降序累加，累计占比跨过阈值即切换分类
        long cumulative = 0L;
        List<Long> idsA = new ArrayList<Long>();
        List<Long> idsB = new ArrayList<Long>();
        List<Long> idsC = new ArrayList<Long>();

        for (Map<String, Object> row : rows) {
            long id = toLong(row.get("id"));
            long freq = toLong(row.get("pickFreq"));
            cumulative += freq;
            double ratio = (double) cumulative / (double) totalFreq;

            String cls;
            if (ratio <= ABC_A_THRESHOLD) {
                cls = "A";
            } else if (ratio <= ABC_B_THRESHOLD) {
                cls = "B";
            } else {
                cls = "C";
            }
            // 前一个商品已跨过 A 的边界后，本商品不应再算作 A：
            // 上面用 <= 判断，因此 equal 情形归入当前类，与算法内核的 searchsorted 语义一致
            if ("A".equals(cls)) {
                idsA.add(id);
            } else if ("B".equals(cls)) {
                idsB.add(id);
            } else {
                idsC.add(id);
            }
            counts.put(cls, counts.get(cls) + 1);
            freqs.put(cls, freqs.get(cls) + freq);
        }

        int updated = 0;
        if (!idsA.isEmpty()) { updated += productMapper.batchUpdateAbcClass("A", idsA); }
        if (!idsB.isEmpty()) { updated += productMapper.batchUpdateAbcClass("B", idsB); }
        if (!idsC.isEmpty()) { updated += productMapper.batchUpdateAbcClass("C", idsC); }
        result.setUpdatedCount(updated);

        Map<String, Double> ratios = new LinkedHashMap<String, Double>();
        for (String cls : new String[]{"A", "B", "C"}) {
            double r = (double) freqs.get(cls) / (double) totalFreq * 100.0;
            ratios.put(cls, BigDecimal.valueOf(r).setScale(2, RoundingMode.HALF_UP).doubleValue());
        }
        result.setClassCounts(counts);
        result.setClassFrequencies(freqs);
        result.setClassFrequencyRatio(ratios);

        log.info("ABC classification done: total={}, freqSum={}, counts={}, updated={}",
                rows.size(), totalFreq, counts, updated);
        return result;
    }

    @Override
    public List<Map<String, Object>> abcSummary() {
        return productMapper.summaryByAbcClass();
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------

    private void validateProduct(BaseProduct p, Long excludeId) {
        if (p == null || p.getProductCode() == null || p.getProductCode().trim().isEmpty()) {
            throw new BusinessException(400, "商品编码不能为空");
        }
        if (p.getProductName() == null || p.getProductName().trim().isEmpty()) {
            throw new BusinessException(400, "商品名称不能为空");
        }
        if (p.getPickFreq() != null && p.getPickFreq() < 0) {
            throw new BusinessException(400, "出库频次不能为负数");
        }
        if (p.getAbcClass() != null && !p.getAbcClass().trim().isEmpty()) {
            String cls = p.getAbcClass().trim().toUpperCase();
            if (!"A".equals(cls) && !"B".equals(cls) && !"C".equals(cls)) {
                throw new BusinessException(400, "ABC 分类只能是 A、B 或 C");
            }
        }
        Long dup = productMapper.selectCount(new LambdaQueryWrapper<BaseProduct>()
                .eq(BaseProduct::getProductCode, p.getProductCode().trim())
                .ne(excludeId != null, BaseProduct::getId, excludeId));
        if (dup != null && dup > 0) {
            throw new BusinessException(409, "商品编码已存在：" + p.getProductCode());
        }
    }

    private static long toLong(Object v) {
        if (v == null) {
            return 0L;
        }
        if (v instanceof Number) {
            return ((Number) v).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
