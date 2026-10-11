package com.lijunjie.wms.algorithm;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 拣货路径规划结果。
 */
public class PickingRoute implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String strategy;

    /** 行走顺序，含起终点出入口 */
    private final List<int[]> route;

    /** 总行走距离（米） */
    private final double distance;

    /** 计算耗时（毫秒） */
    private final long elapsedMillis;

    public PickingRoute(String strategy, List<int[]> route, double distance, long elapsedMillis) {
        this.strategy = strategy;
        this.route = new ArrayList<int[]>(route.size());
        for (int[] p : route) {
            this.route.add(new int[]{p[0], p[1]});
        }
        this.distance = distance;
        this.elapsedMillis = elapsedMillis;
    }

    public String getStrategy() {
        return strategy;
    }

    public List<int[]> getRoute() {
        List<int[]> copy = new ArrayList<int[]>(route.size());
        for (int[] p : route) {
            copy.add(new int[]{p[0], p[1]});
        }
        return copy;
    }

    public double getDistance() {
        return distance;
    }

    public long getElapsedMillis() {
        return elapsedMillis;
    }

    /** 拣货点个数（不含起终点） */
    public int pickCount() {
        return Math.max(0, route.size() - 2);
    }
}
