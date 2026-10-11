package com.lijunjie.wms.algorithm.strategy;

import com.lijunjie.wms.algorithm.SlottingAssignment;
import com.lijunjie.wms.algorithm.SlottingInstance;
import com.lijunjie.wms.algorithm.SlottingStrategy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * 基于遗传算法的货位分配策略（论文 K4 的落点之一）。
 *
 * <p>关键设计要素：</p>
 * <ul>
 *   <li><b>编码</b>：整数编码，染色体长度为商品数，第 i 位表示商品 i 被放入的货位编号；</li>
 *   <li><b>适应度</b>：直接取目标函数的负值，避免额外尺度变换；</li>
 *   <li><b>初始化</b>：注入一个频次贪心解（先验知识）+ 其余随机解（多样性）；</li>
 *   <li><b>选择</b>：锦标赛选择，规模可配；</li>
 *   <li><b>交叉</b>：均匀交叉，适合整数编码；</li>
 *   <li><b>变异</b>：按概率随机重置约 2% 的基因位；</li>
 *   <li><b>修复</b>：消除重复货位，重复者改派到最近的空闲货位，高频商品优先；</li>
 *   <li><b>精英保留</b>：每代保留最优若干个体，保证不退化。</li>
 * </ul>
 *
 * <p>参数默认值与《算法设计说明书》表 3.1 一致，可通过构造函数覆盖。</p>
 */
public class GeneticSlottingStrategy implements SlottingStrategy {

    private final int populationSize;
    private final int generations;
    private final double crossoverRate;
    private final double mutationRate;
    private final int tournamentSize;
    private final int elitism;

    public GeneticSlottingStrategy() {
        this(80, 300, 0.9, 0.15, 3, 2);
    }

    public GeneticSlottingStrategy(int populationSize, int generations, double crossoverRate,
                                   double mutationRate, int tournamentSize, int elitism) {
        this.populationSize = populationSize;
        this.generations = generations;
        this.crossoverRate = crossoverRate;
        this.mutationRate = mutationRate;
        this.tournamentSize = tournamentSize;
        this.elitism = elitism;
    }

    @Override
    public String code() {
        return "genetic";
    }

    @Override
    public String label() {
        return "遗传算法分配";
    }

