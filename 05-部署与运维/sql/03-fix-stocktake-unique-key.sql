-- ============================================================================
-- 迁移脚本 003：修正盘点表的唯一约束
-- ============================================================================
--
-- 问题
-- ----
-- 原设计为 `UNIQUE KEY uk_stocktake_no (stocktake_no)`，即盘点单号全局唯一。
-- 但业务语义是「一张盘点单包含多条明细」——每个“货位 + 商品”是一条明细，
-- 它们共享同一个盘点单号。因此插入第二条明细时立即违反唯一约束，
-- 接口报 500（Duplicate entry 'ST...' for key 'uk_stocktake_no'）。
--
-- 验证时若仓库只有 1 条库存记录，该缺陷不会暴露；一旦有多条库存就必然失败。
--
-- 修正
-- ----
-- 唯一约束应表达「同一张盘点单内，同一货位与同一商品只允许一条明细」：
--   UNIQUE KEY uk_stocktake_no_loc_product (stocktake_no, location_id, product_id)
--
-- 同时保留按仓库与状态、按货位的查询索引。
--
-- 幂等性
-- ------
-- 通过 information_schema 判断索引是否存在，可重复执行。
-- ============================================================================

USE `wms`;

-- 1. 删除原有的单列唯一约束
SET @drop_uk := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = 'wms'
              AND TABLE_NAME = 'biz_stocktake'
              AND INDEX_NAME = 'uk_stocktake_no'
        ),
        'ALTER TABLE `biz_stocktake` DROP INDEX `uk_stocktake_no`',
        'SELECT ''uk_stocktake_no 不存在，跳过'' AS msg'
    )
);
PREPARE stmt FROM @drop_uk;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. 建立符合业务语义的复合唯一约束
SET @add_uk := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = 'wms'
              AND TABLE_NAME = 'biz_stocktake'
              AND INDEX_NAME = 'uk_stocktake_no_loc_product'
        ),
        'SELECT ''uk_stocktake_no_loc_product 已存在，跳过'' AS msg',
        'ALTER TABLE `biz_stocktake` ADD UNIQUE KEY `uk_stocktake_no_loc_product` (`stocktake_no`, `location_id`, `product_id`)'
    )
);
PREPARE stmt2 FROM @add_uk;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;

-- 3. 补一个便于按单号查询的普通索引（唯一约束已含 stocktake_no 前缀，此处仅为语义清晰）
SET @add_idx := (
    SELECT IF(
        EXISTS(
            SELECT 1 FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = 'wms'
              AND TABLE_NAME = 'biz_stocktake'
              AND INDEX_NAME = 'idx_stocktake_no'
        ),
        'SELECT ''idx_stocktake_no 已存在，跳过'' AS msg',
        'ALTER TABLE `biz_stocktake` ADD KEY `idx_stocktake_no` (`stocktake_no`)'
    )
);
PREPARE stmt3 FROM @add_idx;
EXECUTE stmt3;
DEALLOCATE PREPARE stmt3;

-- 4. 输出结果供核对
SELECT INDEX_NAME AS 索引名, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS 列, NON_UNIQUE AS 非唯一
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'wms' AND TABLE_NAME = 'biz_stocktake'
GROUP BY INDEX_NAME, NON_UNIQUE
ORDER BY INDEX_NAME;
