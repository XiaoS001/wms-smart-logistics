# 敏感信息与凭据管理说明

> 本文件说明本项目如何处理数据库密码、JWT 密钥等敏感信息，供开发、答辩与代码审查参考。

---

## 一、基本原则

1. **代码库中不出现任何真实凭据**：仓库里的配置文件只包含占位符、环境变量引用或示例值。
2. **本地凭据只存在于开发机**：真实密码写在被 `.gitignore` 排除的文件中。
3. **凭据可替换**：任何人都能用自己的数据库账号与密码在本机跑起项目，无需修改已提交的代码。

## 二、仓库中允许出现的内容

| 文件 | 内容 | 是否含真实密码 |
|---|---|---|
| `03-源码/backend/src/main/resources/application.yml` | 数据库地址、用户名占位、密码用 `${WMS_DB_PASSWORD:...}` 引用 | ❌ 否 |
| `03-源码/backend/src/main/resources/application-local.yml.example` | 本地配置模板，密码处为提示文字 | ❌ 否 |
| `Readme.md` | 部署步骤说明，密码用占位符表示 | ❌ 否 |

## 三、必须排除在仓库之外的内容（已写入 .gitignore）

| 文件 / 目录 | 原因 |
|---|---|
| `application-local.yml` / `.yaml` / `.properties` | 本地真实数据库密码 |
| `05-部署与运维/数据库与开发环境速查表.md` | 含明文密码，仅本地留存 |
| `*.pem` / `*.key` / `*.p12` / `*.jks` / `*.keystore` | 证书与密钥 |
| `*.zip` / `*.msi` / `*.exe` | 安装包（体积大，且非项目内容） |

## 四、本地开发的凭据配置方式

### 方式一：本地配置文件（推荐）

```bash
# 1. 复制模板
cp 03-源码/backend/src/main/resources/application-local.yml.example \
   03-源码/backend/src/main/resources/application-local.yml

# 2. 编辑 application-local.yml，填入真实密码

# 3. 以 local profile 启动
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### 方式二：环境变量

```powershell
$env:WMS_DB_PASSWORD = "你的密码"
$env:WMS_JWT_SECRET  = "你的随机密钥"
mvn spring-boot:run
```

> 两种方式可同时存在，环境变量优先级更高。

## 五、答辩与演示时的注意事项

1. **演示前检查**：确认演示用的电脑上已创建 `application-local.yml`（或已设置环境变量），否则后端启动会因密码占位符而连接失败。
2. **代码展示时**：可以直接展示 `application.yml`，向评委说明"密码用环境变量注入，避免硬编码"，这是工程规范的正向加分项。
3. **切勿**在答辩投屏时打开 `application-local.yml` 或本地速查表。

## 六、如果密码曾经被提交过（补救措施）

1. 立即**修改数据库密码**（最有效，成本最低）；
2. 从版本库中移除该文件并提交；
3. 若仓库已公开，历史记录仍可追溯，因此**改密码是唯一彻底的补救手段**；
4. 本项目在首次提交前已完成上述隔离，不存在历史泄露问题。
