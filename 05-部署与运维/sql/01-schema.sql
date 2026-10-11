-- ============================================================
--  WMS 建表脚本（共 17 张表）
--  课题：面向智慧物流的仓储管理系统设计与实现
--  生成时间：2026-10-11 15:42:34
--  说明：由《数据库设计说明书》的表定义生成，二者保持一致
--  目标库：MySQL 8.0，字符集 utf8mb4，引擎 InnoDB
-- ============================================================

CREATE DATABASE IF NOT EXISTS wms DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE wms;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ------------------------------------------------------------
-- sys_user　用户表（用户与权限）
-- 系统用户，支持管理员/仓管员/拣货员三种角色
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username` VARCHAR(50) NOT NULL COMMENT '登录名',
  `password` VARCHAR(100) NOT NULL COMMENT '密码（BCrypt 加密存储）',
  `real_name` VARCHAR(50) NOT NULL COMMENT '真实姓名',
  `phone` VARCHAR(20) NULL COMMENT '手机号',
  `email` VARCHAR(100) NULL COMMENT '邮箱',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1 启用 / 0 停用',
  `last_login_time` DATETIME NULL COMMENT '最后登录时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (username) COMMENT '登录名唯一',
  KEY `idx_status` (status) COMMENT '按状态筛选用户'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统用户，支持管理员/仓管员/拣货员三种角色';

-- ------------------------------------------------------------
-- sys_role　角色表（用户与权限）
-- 角色定义，用于权限分配
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `role_code` VARCHAR(50) NOT NULL COMMENT '角色编码，如 ADMIN / KEEPER / PICKER',
  `role_name` VARCHAR(50) NOT NULL COMMENT '角色名称',
  `description` VARCHAR(200) NULL COMMENT '角色说明',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (role_code) COMMENT '角色编码唯一'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色定义，用于权限分配';

-- ------------------------------------------------------------
-- sys_permission　权限表（用户与权限）
-- 菜单与操作权限点，支持树形结构
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `sys_permission`;
CREATE TABLE `sys_permission` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `parent_id` BIGINT NOT NULL DEFAULT 0 COMMENT '父权限 ID，0 为顶级',
  `perm_code` VARCHAR(80) NOT NULL COMMENT '权限编码，如 product:add',
  `perm_name` VARCHAR(80) NOT NULL COMMENT '权限名称',
  `perm_type` TINYINT NOT NULL DEFAULT 1 COMMENT '类型：1 菜单 / 2 按钮',
  `path` VARCHAR(200) NULL COMMENT '前端路由路径',
  `sort` INT NOT NULL DEFAULT 0 COMMENT '排序号',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_perm_code` (perm_code) COMMENT '权限编码唯一',
  KEY `idx_parent` (parent_id) COMMENT '按父节点查询子权限'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='菜单与操作权限点，支持树形结构';

-- ------------------------------------------------------------
-- sys_user_role　用户角色关联表（用户与权限）
-- 用户与角色的多对多关联
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` BIGINT NOT NULL COMMENT '用户 ID',
  `role_id` BIGINT NOT NULL COMMENT '角色 ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (user_id, role_id) COMMENT '防止重复授权',
  KEY `idx_user` (user_id) COMMENT '按用户查角色'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户与角色的多对多关联';

-- ------------------------------------------------------------
-- sys_role_permission　角色权限关联表（用户与权限）
-- 角色与权限的多对多关联
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `sys_role_permission`;
CREATE TABLE `sys_role_permission` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `role_id` BIGINT NOT NULL COMMENT '角色 ID',
  `perm_id` BIGINT NOT NULL COMMENT '权限 ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_perm` (role_id, perm_id) COMMENT '防止重复授权',
  KEY `idx_role` (role_id) COMMENT '按角色查权限'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色与权限的多对多关联';

