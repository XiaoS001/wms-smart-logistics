package com.lijunjie.wms.controller;

import com.lijunjie.wms.common.Result;
import com.lijunjie.wms.entity.BaseLocation;
import com.lijunjie.wms.service.WarehouseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 库位（货位）查询接口。
 *
 * <p>货位坐标与到出入口距离是货位分配算法的输入，因此本模块的查询都以距离升序返回，
 * 便于直接观察“近端货位优先”的效果。</p>
 */
@Tag(name = "库位管理", description = "货位查询与近端货位检索")
@RestController
@RequestMapping("/api/locations")
public class LocationController {

    private final WarehouseService warehouseService;

    public LocationController(WarehouseService warehouseService) {
        this.warehouseService = warehouseService;
    }

    /** 货位列表：可按仓库、库区、状态过滤，按到出入口距离升序 */
    @Operation(summary = "货位列表", description = "按到出入口距离升序返回货位，支持按库区与状态过滤")
    @GetMapping
    public Result<List<BaseLocation>> list(
            @RequestParam(value = "warehouseId", required = false) Long warehouseId,
            @RequestParam(value = "zoneId", required = false) Long zoneId,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "limit", required = false, defaultValue = "200") Integer limit) {
        return Result.success(warehouseService.listLocations(warehouseId, zoneId, status, limit));
    }

    /**
     * 最近的空闲货位。
     *
     * <p>对应货位分配算法的核心查询：按距离升序取前 N 个空闲货位，
     * 由 {@code idx_distance} 索引直接支撑，无需全表排序。</p>
     */
    @Operation(summary = "最近空闲货位",
            description = "按到出入口距离升序返回前 N 个空闲货位，是货位分配算法的典型查询")
    @GetMapping("/nearest-free")
    public Result<List<BaseLocation>> nearestFree(
            @RequestParam("warehouseId") Long warehouseId,
            @RequestParam(value = "limit", required = false, defaultValue = "20") Integer limit) {
        return Result.success(warehouseService.nearestFreeLocations(warehouseId, limit));
    }
}
