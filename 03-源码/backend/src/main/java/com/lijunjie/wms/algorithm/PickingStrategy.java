package com.lijunjie.wms.algorithm;

/**
 * 拣货路径规划策略接口。
 *
 * <p>实现类对应论文 K3 中的策略族：传统 S 型穿越、最大间隙、最近邻贪婪，
 * 以及基于蚁群算法的智能路径规划。</p>
 *
 * <p><b>实现约定</b>：确定性策略（S 型、最大间隙、最近邻）必须与 Python 算法原型
 * （{@code 03-源码/algorithm/wmsopt/picking.py}）在同一算例上给出完全一致的行走距离，
 * 以便系统与论文实验数据相互印证。</p>
 */
public interface PickingStrategy {

    /** 策略标识，如 s-shape */
    String code();

    /** 策略显示名称 */
    String label();

    /**
     * 求解拣货路径。
     *
     * @param path 算例（布局 + 拣货点）
     * @param seed 随机种子，确定性策略忽略该参数
     * @return 路径与总行走距离
     */
    PickingRoute solve(PickPath path, long seed);

    /** 校验路径是否覆盖全部拣货点各一次，且首尾均为出入口 */
    static boolean isValidRoute(PickPath path, PickingRoute route) {
        java.util.List<int[]> points = route.getRoute();
        if (points.size() < 2) {
            return false;
        }
        int[] first = points.get(0);
        int[] last = points.get(points.size() - 1);
        if (first[0] != 0 || first[1] != 0 || last[0] != 0 || last[1] != 0) {
            return false;
        }
        java.util.Set<String> visited = new java.util.HashSet<String>();
        for (int i = 1; i + 1 < points.size(); i++) {
            int[] p = points.get(i);
            if (p[0] == 0 && p[1] == 0) {
                return false;   // 中途不得回到出入口
            }
            visited.add(p[0] + "," + p[1]);
        }
        for (int[] pick : path.getPicks()) {
            if (!visited.contains(pick[0] + "," + pick[1])) {
                return false;   // 漏拣
            }
        }
        return visited.size() == path.getPicks().size();
    }
}