    @Override
    public SlottingAssignment solve(SlottingInstance instance, long seed) {
        long start = System.currentTimeMillis();
        int n = instance.itemCount();
        int slotCount = instance.getLayout().slotCount();
        double[] freqs = instance.getFrequencies();
        double[] ioDistances = instance.getLayout().ioDistances();
        Random random = new Random(seed);

        // 种群初始化：第 1 个个体为频次贪心解
        int[][] population = new int[populationSize][];
        population[0] = new FrequencyGreedyStrategy().solve(instance, seed).getSlotOfItem();
        for (int p = 1; p < populationSize; p++) {
            population[p] = randomPermutation(slotCount, n, random);
        }
        for (int p = 0; p < populationSize; p++) {
            population[p] = repair(population[p], slotCount, freqs, ioDistances);
        }

        double[] costs = new double[populationSize];
        for (int p = 0; p < populationSize; p++) {
            costs[p] = cost(population[p], freqs, ioDistances);
        }

        int bestIdx = argMin(costs);
        int[] best = population[bestIdx].clone();
        double bestCost = costs[bestIdx];

        for (int gen = 0; gen < generations; gen++) {
            // 精英保留
            Integer[] order = sortedIndices(costs);
            List<int[]> nextGeneration = new ArrayList<int[]>(populationSize);
            for (int e = 0; e < Math.min(elitism, populationSize); e++) {
                nextGeneration.add(population[order[e]].clone());
            }

            while (nextGeneration.size() < populationSize) {
                int[] parent1 = tournament(population, costs, random);
                int[] parent2 = tournament(population, costs, random);
                int[] child;
                if (random.nextDouble() < crossoverRate) {
                    child = new int[n];
                    for (int i = 0; i < n; i++) {
                        child[i] = random.nextBoolean() ? parent1[i] : parent2[i];
                    }
                } else {
                    child = parent1.clone();
                }
                if (random.nextDouble() < mutationRate) {
                    int mutateCount = Math.max(1, (int) (0.02 * n));
                    for (int m = 0; m < mutateCount; m++) {
                        int pos = random.nextInt(n);
                        child[pos] = random.nextInt(slotCount);
                    }
                }
                nextGeneration.add(repair(child, slotCount, freqs, ioDistances));
            }

            for (int p = 0; p < populationSize; p++) {
                population[p] = nextGeneration.get(p);
                costs[p] = cost(population[p], freqs, ioDistances);
            }
            int genBest = argMin(costs);
            if (costs[genBest] < bestCost) {
                bestCost = costs[genBest];
                best = population[genBest].clone();
            }
        }

        return new SlottingAssignment(label(), best, bestCost, System.currentTimeMillis() - start);
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------

    private double cost(int[] chromosome, double[] freqs, double[] ioDistances) {
        double sum = 0;
        for (int i = 0; i < chromosome.length; i++) {
            sum += freqs[i] * ioDistances[chromosome[i]];
        }
        return sum;
    }

    /** 消除重复货位：重复者改派到最近的空闲货位，高频商品优先获得近端货位 */
    private int[] repair(int[] chromosome, int slotCount, double[] freqs, double[] ioDistances) {
        int[] result = chromosome.clone();
        boolean[] used = new boolean[slotCount];
        List<Integer> duplicates = new ArrayList<Integer>();
        for (int i = 0; i < result.length; i++) {
            int s = result[i];
            if (s < 0 || s >= slotCount || used[s]) {
                duplicates.add(i);
            } else {
                used[s] = true;
            }
        }
        if (duplicates.isEmpty()) {
            return result;
        }
        // 空闲货位按距离升序
        List<Integer> free = new ArrayList<Integer>();
        for (int s = 0; s < slotCount; s++) {
            if (!used[s]) {
                free.add(s);
            }
        }
        final double[] d = ioDistances;
        free.sort(Comparator.comparingDouble((Integer s) -> d[s]));
        // 重复位按频次降序，优先补给高频商品
        final double[] f = freqs;
        duplicates.sort(Comparator.comparingDouble((Integer i) -> -f[i]));

        for (int k = 0; k < duplicates.size() && k < free.size(); k++) {
            int item = duplicates.get(k);
            int slot = free.get(k);
            result[item] = slot;
            used[slot] = true;
        }
        return result;
    }

    private int[] tournament(int[][] population, double[] costs, Random random) {
        int best = -1;
        double bestCost = Double.MAX_VALUE;
        for (int k = 0; k < tournamentSize; k++) {
            int idx = random.nextInt(population.length);
            if (costs[idx] < bestCost) {
                bestCost = costs[idx];
                best = idx;
            }
        }
        return population[best];
    }

    private int[] randomPermutation(int slotCount, int n, Random random) {
        int[] pool = new int[slotCount];
        for (int i = 0; i < slotCount; i++) {
            pool[i] = i;
        }
        for (int i = 0; i < n; i++) {
            int j = i + random.nextInt(slotCount - i);
            int tmp = pool[i];
            pool[i] = pool[j];
            pool[j] = tmp;
        }
        return Arrays.copyOf(pool, n);
    }

    private static int argMin(double[] values) {
        int best = 0;
        for (int i = 1; i < values.length; i++) {
            if (values[i] < values[best]) {
                best = i;
            }
        }
        return best;
    }

    private static Integer[] sortedIndices(double[] values) {
        Integer[] idx = new Integer[values.length];
        for (int i = 0; i < idx.length; i++) {
            idx[i] = i;
        }
        final double[] v = values;
        Arrays.sort(idx, Comparator.comparingDouble((Integer i) -> v[i]));
        return idx;
    }
}
