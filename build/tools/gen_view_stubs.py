# -*- coding: utf-8 -*-
"""为尚未实现的页面生成占位组件。

为什么需要这个工具：
路由表中登记了全部页面，如果组件文件缺失，开发服务器会直接报模块解析失败，
无法启动验证。占位组件让工程随时可运行，同时把待实现清单显式列在页面上，
避免"忘记实现某个页面"。

实现真实的页面后，直接用真实组件覆盖同名文件即可，无需改动路由。

用法：
    python build/tools/gen_view_stubs.py          # 只补缺失的页面
    python build/tools/gen_view_stubs.py --force  # 覆盖已有占位页（不覆盖真实实现）
"""
import io
import os
import sys

ROOT = r"D:\DeepSeekXM\XM01"
VIEW_DIR = os.path.join(ROOT, "03-源码", "frontend", "src", "views")

# 页面清单：(相对路径, 页面标题, 该页面将使用的后端接口)
STUBS = [
    ("dashboard/index.vue", "首页概览", [
        "GET /api/report/dashboard", "GET /api/report/operation-trend"
    ]),
    ("base/warehouse/index.vue", "仓库管理", [
        "GET /api/warehouses", "POST /api/warehouses", "PUT /api/warehouses/{id}",
        "DELETE /api/warehouses/{id}", "POST /api/warehouses/{id}/locations/generate"
    ]),
    ("base/location/index.vue", "库位管理", [
        "GET /api/locations", "GET /api/locations/nearest-free",
        "GET /api/warehouses/{id}/locations/summary"
    ]),
    ("base/product/index.vue", "商品管理", [
        "GET /api/products", "POST /api/products", "PUT /api/products/{id}",
        "DELETE /api/products/{id}", "POST /api/products/abc-classify"
    ]),
    ("base/category/index.vue", "商品分类", [
        "GET /api/categories/tree", "POST /api/categories",
        "PUT /api/categories/{id}", "DELETE /api/categories/{id}"
    ]),
    ("inbound/index.vue", "入库管理", [
        "GET /api/inbound/orders", "POST /api/inbound/orders",
        "POST /api/inbound/orders/{id}/confirm", "POST /api/inbound/orders/{id}/cancel"
    ]),
    ("outbound/index.vue", "出库管理", [
        "GET /api/outbound/orders", "POST /api/outbound/orders",
        "POST /api/outbound/orders/{id}/plan-route", "POST /api/outbound/orders/{id}/confirm"
    ]),
    ("stock/index.vue", "库存管理", [
        "GET /api/stock", "GET /api/stock/summary", "GET /api/stock/alerts",
        "GET /api/stock/warehouse/{id}/summary"
    ]),
    ("stock/stocktake.vue", "库存盘点", [
        "POST /api/stock/stocktake/generate", "GET /api/stock/stocktake",
        "POST /api/stock/stocktake/submit", "DELETE /api/stock/stocktake/{no}"
    ]),
    ("picking/index.vue", "拣货路径规划", [
        "GET /api/outbound/orders", "POST /api/outbound/orders/{id}/plan-route",
        "GET /api/outbound/strategies"
    ]),
    ("slotting/index.vue", "货位优化", [
        "POST /api/slotting/plans/preview", "GET /api/slotting/strategies",
        "POST /api/slotting/recommend/{warehouseId}"
    ]),
    ("slotting/plan.vue", "优化方案", [
        "GET /api/slotting/plans", "POST /api/slotting/plans/{id}/apply",
        "DELETE /api/slotting/plans/{id}", "GET /api/slotting/plans/statistics"
    ]),
    ("report/algorithm.vue", "算法效果", [
        "GET /api/report/slotting-effect/{warehouseId}",
        "GET /api/report/picking-effect/{warehouseId}",
        "GET /api/report/route-algorithm-statistics"
    ]),
    ("report/stock.vue", "库存与作业", [
        "GET /api/report/operation-trend", "GET /api/report/stock-distribution",
        "GET /api/report/frequency-contribution"
    ]),
    ("system/user.vue", "用户管理", [
        "GET /api/users", "GET /api/users/{id}"
    ]),
    ("system/profile.vue", "个人信息", [
        "GET /api/auth/me"
    ]),
]

TEMPLATE = """<script setup lang="ts">
/**
 * {title}（占位页面）
 *
 * 该页面尚未实现，此处先登记占位组件，保证路由完整、工程可启动。
 * 实现时直接用真实组件覆盖本文件即可，无需改动路由表。
 *
 * 规划使用的后端接口：
{api_lines}
 */
</script>

<template>
  <div class="page-root">
    <div class="wms-card stub">
      <ElIcon class="stub__icon" :size="46"><Tools /></ElIcon>
      <h2 class="stub__title">{title}</h2>
      <p class="stub__desc">该页面正在开发中，敬请期待。</p>

      <div class="stub__section">
        <div class="stub__section-title">规划使用的后端接口</div>
        <ul class="stub__list">
{api_items}
        </ul>
      </div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.stub {{
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 56px 20px;
  text-align: center;

  &__icon {{
    color: var(--el-color-primary-light-5);
  }}

  &__title {{
    margin: 18px 0 0;
    font-size: 20px;
    font-weight: 600;
    color: #303133;
  }}

  &__desc {{
    margin: 8px 0 0;
    font-size: 13px;
    color: #909399;
  }}

  &__section {{
    margin-top: 30px;
    width: 100%;
    max-width: 520px;
    text-align: left;
  }}

  &__section-title {{
    font-size: 13px;
    font-weight: 600;
    color: #606266;
    margin-bottom: 10px;
  }}

  &__list {{
    margin: 0;
    padding: 12px 16px;
    list-style: none;
    background: #f8fafc;
    border: 1px dashed #dcdfe6;
    border-radius: 6px;

    li {{
      font-family: 'Consolas', monospace;
      font-size: 12px;
      color: #475569;
      line-height: 2;
    }}
  }}
}}
</style>
"""


def is_stub(text: str) -> bool:
    return "（占位页面）" in text or "该页面正在开发中" in text


def main() -> int:
    force = "--force" in sys.argv
    created, skipped, kept = 0, 0, 0

    for rel, title, apis in STUBS:
        path = os.path.join(VIEW_DIR, rel.replace("/", os.sep))
        os.makedirs(os.path.dirname(path), exist_ok=True)

        if os.path.exists(path):
            with io.open(path, "r", encoding="utf-8") as fh:
                existing = fh.read()
            if is_stub(existing):
                if not force:
                    skipped += 1
                    continue
            else:
                # 已是真实实现：绝不覆盖
                kept += 1
                print("  keep    %s（已有真实实现，未覆盖）" % rel)
                continue

        content = TEMPLATE.format(
            title=title,
            api_lines="\n".join(" *   - " + a for a in apis),
            api_items="\n".join("          <li>%s</li>" % a for a in apis)
        )
        with io.open(path, "w", encoding="utf-8", newline="\n") as fh:
            fh.write(content)
        created += 1
        print("  create  %s" % rel)

    print()
    print("created=%d skipped=%d kept(real implementation)=%d" % (created, skipped, kept))
    return 0


if __name__ == "__main__":
    sys.exit(main())
