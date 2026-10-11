# 项目总览

## 面向智慧物流的仓储管理系统设计与实现

### ——以货位分配与拣货路径优化为核心

![status](https://img.shields.io/badge/status-开发中-blue)
![java](https://img.shields.io/badge/Java-8-orange)
![springboot](https://img.shields.io/badge/Spring%20Boot-2.7.18-green)
![mysql](https://img.shields.io/badge/MySQL-8.0-blue)
![vue](https://img.shields.io/badge/Vue-3-42b883)

| 项目信息 | 内容 |
|---|---|
| 学生 | 李俊杰 |
| 学号 | 252300604 |
| 专业班级 | 软件工程（2306 班） |
| 选题类型 | 工程设计 |
| 选题来源 | 联系生产实际 |
| 起止时间 | 2026-09-15 —— 2027-05-08 |

---

## 一、项目简介

本项目在常规仓储管理系统（用户权限、仓库与库位、商品、入库、出库、库存）的基础上，嵌入两类**决策优化**能力，使系统由「业务记录工具」升级为「业务决策支持工具」：

1. **货位分配优化**：依据商品历史出库频次，将高频出库商品分配到靠近出入口的货位，降低拣货行走距离；
2. **拣货路径规划**：针对给定订单的多个拣货点，规划出行走距离最短的拣货先后顺序。

系统最终交付一套可运行的仓储管理系统、完整设计文档与毕业论文，并通过多组对比实验对优化效果进行量化评估。

## 二、关键技术问题

| 编号 | 关键问题 | 实现位置 |
|---|---|---|
| K1 | 货位分配问题的数学建模（目标函数、容量与类型约束、ABC 分类） | `03-源码/algorithm/wmsopt/model.py` |
| K2 | 货位分配求解算法设计与对比（随机 / 频次贪心 / ABC 分区贪心 / 遗传算法） | `03-源码/algorithm/wmsopt/slotting.py` |
| K3 | 拣货路径规划建模与多策略实现（S 型 / 最大间隙 / 最近邻 / 蚁群） | `03-源码/algorithm/wmsopt/picking.py` |
| K4 | 元启发式算法编码方案与参数设计（GA 与 ACO） | 同上 + `04-测试与实验/03-算法对比实验/` |
| K5 | 优化效果量化评估（数据生成规则、评价指标、穷举最优解基准） | `03-源码/algorithm/experiments.py` |

## 三、实验结论摘要（第一版，可复现）

实验脚本：`03-源码/algorithm/experiments.py`（随机种子固定为 `20261011`）

| 实验 | 指标 | 结果 | 任务书要求 |
|---|---|---|---|
| 拣货路径 | 蚁群算法相对 S 型基线降低 | **17.05% ~ 34.67%**（5 个规模梯度） | ≥ 10% ✅ |
| 货位分配 | 频次贪心相对随机分配（理论目标值） | **降低 60.52%** | — |
| 货位分配 | 频次贪心相对随机分配（仿真拣货距离） | **降低 46.67%** | ≥ 15% ✅ |
| 最优性 | 蚁群算法命中穷举最优解 | 5 组小规模算例中命中 3 组 | — |

图表与数据见 `04-测试与实验/03-算法对比实验/` 与 `04-测试与实验/04-实验数据/`。

## 四、技术栈

| 层次 | 技术 |
|---|---|
| 后端 | JDK 8、Spring Boot 2.7.18、MyBatis-Plus 3.5.7、Druid、JWT |
| 数据库 | MySQL 8.0，17 张表，utf8mb4 / InnoDB |
| 前端 | Vue 3、Vite、Element Plus、ECharts、Axios、Pinia |
| 算法原型 | Python 3.12、NumPy、pandas、matplotlib |
| 部署 | Maven、Docker（规划中） |

## 五、目录结构

```
XM01/
├── 01-文档/                  项目与论文文档
│   ├── 00-项目管理/           总体计划、进度看板、环境审计、问题记录
│   ├── 01-任务书/             学校下发的任务书
│   ├── 02-开题报告/           开题报告书（待写）
│   ├── 03-进度检查/           中期检查等过程材料
│   ├── 04-毕业设计论文/        论文正文与插图
│   ├── 05-答辩材料/           答辩 PPT
│   ├── 06-文献与综述/          参考文献清单
│   └── 99-格式模板/           撰写规范要点摘录
├── 02-设计文档/              设计说明书
│   ├── 01-需求分析/           需求规格说明书（待写）
│   ├── 02-概要设计/           概要设计说明书（待写）
│   ├── 03-数据库设计/          数据库设计说明书 ✅
│   ├── 04-接口文档/           RESTful 接口清单（待写）
│   └── 05-算法设计/           算法设计说明书 ✅
├── 03-源码/
│   ├── algorithm/            算法内核（Python 原型）
│   │   ├── wmsopt/            数据模型、货位分配、拣货路径
│   │   ├── experiments.py     对比实验脚本
│   │   └── tests_selfcheck.py 正确性自检
│   ├── backend/              Spring Boot 后端
│   └── frontend/             Vue 3 前端（待开发）
├── 04-测试与实验/             测试用例、性能测试、算法对比实验与数据
├── 05-部署与运维/
│   ├── sql/                   建表与初始化脚本 ✅
│   ├── scripts/               启停与环境自检脚本
│   └── docker/                容器化配置（规划中）
└── 06-资源/                   参考资料与图表素材
```

## 六、快速开始

### 6.1 环境要求

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | 8 | 后端运行 |
| Maven | 3.9+ | 构建 |
| MySQL | 8.0 | 数据库 |
| Node.js | 18+ | 前端 |
| Python | 3.10+ | 算法原型（需 numpy / pandas / matplotlib） |

### 6.2 初始化数据库

```bash
mysql -u root -p --port=3306 --protocol=TCP < 05-部署与运维/sql/01-schema.sql
mysql -u root -p --port=3306 --protocol=TCP < 05-部署与运维/sql/02-init-data.sql
```

执行后应得到 17 张表、200 个货位、15 个示例商品。

### 6.3 配置本地数据库密码

```bash
# 复制模板并填入真实密码（该文件已被 .gitignore 排除，不会进入版本库）
cp 03-源码/backend/src/main/resources/application-local.yml.example \
   03-源码/backend/src/main/resources/application-local.yml
```

详见 [`SECURITY.md`](SECURITY.md)。

### 6.4 运行算法实验

```bash
cd 03-源码/algorithm
python experiments.py        # 生成实验数据与图表
python tests_selfcheck.py    # 运行正确性自检
```

### 6.5 启动后端

```bash
cd 03-源码/backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

启动后接口文档地址：`http://localhost:8080/swagger-ui.html`

## 七、Git 与推送约定

### 7.1 仓库信息

| 项 | 值 |
|---|---|
| 远程仓库 | `https://github.com/XiaoS001/wms-smart-logistics`（公开） |
| 默认分支 | `main` |
| 提交身份 | 李俊杰 / 2685210142@qq.com |

### 7.2 网络与代理（重要）

GitHub 的连通性随网络环境变化，`git` **不会自动使用系统代理**，因此推送前需按当前网络状态二选一：

```powershell
# 情况一：Clash 等代理软件正在运行（系统代理开启）
git config --local http.proxy  http://127.0.0.1:7897
git config --local https.proxy http://127.0.0.1:7897

# 情况二：直连可用（代理已退出）
git config --local --unset http.proxy
git config --local --unset https.proxy
```

判断依据：`git ls-remote origin HEAD` 能返回提交号即当前配置可用。

### 7.3 日常提交

```bash
git add -A
git commit -m "feat: 简明描述本次改动"
git push origin main
```

**约定**：里程碑节点（M1/M2/M3）必推一次；`03-源码/backend/src/main/resources/application-local.yml` 与含密码的本地文档**永远不要**提交。

## 八、文档索引

| 文档 | 位置 |
|---|---|
| 总体计划与实施方案 | [`01-文档/00-项目管理/`](01-文档/00-项目管理/) |
| 进度看板 | [`01-文档/03-进度检查/进度看板.md`](01-文档/03-进度检查/进度看板.md) |
| 论文撰写规范要点 | [`01-文档/99-格式模板/`](01-文档/99-格式模板/) |
| 算法设计说明书 | [`02-设计文档/05-算法设计/`](02-设计文档/05-算法设计/) |
| 数据库设计说明书 | [`02-设计文档/03-数据库设计/`](02-设计文档/03-数据库设计/) |
| 实验结果汇总 | [`04-测试与实验/03-算法对比实验/实验结果汇总.md`](04-测试与实验/03-算法对比实验/实验结果汇总.md) |
| 敏感信息管理说明 | [`SECURITY.md`](SECURITY.md) |

## 九、开发进度

| 阶段 | 状态 |
|---|---|
| 项目准备（环境、目录、计划） | ✅ 完成 |
| 算法内核（K1–K5）与第一版实验 | ✅ 完成 |
| 数据库设计（17 张表 + 脚本） | ✅ 完成 |
| 算法设计说明书 | ✅ 完成 |
| 数据库设计说明书 | ✅ 完成 |
| 需求规格说明书 | ✅ 完成 |
| 算法内核移植为 Java 版（含交叉验证） | ✅ 完成 |
| 后端开发（9 个模块） | 🔄 进行中：用户权限 ✅、仓库库位 ✅、商品管理 ✅、入库 ✅、出库 ✅、库存 ✅、货位优化 🔄、拣货路径 ✅、报表统计 ⬜ |
| 前端开发 | ⬜ 待开始 |
| 系统测试与正式实验报告 | ⬜ 待开始 |
| 毕业论文 | ⬜ 待开始 |

## 十、开发期检查工具

除各模块的端到端验证脚本外，`build/diag/CheckMapperXml.java` 是一个结构审查工具，用于检查 MyBatis 注解 SQL 的 XML 合法性：

```powershell
# 编译（一次即可）
D:\Dev\jdk8\bin\javac.exe -encoding UTF-8 -d build\diag build\diag\CheckMapperXml.java
# 运行（需先构建后端）
D:\Dev\jdk8\bin\java.exe -Dfile.encoding=UTF-8 -cp build\diag CheckMapperXml 03-源码\backend\target\classes
```

**为什么需要它**：`@Select` 等注解若以 `<script>` 开头，MyBatis 会按 XML 解析。此时 SQL 中裸写的小于号（例如 `<=`）是非法 XML 字符，会导致**应用启动即失败**，报“元素内容必须由格式正确的字符数据或标记组成”。比较运算符必须写成 `&lt;=`。

该工具直接解析 class 文件的常量池（这类注解的保留级别是 `CLASS`，反射读不到），对全部以 `<script>` 开头的 SQL 做 XML 语法校验。

## 十一、许可证

本项目为教学用途的毕业设计作品。算法实现与文档均为本人独立完成，参考的公开文献已在论文与设计文档中标注。

## 十二、说明

系统中使用的示例数据（商品、仓库、订单）均为程序生成的模拟数据，不涉及任何真实企业数据。数据库连接等敏感信息按 [`SECURITY.md`](SECURITY.md) 的约定管理，未进入版本库。
