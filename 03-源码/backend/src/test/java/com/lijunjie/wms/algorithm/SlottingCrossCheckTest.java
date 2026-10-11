package com.lijunjie.wms.algorithm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lijunjie.wms.algorithm.strategy.AbcGreedyStrategy;
import com.lijunjie.wms.algorithm.strategy.FrequencyGreedyStrategy;
import com.lijunjie.wms.algorithm.strategy.GeneticSlottingStrategy;
import com.lijunjie.wms.algorithm.strategy.RandomSlottingStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 算法内核交叉验证：Java 实现与 Python 原型的结果必须一致。
 *
 * <p>验证思路：Python 算法内核先用同一算例生成“黄金数据”
 * （{@code 04-测试与实验/04-实验数据/golden_slotting.json}），
 * 本测试读取该文件并在 Java 侧重算，逐项比对目标函数值。</p>
 *
 * <p>比对口径：</p>
 * <ul>
 *   <li><b>确定性策略</b>（频次贪心、ABC 分区贪心）：目标函数值必须<b>完全相等</b>；</li>
 *   <li><b>遗传算法</b>：随机过程与 Python 不同，因此校验其目标函数值
 *       不劣于贪心解（贪心解在该目标下即为最优，GA 至少应收敛到同一水平）；</li>
 *   <li><b>随机分配</b>：仅校验量级合理（随机策略与 Python 的随机序列不同）。</li>
 * </ul>
 */
class SlottingCrossCheckTest {

    /** Python 侧生成的黄金数据 */
    private static final String GOLDEN_FILE =
            "D:/DeepSeekXM/XM01/04-测试与实验/04-实验数据/golden_slotting.json";

    /** 目标函数值的浮点比较容差 */
    private static final double EPSILON = 1e-6;

    @Test
    @DisplayName("Java 算法内核与 Python 原型结果一致")
    void crossCheckWithPythonGoldenData() throws Exception {
        File golden = new File(GOLDEN_FILE);
        assumeTrue(golden.exists(), "黄金数据不存在，跳过交叉验证：" + GOLDEN_FILE);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(golden);

        // ---- 还原算例 ----
        JsonNode layoutNode = root.get("layout");
        WarehouseLayout layout = new WarehouseLayout(
                layoutNode.get("aisles").asInt(),
                layoutNode.get("rows").asInt(),
                layoutNode.get("aisleGap").asDouble(),
                layoutNode.get("rowGap").asDouble());
        assertEquals(layoutNode.get("slotCount").asInt(), layout.slotCount(),
                "货位数应与 Python 一致");

        JsonNode itemsNode = root.get("items");
        int itemCount = itemsNode.get("count").asInt();
        double[] freqs = new double[itemCount];
        JsonNode freqArray = itemsNode.get("freqs");
        for (int i = 0; i < itemCount; i++) {
            freqs[i] = freqArray.get(i).asDouble();
        }
        int[] abcFromPython = new int[itemCount];
        JsonNode abcArray = itemsNode.get("abcClass");
        for (int i = 0; i < itemCount; i++) {
            abcFromPython[i] = abcArray.get(i).asInt();
        }

        SlottingInstance instance = new SlottingInstance(layout, freqs, abcFromPython);
        assertEquals(itemCount, instance.itemCount());

        // ---- 距离模型一致性 ----
        JsonNode strategies = root.get("strategies");

        // 1. 频次贪心：确定性，必须完全一致
        double pythonGreedy = strategies.get("frequencyGreedy").get("objective").asDouble();
        SlottingAssignment javaGreedy = new FrequencyGreedyStrategy().solve(instance, 20261011L);
        assertEquals(pythonGreedy, javaGreedy.getObjective(), EPSILON,
                "频次贪心的目标函数值必须与 Python 完全一致");

        // 2. ABC 分区贪心：确定性，必须完全一致
        double pythonAbc = strategies.get("abcGreedy").get("objective").asDouble();
        SlottingAssignment javaAbc = new AbcGreedyStrategy().solve(instance, 20261011L);
        assertEquals(pythonAbc, javaAbc.getObjective(), EPSILON,
                "ABC 分区贪心的目标函数值必须与 Python 完全一致");

        // 3. 遗传算法：不劣于贪心解（贪心在该目标下即为最优解）
        SlottingAssignment javaGa = new GeneticSlottingStrategy().solve(instance, 20261011L);
        assertTrue(javaGa.getObjective() <= javaGreedy.getObjective() + EPSILON,
                "遗传算法结果不应劣于频次贪心：ga=" + javaGa.getObjective()
                        + ", greedy=" + javaGreedy.getObjective());

        // 4. 随机分配：结果应明显劣于贪心（量级校验），且分配合法
        SlottingAssignment javaRandom = new RandomSlottingStrategy().solve(instance, 20261011L);
        assertTrue(javaRandom.getObjective() > javaGreedy.getObjective() * 1.5,
                "随机分配应明显劣于贪心：random=" + javaRandom.getObjective()
                        + ", greedy=" + javaGreedy.getObjective());

        // ---- 分配合法性：每个商品一个货位、互不重复、且在合法范围内 ----
        assertLegalAssignment(javaRandom, layout);
        assertLegalAssignment(javaGreedy, layout);
        assertLegalAssignment(javaAbc, layout);
        assertLegalAssignment(javaGa, layout);

        // ---- ABC 分类一致性：Java 推导结果应与 Python 一致 ----
        int[] javaAbc2 = SlottingInstance.deriveAbcClass(freqs);
        int diff = 0;
        for (int i = 0; i < itemCount; i++) {
            if (javaAbc2[i] != abcFromPython[i]) {
                diff++;
            }
        }
        assertEquals(0, diff, "ABC 分类推导结果应与 Python 一致，不一致的个数=" + diff);

        // ---- 输出对照表，便于人工核对 ----
        System.out.println("=== Java 与 Python 目标函数值对照 ===");
        System.out.printf("  %-20s python=%.2f  java=%.2f%n", "frequency-greedy", pythonGreedy, javaGreedy.getObjective());
        System.out.printf("  %-20s python=%.2f  java=%.2f%n", "abc-greedy", pythonAbc, javaAbc.getObjective());
        System.out.printf("  %-20s python=%.2f  java=%.2f (仅要求不劣于贪心)%n", "genetic",
                strategies.get("genetic").get("objective").asDouble(), javaGa.getObjective());
        System.out.printf("  %-20s python=%.2f  java=%.2f (随机序列不同，仅校验量级)%n", "random",
                strategies.get("random").get("objective").asDouble(), javaRandom.getObjective());
        System.out.printf("  平均加权行走距离降幅(贪心 vs 随机) = %.2f%%%n",
                (javaRandom.getObjective() - javaGreedy.getObjective()) / javaRandom.getObjective() * 100);
    }

    /** 校验分配方案合法：长度正确、取值在范围内、货位不重复 */
    private void assertLegalAssignment(SlottingAssignment assignment, WarehouseLayout layout) {
        int[] slots = assignment.getSlotOfItem();
        assertNotNull(slots);
        List<Integer> seen = new ArrayList<Integer>();
        for (int slot : slots) {
            assertTrue(slot >= 0 && slot < layout.slotCount(),
                    "货位编号越界：" + slot + "（合法范围 0 ~ " + (layout.slotCount() - 1) + "）");
            assertTrue(!seen.contains(slot), "货位重复分配：" + slot);
            seen.add(slot);
        }
    }
}
