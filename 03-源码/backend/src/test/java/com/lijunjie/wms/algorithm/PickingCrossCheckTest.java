package com.lijunjie.wms.algorithm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lijunjie.wms.algorithm.strategy.AntColonyPickingStrategy;
import com.lijunjie.wms.algorithm.strategy.LargestGapPickingStrategy;
import com.lijunjie.wms.algorithm.strategy.NearestNeighborPickingStrategy;
import com.lijunjie.wms.algorithm.strategy.SShapePickingStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 拣货路径规划交叉验证：Java 实现与 Python 原型的结果必须一致。
 *
 * <p>黄金数据由 Python 内核生成（{@code golden_picking.json}），包含三组不同规模的拣货点集合、
 * 三种确定性策略的行走距离与 Held-Karp 穷举最优解。</p>
 *
 * <p>比对口径：</p>
 * <ul>
 *   <li><b>S 型、最大间隙、最近邻</b>：行走距离必须与 Python <b>完全相等</b>；</li>
 *   <li><b>蚁群算法</b>：随机过程不同，校验其不劣于 S 型基线且接近穷举最优；</li>
 *   <li>所有策略的路径都必须覆盖全部拣货点且首尾为出入口。</li>
 * </ul>
 */
class PickingCrossCheckTest {

    private static final String GOLDEN_FILE =
            "D:/DeepSeekXM/XM01/04-测试与实验/04-实验数据/golden_picking.json";

    private static final double EPSILON = 1e-6;

    /** 跨平台换行符 */
    private static final String LINE_SEP = System.getProperty("line.separator");

    @Test
    @DisplayName("Java 拣货路径策略与 Python 原型结果一致")
    void crossCheckWithPythonGoldenData() throws Exception {
        File golden = new File(GOLDEN_FILE);
        assumeTrue(golden.exists(), "黄金数据不存在，跳过交叉验证：" + GOLDEN_FILE);

        JsonNode root = new ObjectMapper().readTree(golden);
        JsonNode layoutNode = root.get("layout");
        WarehouseLayout layout = new WarehouseLayout(
                layoutNode.get("aisles").asInt(),
                layoutNode.get("rows").asInt(),
                layoutNode.get("aisleGap").asDouble(),
                layoutNode.get("rowGap").asDouble());

        PickingStrategy sShape = new SShapePickingStrategy();
        PickingStrategy largestGap = new LargestGapPickingStrategy();
        PickingStrategy nearest = new NearestNeighborPickingStrategy();
        PickingStrategy aco = new AntColonyPickingStrategy();

        List<String> report = new ArrayList<String>();
        int acoBeatsBaseline = 0;
        // 逐算例记录距离，最后导出为 CSV 供论文与设计文档引用
        List<String> csvRows = new ArrayList<String>();

        for (JsonNode caseNode : root.get("cases")) {
            int pickCount = caseNode.get("pickCount").asInt();

            List<int[]> picks = new ArrayList<int[]>();
            for (JsonNode p : caseNode.get("picks")) {
                picks.add(new int[]{p.get(0).asInt(), p.get(1).asInt()});
            }
            PickPath path = new PickPath(layout, picks);
            assertEquals(pickCount, path.pickCount());

            JsonNode expected = caseNode.get("distances");
            double opt = expected.get("optimal").asDouble();

            // ---- 确定性策略：必须与 Python 完全一致 ----
            assertStrategyMatches("S型", sShape, path, expected.get("sShape").asDouble(), pickCount, report);
            assertStrategyMatches("最大间隙", largestGap, path, expected.get("largestGap").asDouble(), pickCount, report);
            assertStrategyMatches("最近邻", nearest, path, expected.get("nearestNeighbor").asDouble(), pickCount, report);

            // ---- 蚁群算法：不劣于 S 型基线，且不超过穷举最优（允许极小容差） ----
            PickingRoute acoRoute = aco.solve(path, 20261011L);
            assertTrue(PickingStrategy.isValidRoute(path, acoRoute), "ACO 路径必须覆盖全部拣货点且首尾为出入口");
            double sShapeDistance = expected.get("sShape").asDouble();
            assertTrue(acoRoute.getDistance() <= sShapeDistance + EPSILON,
                    "ACO 不应劣于 S 型基线：aco=" + acoRoute.getDistance() + ", sShape=" + sShapeDistance);
            assertTrue(acoRoute.getDistance() >= opt - EPSILON,
                    "ACO 结果不应优于穷举最优（说明距离模型不一致）：aco="
                            + acoRoute.getDistance() + ", optimal=" + opt);
            if (acoRoute.getDistance() < sShapeDistance - EPSILON) {
                acoBeatsBaseline++;
            }
            report.add(String.format("  %2d 点 | 最优 %7.2f | S型 %7.2f | ACO %7.2f | ACO相对S型 %+6.2f%%",
                    pickCount, opt, sShapeDistance, acoRoute.getDistance(),
                    (acoRoute.getDistance() - sShapeDistance) / sShapeDistance * 100));
            csvRows.add(String.format("%d,%.2f,%.2f,%.2f,%.2f,%.2f",
                    pickCount,
                    sShapeDistance,
                    expected.get("largestGap").asDouble(),
                    expected.get("nearestNeighbor").asDouble(),
                    opt,
                    acoRoute.getDistance()));
        }

        // 把三种确定性策略 + 穷举最优 + ACO 的距离导出为 CSV
        try {
            java.io.File csv = new java.io.File(
                    "D:/DeepSeekXM/XM01/04-测试与实验/04-实验数据/exp_picking_java_check.csv");
            StringBuilder sb = new StringBuilder();
            sb.append("pickCount,sShape,largestGap,nearestNeighbor,optimal,aco").append(LINE_SEP);
            for (String row : csvRows) {
                sb.append(row).append(LINE_SEP);
            }
            try (java.io.Writer w = new java.io.OutputStreamWriter(
                    new java.io.FileOutputStream(csv), java.nio.charset.StandardCharsets.UTF_8)) {
                w.write(sb.toString());
            }
            System.out.println("CSV written: " + csv.getAbsolutePath());
        } catch (Exception e) {
            System.out.println("CSV export skipped: " + e.getMessage());
        }

        System.out.println("=== Java 拣货路径规划对照（Python 黄金数据）===");
        for (String line : report) {
            System.out.println(line);
        }
        assertTrue(acoBeatsBaseline > 0,
                "蚁群算法至少在部分算例上应优于 S 型基线，实际提升算例数=" + acoBeatsBaseline);

        // ---- 边界用例：1 个与 2 个拣货点 ----
        // 只有 1 个拣货点时是最容易被忽略的情形：若实现直接返回出入口到出入口，
        // 会得到距离 0 并丢掉拣货点，上层统计与前端可视化都会失真。
        // 期望值按曼哈顿距离手算：distance = 2.5*x + 1.2*y。
        assertEdgeCase("单拣货点 (1,1)", layout, new int[][]{{1, 1}}, 2 * 3.70);
        // (0,0)->(1,1)->(1,2)->(0,0) = 3.70 + 1.20 + 4.90
        assertEdgeCase("两拣货点 (1,1)(1,2)", layout, new int[][]{{1, 1}, {1, 2}}, 9.80);
        assertEdgeCase("单拣货点远角 (10,20)", layout, new int[][]{{10, 20}}, 2 * 49.00);
    }

