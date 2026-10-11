package com.lijunjie.wms.algorithm.strategy;

import com.lijunjie.wms.algorithm.PickPath;
import com.lijunjie.wms.algorithm.PickingRoute;
import com.lijunjie.wms.algorithm.PickingStrategy;
import com.lijunjie.wms.algorithm.WarehouseLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 蚁群算法（ACO）拣货路径规划（论文 K4 的落点之一）。
 *
 * <p>要素设计与 Python 实现一致：</p>
 * <ul>
 *   <li><b>解表示</b>：访问顺序排列，节点集合 = {出入口} ∪ 拣货点；</li>
 *   <li><b>信息素</b>：路径 (i, j) 上的浓度，初值为常数 1；</li>
 *   <li><b>启发式信息</b>：eta[i][j] = 1 / d(i, j)，距离越短吸引力越大；</li>
 *   <li><b>转移概率</b>：p(j) ∝ tau[i][j]^alpha × eta[i][j]^beta；</li>
 *   <li><b>信息素更新</b>：tau ← (1 - rho) × tau + Σ 沉积量，沉积量 = q / 路径长度；</li>
 *   <li><b>精英策略</b>：每轮额外强化全局最优路径，加速收敛。</li>
 * </ul>
 *
 * <p>参数默认值取自《算法设计说明书》，可通过构造函数覆盖以支撑参数敏感性实验。</p>
 */
public class AntColonyPickingStrategy implements PickingStrategy {

    private final int antCount;
    private final int iterations;
    private final double alpha;
    private final double beta;
    private final double rho;
    private final double q;
    private final int elite;

    public AntColonyPickingStrategy() {
        this(25, 80, 1.0, 3.0, 0.4, 100.0, 2);
    }

    public AntColonyPickingStrategy(int antCount, int iterations, double alpha, double beta,
                                    double rho, double q, int elite) {
        this.antCount = antCount;
        this.iterations = iterations;
        this.alpha = alpha;
        this.beta = beta;
        this.rho = rho;
        this.q = q;
        this.elite = elite;
    }

    @Override
    public String code() {
        return "ant-colony";
    }

    @Override
    public String label() {
        return "蚁群算法";
    }

