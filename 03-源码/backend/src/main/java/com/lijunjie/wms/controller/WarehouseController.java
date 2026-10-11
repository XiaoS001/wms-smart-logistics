package com.lijunjie.wms.controller;

import com.lijunjie.wms.common.Result;
import com.lijunjie.wms.dto.GenerateLocationsRequest;
import com.lijunjie.wms.dto.GenerateLocationsResult;
import com.lijunjie.wms.entity.BaseWarehouse;
import com.lijunjie.wms.service.WarehouseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 仓库管理接口。
 */
@Tag(name = "仓库管理", description = "仓库的增删改查与货位批量生成")
@RestController
@RequestMapping("/api/warehouses")
public class WarehouseController {

    private final WarehouseService warehouseService;

    public WarehouseController(WarehouseService warehouseService) {
        this.warehouseService = warehouseService;
    }

    /** 仓库列表，可按编码或名称模糊检索 */
    @Operation(summary = "仓库列表", description = "返回全部未删除仓库，支持关键字模糊检索")
    @GetMapping
    public Result<List<BaseWarehouse>> list(@RequestParam(value = "keyword", required = false) String keyword) {
        return Result.success(warehouseService.listWarehouses(keyword));
    }

    /** 仓库详情 */
    @Operation(summary = "仓库详情", description = "按 ID 查询仓库，含布局参数")
    @GetMapping("/{id}")
    public Result<BaseWarehouse> detail(@PathVariable("id") Long id) {
        return Result.success(warehouseService.getWarehouse(id));
    }

    /** 新增仓库 */
    @Operation(summary = "新增仓库", description = "登记仓库编码、名称与布局参数")
    @PostMapping
    public Result<BaseWarehouse> create(@RequestBody BaseWarehouse warehouse) {
        return Result.success("仓库创建成功", warehouseService.createWarehouse(warehouse));
    }

    /** 修改仓库 */
    @Operation(summary = "修改仓库", description = "修改仓库信息；布局参数变更时不会自动重算已有货位距离")
    @PutMapping("/{id}")
    public Result<BaseWarehouse> update(@PathVariable("id") Long id,
                                        @RequestBody BaseWarehouse warehouse) {
        return Result.success("仓库修改成功", warehouseService.updateWarehouse(id, warehouse));
    }

    /** 删除仓库（存在货位时拒绝） */
    @Operation(summary = "删除仓库", description = "逻辑删除；存在关联货位时返回 409")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        warehouseService.deleteWarehouse(id);
        return Result.success("仓库已删除", null);
    }

    /**
     * 批量生成货位。
     *
     * <p>这是本模块最核心的接口：按仓库布局生成规则货位，并计算每个货位到出入口的距离，
     * 同时按距离阈值划分 A/B/C 库区。生成结果即货位分配算法的输入数据。</p>
     */
    @Operation(summary = "批量生成货位",
            description = "按巷道数×纵深数生成货位，自动计算到出入口距离并划分 A/B/C 库区；"
                    + "overwrite=true 时先清空该仓库已有货位")
    @PostMapping("/{id}/locations/generate")
    public Result<GenerateLocationsResult> generateLocations(
            @PathVariable("id") Long id,
            @RequestBody(required = false) GenerateLocationsRequest request) {
        GenerateLocationsResult result = warehouseService.generateLocations(id, request);
        return Result.success("货位生成完成", result);
    }

    /** 库区货位分布概览 */
    @Operation(summary = "库区货位分布", description = "统计各库区的货位数量与距离范围")
    @GetMapping("/{id}/locations/summary")
    public Result<List<Map<String, Object>>> locationSummary(@PathVariable("id") Long id) {
        return Result.success(warehouseService.locationSummary(id));
    }
}
