package com.lijunjie.wms.controller;

import com.lijunjie.wms.algorithm.SlottingAssignment;
import com.lijunjie.wms.common.Result;
import com.lijunjie.wms.dto.SlottingRecommendResult;
import com.lijunjie.wms.service.SlottingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 货位分配算法接口。
 *
 * <p>把算法内核暴露为服务：既支持对整仓求解（货位优化模块使用），
 * 也支持为指定商品推荐货位（入库模块使用）。</p>
 */
@Tag(name = "货位分配算法", description = "货位优化的求解与货位推荐")
@RestController
@RequestMapping("/api/slotting")
public class SlottingController {

    private final SlottingService slottingService;

    public SlottingController(SlottingService slottingService) {
        this.slottingService = slottingService;
    }

    /** 可用策略列表 */
    @Operation(summary = "可用策略", description = "返回全部货位分配策略的编码与名称，供前端下拉框使用")
    @GetMapping("/strategies")
    public Result<Map<String, String>> strategies() {
        return Result.success(slottingService.availableStrategies());
    }

    /**
     * 对整仓执行货位分配求解。
     *
     * <p>返回的目标函数值（加权拣货行走距离）可与《算法设计说明书》中的实验数据对照。</p>
     */
    @Operation(summary = "执行货位分配求解",
            description = "以全部商品与全部货位为算例求解，返回分配方案与加权拣货行走距离")
    @PostMapping("/solve")
    public Result<Map<String, Object>> solve(
            @RequestParam("warehouseId") Long warehouseId,
            @RequestParam(value = "strategy", required = false, defaultValue = "frequency-greedy") String strategy,
            @RequestParam(value = "seed", required = false, defaultValue = "20261011") Long seed) {
        SlottingAssignment assignment = slottingService.solve(warehouseId, strategy, seed == null ? 20261011L : seed);

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("strategy", strategy);
        data.put("objective", assignment.getObjective());
        data.put("itemCount", assignment.getSlotOfItem().length);
        data.put("elapsedMillis", assignment.getElapsedMillis());
        data.put("slotOfItem", assignment.getSlotOfItem());
        return Result.success("求解完成", data);
    }

    /**
     * 为入库商品推荐货位。
     *
     * @param productIds 逗号分隔的商品 ID；为空时对全部商品给出推荐
     */
    @Operation(summary = "货位推荐",
            description = "为指定商品推荐目标货位并给出推荐理由；productIds 为空时对全部商品推荐")
    @PostMapping("/recommend/{warehouseId}")
    public Result<SlottingRecommendResult> recommend(
            @PathVariable("warehouseId") Long warehouseId,
            @RequestParam(value = "productIds", required = false) String productIds,
            @RequestParam(value = "strategy", required = false, defaultValue = "frequency-greedy") String strategy,
            @RequestParam(value = "seed", required = false, defaultValue = "20261011") Long seed) {

        List<Long> ids = null;
        if (productIds != null && !productIds.trim().isEmpty()) {
            ids = Arrays.stream(productIds.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Long::valueOf)
                    .collect(Collectors.toList());
        }
        SlottingRecommendResult result = slottingService.recommend(warehouseId, ids, strategy,
                seed == null ? 20261011L : seed);
        return Result.success("货位推荐完成", result);
    }
}