    @Override
    public PickingRoute solve(PickPath path, long seed) {
        long start = System.currentTimeMillis();
        WarehouseLayout layout = path.getLayout();

        // 节点集合：出入口 + 全部拣货点
        List<int[]> nodes = new ArrayList<int[]>();
        nodes.add(PickPath.io());
        nodes.addAll(path.getPicks());
        int n = nodes.size();

        // 边界情形：只有 1 个拣货点时，节点数为 2。
        // 此时必须真正“去一趟再回来”，距离为 2 × 该点到出入口的距离；
        // 若直接返回 (0,0) -> (0,0)，会得到距离 0 且丢掉拣货点，
        // 是一处会让上层统计与前端可视化同时失真的缺陷。
        if (n == 2) {
            List<int[]> single = new ArrayList<int[]>();
            single.add(PickPath.io());
            single.add(nodes.get(1));
            single.add(PickPath.io());
            List<int[]> cleanedSingle = PickPath.dedupeConsecutive(single);
            return new PickingRoute(label(), cleanedSingle,
                    PickPath.pathLength(layout, cleanedSingle), System.currentTimeMillis() - start);
        }
        if (n < 2) {
            List<int[]> empty = new ArrayList<int[]>();
            empty.add(PickPath.io());
            empty.add(PickPath.io());
            return new PickingRoute(label(), empty, 0.0, System.currentTimeMillis() - start);
        }

        // 距离矩阵与启发式信息
        double[][] dist = new double[n][n];
        double[][] eta = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) {
                    dist[i][j] = Double.POSITIVE_INFINITY;
                    eta[i][j] = 0.0;
                } else {
                    dist[i][j] = distance(layout, nodes.get(i), nodes.get(j));
                    eta[i][j] = 1.0 / dist[i][j];
                }
            }
        }

        double[][] tau = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                tau[i][j] = 1.0;
            }
        }

        Random random = new Random(seed);
        int[] bestOrder = null;
        double bestLength = Double.POSITIVE_INFINITY;

        for (int iter = 0; iter < iterations; iter++) {
            List<int[]> tours = new ArrayList<int[]>(antCount);
            List<Double> lengths = new ArrayList<Double>(antCount);

            for (int ant = 0; ant < antCount; ant++) {
                boolean[] visited = new boolean[n];
                int[] order = new int[n];
                visited[0] = true;
                order[0] = 0;
                int current = 0;

                for (int step = 1; step < n; step++) {
                    // 转移概率：未访问节点的 tau^alpha * eta^beta
                    double[] prob = new double[n];
                    double sum = 0.0;
                    for (int j = 0; j < n; j++) {
                        if (!visited[j]) {
                            prob[j] = Math.pow(tau[current][j], alpha) * Math.pow(eta[current][j], beta);
                            sum += prob[j];
                        }
                    }
                    int next;
                    if (sum <= 0.0) {
                        // 退化情形：直接取最近的未访问节点
                        next = -1;
                        double bestEta = -1.0;
                        for (int j = 0; j < n; j++) {
                            if (!visited[j] && eta[current][j] > bestEta) {
                                bestEta = eta[current][j];
                                next = j;
                            }
                        }
                    } else {
                        double r = random.nextDouble() * sum;
                        double cumulative = 0.0;
                        next = -1;
                        for (int j = 0; j < n; j++) {
                            if (!visited[j] && prob[j] > 0) {
                                cumulative += prob[j];
                                if (r <= cumulative) {
                                    next = j;
                                    break;
                                }
                            }
                        }
                        if (next < 0) {
                            for (int j = n - 1; j >= 0; j--) {
                                if (!visited[j]) {
                                    next = j;
                                    break;
                                }
                            }
                        }
                    }
                    visited[next] = true;
                    order[step] = next;
                    current = next;
                }

                double length = tourLength(dist, order);
                tours.add(order);
                lengths.add(length);
                if (length < bestLength) {
                    bestLength = length;
                    bestOrder = order.clone();
                }
            }

            // 信息素挥发
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    tau[i][j] *= (1.0 - rho);
                }
            }
            // 信息素沉积
            for (int t = 0; t < tours.size(); t++) {
                int[] order = tours.get(t);
                double deposit = q / lengths.get(t);
                for (int k = 0; k + 1 < order.length; k++) {
                    tau[order[k]][order[k + 1]] += deposit;
                    tau[order[k + 1]][order[k]] += deposit;
                }
                tau[order[order.length - 1]][order[0]] += deposit;
                tau[order[0]][order[order.length - 1]] += deposit;
            }
            // 精英强化
            if (bestOrder != null && elite > 0) {
                double extra = elite * q / bestLength;
                for (int k = 0; k + 1 < bestOrder.length; k++) {
                    tau[bestOrder[k]][bestOrder[k + 1]] += extra;
                    tau[bestOrder[k + 1]][bestOrder[k]] += extra;
                }
            }
        }

        // 组装路径：最优访问顺序 + 返回出入口
        List<int[]> route = new ArrayList<int[]>();
        for (int idx : bestOrder) {
            route.add(nodes.get(idx));
        }
        route.add(PickPath.io());
        List<int[]> cleaned = PickPath.dedupeConsecutive(route);
        double distance = PickPath.pathLength(layout, cleaned);
        return new PickingRoute(label(), cleaned, distance, System.currentTimeMillis() - start);
    }

    private static double distance(WarehouseLayout layout, int[] a, int[] b) {
        return layout.getAisleGap() * Math.abs(a[0] - b[0])
                + layout.getRowGap() * Math.abs(a[1] - b[1]);
    }

    /** 环路长度：按访问顺序累加，并计入末节点返回首节点（出入口） */
    private static double tourLength(double[][] dist, int[] order) {
        double total = 0.0;
        for (int k = 0; k + 1 < order.length; k++) {
            total += dist[order[k]][order[k + 1]];
        }
        total += dist[order[order.length - 1]][order[0]];
        return total;
    }
}
