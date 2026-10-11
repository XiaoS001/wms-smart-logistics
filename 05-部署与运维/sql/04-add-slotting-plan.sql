-- ============================================================================
-- 迁移脚本 004：新增货位优化历史表
-- ============================================================================
--
-- 背景
-- ----
-- 货位优化模块需要在执行优化时留存记录：用了什么策略、参数是什么、
-- 优化前后的加权行走距离、降幅、是否已应用，以及生成时间。
-- 原设计（17 张表）未包含该表，此处补充，表总数变为 18 张。
--
-- 设计要点
-- --------
-- 1. 目标函数：加权拣货行走距离 F = Σ(商品出库频次 × 该商品所在货位到出入口距离)；
--    字段 current_objective / optimized_objective 分别记录优化前后的 F 值，
--    与《算法设计说明书》的实验口径一致，便于论文直接引用。
-- 2. applied 标记方案是否已写回库存；只有未应用的方案可以重复应用或删除。
-- 3. plan_detail 以 JSON 文本保存商品到货位的分配明细，供前端回放与审计。
--
-- 幂等性
-- ------
-- 使用 CREATE TABLE IF NOT EXISTS，可重复执行。
-- ============================================================================

USE `wms`;

CREATE TABLE IF NOT EXISTS `biz_slotting_plan` (
  `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `plan_no`             VARCHAR(50)  NOT NULL                COMMENT '优化方案编号',
  `warehouse_id`        BIGINT       NOT NULL                COMMENT '仓库 ID',
  `strategy`            VARCHAR(30)  NOT NULL                COMMENT '优化策略编码',
  `strategy_label`      VARCHAR(50)           DEFAULT NULL   COMMENT '策略显示名称',
  `seed`                BIGINT                DEFAULT NULL   COMMENT '随机种子，保证结果可复现',
  `product_count`       INT          NOT NULL DEFAULT 0      COMMENT '参与优化的商品数',
  `slot_count`          INT          NOT NULL DEFAULT 0      COMMENT '参与优化的货位数',
  `current_objective`   DECIMAL(16,2)         DEFAULT NULL   COMMENT '优化前加权行走距离（米·次）',
  `optimized_objective` DECIMAL(16,2)         DEFAULT NULL   COMMENT '优化后加权行走距离（米·次）',
  `improvement_percent` DECIMAL(8,2)          DEFAULT NULL   COMMENT '降幅百分比',
  `elapsed_millis`      BIGINT                DEFAULT NULL   COMMENT '算法耗时（毫秒）',
  `applied`             TINYINT      NOT NULL DEFAULT 0      COMMENT '是否已应用：1 是 / 0 否',
  `applied_time`        DATETIME              DEFAULT NULL   COMMENT '应用时间',
  `moved_records`       INT                   DEFAULT NULL   COMMENT '应用时搬动的库存记录数',
  `plan_detail`         TEXT                                 COMMENT '分配明细（JSON）',
  `operator_id`         BIGINT                DEFAULT NULL   COMMENT '操作人 ID',
  `remark`              VARCHAR(255)          DEFAULT NULL   COMMENT '备注',
  `create_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plan_no` (`plan_no`)                       COMMENT '方案编号唯一',
  KEY `idx_warehouse_time` (`warehouse_id`, `create_time`)  COMMENT '按仓库与时间查询',
  KEY `idx_applied` (`applied`)                             COMMENT '按是否已应用筛选'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='货位优化方案与效果记录';

-- 输出结果供核对
SELECT COUNT(*) AS table_exists FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'wms' AND TABLE_NAME = 'biz_slotting_plan';