-- ------------------------------------------------------------
-- base_warehouse　仓库表（仓库与库位）
-- 仓库信息；布局参数用于算法计算行走距离
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `base_warehouse`;
CREATE TABLE `base_warehouse` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `warehouse_code` VARCHAR(50) NOT NULL COMMENT '仓库编码',
  `warehouse_name` VARCHAR(100) NOT NULL COMMENT '仓库名称',
  `aisle_count` INT NOT NULL COMMENT '巷道数 X（算法输入）',
  `row_count` INT NOT NULL COMMENT '每条巷道纵深货位数 Y（算法输入）',
  `aisle_gap` DECIMAL(6,2) NOT NULL DEFAULT 2.50 COMMENT '相邻巷道间距（米）',
  `row_gap` DECIMAL(6,2) NOT NULL DEFAULT 1.20 COMMENT '相邻货位纵深间距（米）',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1 启用 / 0 停用',
  `remark` VARCHAR(200) NULL COMMENT '备注',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wh_code` (warehouse_code) COMMENT '仓库编码唯一'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='仓库信息；布局参数用于算法计算行走距离';

-- ------------------------------------------------------------
-- base_zone　库区表（仓库与库位）
-- 库区划分，支持按 ABC 分类分区存储
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `base_zone`;
CREATE TABLE `base_zone` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `warehouse_id` BIGINT NOT NULL COMMENT '所属仓库 ID',
  `zone_code` VARCHAR(50) NOT NULL COMMENT '库区编码',
  `zone_name` VARCHAR(100) NOT NULL COMMENT '库区名称',
  `zone_type` VARCHAR(20) NOT NULL DEFAULT 'NORMAL' COMMENT '库区类型：A/B/C 分区或 NORMAL',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_zone` (warehouse_id, zone_code) COMMENT '同一仓库内库区编码唯一',
  KEY `idx_wh` (warehouse_id) COMMENT '按仓库查库区'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='库区划分，支持按 ABC 分类分区存储';

-- ------------------------------------------------------------
-- base_location　货位表（仓库与库位）
-- 货位坐标是算法计算距离的核心数据
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `base_location`;
CREATE TABLE `base_location` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `warehouse_id` BIGINT NOT NULL COMMENT '所属仓库 ID',
  `zone_id` BIGINT NULL COMMENT '所属库区 ID',
  `location_code` VARCHAR(50) NOT NULL COMMENT '货位编码，如 A-01-05',
  `aisle_no` INT NOT NULL COMMENT '巷道号 x（1 起）',
  `row_no` INT NOT NULL COMMENT '纵深号 y（1 起）',
  `distance` DECIMAL(8,2) NOT NULL DEFAULT 0 COMMENT '到出入口的行走距离（冗余字段，便于排序）',
  `capacity` DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '容量',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1 空闲 / 2 占用 / 0 停用',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_loc_code` (warehouse_id, location_code) COMMENT '货位编码唯一',
  UNIQUE KEY `uk_loc_pos` (warehouse_id, aisle_no, row_no) COMMENT '坐标唯一，保证算法输入无歧义',
  KEY `idx_distance` (warehouse_id, distance) COMMENT '按距离排序取近端货位（货位分配高频查询）',
  KEY `idx_status` (status) COMMENT '按状态筛选空闲货位'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='货位坐标是算法计算距离的核心数据';

-- ------------------------------------------------------------
-- base_category　商品分类表（商品管理）
-- 商品分类，支持树形
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `base_category`;
CREATE TABLE `base_category` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `parent_id` BIGINT NOT NULL DEFAULT 0 COMMENT '父分类 ID',
  `category_name` VARCHAR(80) NOT NULL COMMENT '分类名称',
  `sort` INT NOT NULL DEFAULT 0 COMMENT '排序号',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_parent` (parent_id) COMMENT '按父分类查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='商品分类，支持树形';

-- ------------------------------------------------------------
-- base_product　商品表（商品管理）
-- 商品基础信息；pick_freq 是货位分配算法的核心输入
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `base_product`;
CREATE TABLE `base_product` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `product_code` VARCHAR(50) NOT NULL COMMENT '商品编码（SKU）',
  `product_name` VARCHAR(150) NOT NULL COMMENT '商品名称',
  `category_id` BIGINT NULL COMMENT '分类 ID',
  `spec` VARCHAR(100) NULL COMMENT '规格',
  `unit` VARCHAR(20) NULL COMMENT '计量单位',
  `weight` DECIMAL(10,3) NULL COMMENT '重量（kg）',
  `volume` DECIMAL(10,4) NULL COMMENT '体积（m³）',
  `pick_freq` INT NOT NULL DEFAULT 0 COMMENT '累计出库频次（算法输入，出库时累加）',
  `abc_class` CHAR(1) NOT NULL DEFAULT 'C' COMMENT 'ABC 分类：A / B / C',
  `safety_stock` DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '安全库存',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_code` (product_code) COMMENT '商品编码唯一',
  KEY `idx_pick_freq` (pick_freq) COMMENT '按出库频次排序（ABC 分类与贪心分配高频查询）',
  KEY `idx_category` (category_id) COMMENT '按分类查询商品',
  KEY `idx_abc` (abc_class) COMMENT '按 ABC 分类筛选'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='商品基础信息；pick_freq 是货位分配算法的核心输入';

