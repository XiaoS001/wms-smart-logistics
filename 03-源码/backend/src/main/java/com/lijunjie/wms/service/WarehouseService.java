package com.lijunjie.wms.service;

import com.lijunjie.wms.dto.GenerateLocationsRequest;
import com.lijunjie.wms.dto.GenerateLocationsResult;
import com.lijunjie.wms.entity.BaseLocation;
import com.lijunjie.wms.entity.BaseWarehouse;

import java.util.List;

/**
 * 仓库与库位业务接口。
 */
public interface WarehouseService {

    /** 查询全部未删除仓库 */
    List<BaseWarehouse> listWarehouses(String keyword);

    /** 按 ID 查询仓库 */
    BaseWarehouse getWarehouse(Long id);

    /** 新增仓库（校验编码唯一） */
    BaseWarehouse createWarehouse(BaseWarehouse warehouse);

    /** 修改仓库 */
    BaseWarehouse updateWarehouse(Long id, BaseWarehouse warehouse);

    /** 删除仓库（存在货位时禁止删除） */
    void deleteWarehouse(Long id);

    /**
     * 按仓库布局批量生成货位。
     *
     * @param warehouseId 仓库 ID
     * @param request     生成参数（为空时使用仓库自身的布局参数与默认阈值）
     * @return 生成结果统计
     */
    GenerateLocationsResult generateLocations(Long warehouseId, GenerateLocationsRequest request);

    /** 分页查询货位，可按库区与状态过滤，按距离升序 */
    List<BaseLocation> listLocations(Long warehouseId, Long zoneId, Integer status, int limit);

    /** 查询某仓库下各库区的货位分布概览 */
    List<java.util.Map<String, Object>> locationSummary(Long warehouseId);

    /** 取距离出入口最近的若干个空闲货位（货位分配算法的典型查询） */
    List<BaseLocation> nearestFreeLocations(Long warehouseId, int limit);
}
