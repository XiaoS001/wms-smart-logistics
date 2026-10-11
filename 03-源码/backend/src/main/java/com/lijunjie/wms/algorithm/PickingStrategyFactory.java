package com.lijunjie.wms.algorithm;

import com.lijunjie.wms.algorithm.strategy.AntColonyPickingStrategy;
import com.lijunjie.wms.algorithm.strategy.LargestGapPickingStrategy;
import com.lijunjie.wms.algorithm.strategy.NearestNeighborPickingStrategy;
import com.lijunjie.wms.algorithm.strategy.SShapePickingStrategy;
import com.lijunjie.wms.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 拣货路径规划策略工厂。
 */
@Component
public class PickingStrategyFactory {

    private final Map<String, PickingStrategy> strategies = new LinkedHashMap<String, PickingStrategy>();

    public PickingStrategyFactory() {
        register(new SShapePickingStrategy());
        register(new LargestGapPickingStrategy());
        register(new NearestNeighborPickingStrategy());
        register(new AntColonyPickingStrategy());
    }

    private void register(PickingStrategy strategy) {
        strategies.put(strategy.code(), strategy);
    }

    /**
     * 按编码获取策略。
     *
     * @param code 策略编码：s-shape / largest-gap / nearest-neighbor / ant-colony
     * @throws BusinessException 编码不存在时抛出（400）
     */
    public PickingStrategy get(String code) {
        PickingStrategy strategy = code == null ? null : strategies.get(code);
        if (strategy == null) {
            throw new BusinessException(400, "不支持的拣货路径策略：" + code
                    + "，可选值：" + String.join(", ", strategies.keySet()));
        }
        return strategy;
    }

    /** 全部可用策略，用于接口返回给前端下拉框 */
    public Map<String, String> available() {
        Map<String, String> map = new LinkedHashMap<String, String>();
        for (Map.Entry<String, PickingStrategy> e : strategies.entrySet()) {
            map.put(e.getKey(), e.getValue().label());
        }
        return map;
    }
}