-- ------------------------------------------------------------
-- inv_stock　库存表（库存管理）
-- 商品在货位上的实时库存
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `inv_stock`;
CREATE TABLE `inv_stock` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `warehouse_id` BIGINT NOT NULL COMMENT '仓库 ID',
  `location_id` BIGINT NOT NULL COMMENT '货位 ID',
  `product_id` BIGINT NOT NULL COMMENT '商品 ID',
  `quantity` DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '库存数量',
  `locked_quantity` DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '锁定数量（已分配未出库）',
  `batch_no` VARCHAR(50) NULL COMMENT '批次号',
  `production_date` DATE NULL COMMENT '生产日期',
  `expiry_date` DATE NULL COMMENT '有效期至',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_loc_product_batch` (location_id, product_id, batch_no) COMMENT '同一货位同一商品同一批次唯一',
  KEY `idx_product` (product_id) COMMENT '按商品查库存',
  KEY `idx_warehouse` (warehouse_id) COMMENT '按仓库查库存',
  KEY `idx_expiry` (expiry_date) COMMENT '临期预警查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='商品在货位上的实时库存';

-- ------------------------------------------------------------
-- biz_inbound_order　入库单主表（入库管理）
-- 入库单，含智能推荐货位标记
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `biz_inbound_order`;
CREATE TABLE `biz_inbound_order` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_no` VARCHAR(50) NOT NULL COMMENT '入库单号',
  `warehouse_id` BIGINT NOT NULL COMMENT '仓库 ID',
  `supplier` VARCHAR(150) NULL COMMENT '供应商',
  `total_quantity` DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '总数量',
  `total_amount` DECIMAL(14,2) NOT NULL DEFAULT 0 COMMENT '总金额',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0 待入库 / 1 部分入库 / 2 已完成 / 9 已取消',
  `use_slotting_suggest` TINYINT NOT NULL DEFAULT 0 COMMENT '是否使用货位分配算法推荐：1 是',
  `operator_id` BIGINT NULL COMMENT '操作人 ID',
  `inbound_time` DATETIME NULL COMMENT '实际入库时间',
  `remark` VARCHAR(255) NULL COMMENT '备注',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (order_no) COMMENT '单号唯一',
  KEY `idx_status_time` (status, create_time) COMMENT '按状态与时间查询单据列表',
  KEY `idx_warehouse` (warehouse_id) COMMENT '按仓库查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='入库单，含智能推荐货位标记';

-- ------------------------------------------------------------
-- biz_inbound_item　入库单明细表（入库管理）
-- 入库单的明细行
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `biz_inbound_item`;
CREATE TABLE `biz_inbound_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_id` BIGINT NOT NULL COMMENT '入库单 ID',
  `product_id` BIGINT NOT NULL COMMENT '商品 ID',
  `location_id` BIGINT NULL COMMENT '目标货位 ID（可由算法推荐）',
  `suggest_reason` VARCHAR(200) NULL COMMENT '推荐该货位的理由（如“A 类商品，近端货位”）',
  `plan_quantity` DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '计划数量',
  `actual_quantity` DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '实收数量',
  `unit_price` DECIMAL(12,2) NULL COMMENT '单价',
  `batch_no` VARCHAR(50) NULL COMMENT '批次号',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_order` (order_id) COMMENT '按单据查明细',
  KEY `idx_product` (product_id) COMMENT '按商品查明细',
  KEY `idx_location` (location_id) COMMENT '按货位查明细'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='入库单的明细行';

-- ------------------------------------------------------------
-- biz_outbound_order　出库单主表（出库管理）
-- 出库单，含拣货路径规划结果
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `biz_outbound_order`;
CREATE TABLE `biz_outbound_order` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_no` VARCHAR(50) NOT NULL COMMENT '出库单号',
  `warehouse_id` BIGINT NOT NULL COMMENT '仓库 ID',
  `customer` VARCHAR(150) NULL COMMENT '客户',
  `total_quantity` DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '总数量',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0 待拣货 / 1 拣货中 / 2 已出库 / 9 已取消',
  `route_distance` DECIMAL(10,2) NULL COMMENT '算法规划的拣货路径总距离（米）',
  `route_detail` TEXT NULL COMMENT '算法规划的拣货顺序（JSON，含货位序列）',
  `route_algo` VARCHAR(30) NULL COMMENT '使用的路径算法：S_SHAPE/LARGEST_GAP/NEAREST/ACO',
  `operator_id` BIGINT NULL COMMENT '操作人 ID',
  `outbound_time` DATETIME NULL COMMENT '实际出库时间',
  `remark` VARCHAR(255) NULL COMMENT '备注',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (order_no) COMMENT '单号唯一',
  KEY `idx_status_time` (status, create_time) COMMENT '按状态与时间查询单据列表',
  KEY `idx_warehouse` (warehouse_id) COMMENT '按仓库查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='出库单，含拣货路径规划结果';

