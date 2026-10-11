package com.lijunjie.wms.algorithm;

import com.lijunjie.wms.algorithm.strategy.AbcGreedyStrategy;
import com.lijunjie.wms.algorithm.strategy.FrequencyGreedyStrategy;
import com.lijunjie.wms.algorithm.strategy.GeneticSlottingStrategy;
import com.lijunjie.wms.algorithm.strategy.RandomSlottingStrategy;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 货位分配策略工厂。
 *
 * <p>集中管理可用策略，供接口层按编码选择。新增策略只需在此注册。</p>
 */
@Component
public class SlottingStrategyFactory {

    private final Map<String, SlottingStrategy> strategies = new LinkedHashMap<String, SlottingStrategy>();

    public SlottingStrategyFactory() {
        register(new RandomSlottingStrategy());
        register(new FrequencyGreedyStrategy());
        register(new AbcGreedyStrategy());
        register(new GeneticSlottingStrategy());
    }

    private void register(SlottingStrategy strategy) {
        strategies.put(strategy.code(), strategy);
    }

    /**
     * 按编码获取策略。
     *
     * @param code 策略编码：random / frequency-greedy / abc-greedy / genetic
     * @return 策略实现
     * @throws com.lijunjie.wms.common.exception.BusinessException 编码不存在时抛出（400）
     */
    public SlottingStrategy get(String code) {
        SlottingStrategy strategy = code == null ? null : strategies.get(code);
        if (strategy == null) {
            // 参数不合法属于客户端问题：返回 400 而不是 500，并记录为警告级别
            throw new com.lijunjie.wms.common.exception.BusinessException(400,
                    "不支持的货位分配策略：" + code + "，可选值：" + String.join(", ", strategies.keySet()));
        }
        return strategy;
    }

    /** 全部可用策略，用于接口返回给前端下拉框 */
    public Map<String, String> available() {
        Map<String, String> map = new LinkedHashMap<String, String>();
        for (Map.Entry<String, SlottingStrategy> e : strategies.entrySet()) {
            map.put(e.getKey(), e.getValue().label());
        }
        return map;
    }
}