    /**
     * 边界用例校验：全部策略都必须覆盖全部拣货点，且行走距离符合手算值。
     *
     * @param name             用例名称
     * @param layout           仓库布局
     * @param picks            拣货点坐标
     * @param expectedDistance 手算的期望距离
     */
    private void assertEdgeCase(String name, WarehouseLayout layout, int[][] picks, double expectedDistance) {
        List<int[]> points = new ArrayList<int[]>();
        for (int[] p : picks) {
            points.add(p);
        }
        PickPath path = new PickPath(layout, points);
        PickingStrategy[] strategies = {
                new SShapePickingStrategy(),
                new LargestGapPickingStrategy(),
                new NearestNeighborPickingStrategy(),
                new AntColonyPickingStrategy()
        };
        for (PickingStrategy strategy : strategies) {
            PickingRoute route = strategy.solve(path, 20261011L);
            assertTrue(PickingStrategy.isValidRoute(path, route),
                    name + "：" + strategy.label() + " 的路径必须覆盖全部拣货点且首尾为出入口，实际="
                            + describe(route));
            assertEquals(expectedDistance, route.getDistance(), EPSILON,
                    name + "：" + strategy.label() + " 的距离应为手算值 " + expectedDistance
                            + "，实际=" + route.getDistance());
        }
    }

    /** 把路径渲染为便于阅读的坐标串，用于断言失败时定位 */
    private static String describe(PickingRoute route) {
        StringBuilder sb = new StringBuilder();
        for (int[] p : route.getRoute()) {
            sb.append('(').append(p[0]).append(',').append(p[1]).append(')');
        }
        return sb.toString();
    }

    /** 校验单个确定性策略：距离完全一致、路径合法、不劣于穷举最优 */
    private void assertStrategyMatches(String name, PickingStrategy strategy, PickPath path,
                                       double pythonDistance, int pickCount, List<String> report) {
        PickingRoute route = strategy.solve(path, 20261011L);
        assertEquals(pythonDistance, route.getDistance(), EPSILON,
                name + " 策略在 " + pickCount + " 个拣货点上的行走距离必须与 Python 完全一致");
        assertTrue(PickingStrategy.isValidRoute(path, route),
                name + " 策略路径必须覆盖全部拣货点且首尾为出入口");
    }
}