-- ------------------------------------------------------------
-- biz_outbound_item　出库单明细表（出库管理）
-- 出库单明细，记录拣货顺序号
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `biz_outbound_item`;
CREATE TABLE `biz_outbound_item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_id` BIGINT NOT NULL COMMENT '出库单 ID',
  `product_id` BIGINT NOT NULL COMMENT '商品 ID',
  `location_id` BIGINT NOT NULL COMMENT '拣货货位 ID',
  `quantity` DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '出库数量',
  `pick_seq` INT NULL COMMENT '算法给出的拣货顺序号',
  `picked` TINYINT NOT NULL DEFAULT 0 COMMENT '是否已拣：0 否 / 1 是',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_order` (order_id) COMMENT '按单据查明细',
  KEY `idx_product` (product_id) COMMENT '按商品查明细',
  KEY `idx_location` (location_id) COMMENT '按货位查明细'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='出库单明细，记录拣货顺序号';

-- ------------------------------------------------------------
-- biz_stocktake　盘点表（库存管理）
-- 库存盘点记录与差异
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `biz_stocktake`;
CREATE TABLE `biz_stocktake` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `stocktake_no` VARCHAR(50) NOT NULL COMMENT '盘点单号',
  `warehouse_id` BIGINT NOT NULL COMMENT '仓库 ID',
  `location_id` BIGINT NOT NULL COMMENT '货位 ID',
  `product_id` BIGINT NOT NULL COMMENT '商品 ID',
  `book_quantity` DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '账面数量',
  `actual_quantity` DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '实盘数量',
  `diff_quantity` DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '差异数量',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0 待盘点 / 1 已盘点 / 2 已调整',
  `operator_id` BIGINT NULL COMMENT '盘点人 ID',
  `stocktake_time` DATETIME NULL COMMENT '盘点时间',
  `remark` VARCHAR(255) NULL COMMENT '备注',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_stocktake_no_loc_product` (`stocktake_no`, `location_id`, `product_id`) COMMENT '同一盘点单内货位+商品唯一',
  KEY `idx_stocktake_no` (stocktake_no) COMMENT '按盘点单号查询',
  KEY `idx_warehouse_status` (warehouse_id, status) COMMENT '按仓库与状态查询',
  KEY `idx_location` (location_id) COMMENT '按货位查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='库存盘点记录与差异';

-- ------------------------------------------------------------
-- sys_operation_log　操作日志表（系统管理）
-- 记录关键业务操作，便于追溯与审计
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `sys_operation_log`;
CREATE TABLE `sys_operation_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` BIGINT NULL COMMENT '操作人 ID',
  `username` VARCHAR(50) NULL COMMENT '操作人登录名（冗余）',
  `module` VARCHAR(50) NULL COMMENT '业务模块',
  `operation` VARCHAR(100) NULL COMMENT '操作描述',
  `method` VARCHAR(10) NULL COMMENT '请求方法',
  `request_uri` VARCHAR(255) NULL COMMENT '请求地址',
  `ip` VARCHAR(50) NULL COMMENT '客户端 IP',
  `duration_ms` INT NULL COMMENT '耗时（毫秒）',
  `success` TINYINT NOT NULL DEFAULT 1 COMMENT '是否成功',
  `error_msg` VARCHAR(500) NULL COMMENT '错误信息',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (user_id, create_time) COMMENT '按用户与时间检索日志',
  KEY `idx_module` (module) COMMENT '按模块筛选'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='记录关键业务操作，便于追溯与审计';

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================================
-- 18. 货位优化方案与效果记录（biz_slotting_plan）
-- ============================================================================
-- 记录每次货位优化的策略、参数、优化前后的加权行走距离与是否已应用。
-- 目标函数：F = Σ(商品出库频次 × 该商品所在货位到出入口距离)，
-- 与《算法设计说明书》的实验口径一致，便于论文直接引用。
-- ============================================================================
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

-- 建表完成，共 18 张表