# 义采通 Inventory Management

基于原型实现的可运行 MVP：

- `frontend`：Vue 3 + TypeScript + Vite，包含注册/登录、选品中心、今日采购、档案管理、我的。
- `backend`：Java 21 + Spring Boot 3，使用 MySQL 保存业务数据，MinIO 保存图片，Qdrant 保存 SigLIP 向量。
- `embedding-service`：FastAPI + Transformers SigLIP，提供图片向量接口。
- `docker-compose.yml`：一键启动 MySQL、MinIO、Qdrant、向量服务、后端和前端。

## 快速启动

1. 安装 Docker Desktop。
2. 在项目根目录复制环境变量：`Copy-Item .env.example .env`
3. 启动：`docker compose up --build`
4. 打开 `http://localhost:5173`

首次注册会创建一个独立租户和管理员。演示环境按需求明文保存密码；**生产环境必须改用 BCrypt/Argon2id**。

## 图片框选与向量检索

商品档案上传或以图搜图时，前端先显示图片并让用户拖拽框选目标商品。浏览器将框选区域裁成 JPEG 后上传。后端将图片保存到 MinIO，调用 SigLIP 服务生成向量，并写入 Qdrant，payload 包含 `tenantId`、`productId`、`imageId`。搜图默认返回 Top 20，并按 `IMAGE_SEARCH_THRESHOLD` 过滤。

## 默认端口

| 服务 | 地址 |
|---|---|
| Web | http://localhost:5173 |
| API | http://localhost:8080 |
| MinIO Console | http://localhost:9001 |
| Qdrant | http://localhost:6333 |
| SigLIP | http://localhost:8000 |

## 说明

- 所有业务查询在服务端从登录 token 获取 `tenantId`，不信任客户端传入值。
- ADMIN/BUYER 可查看店铺和档口；OPERATOR/VIEWER 的接口响应会清空这些敏感字段。
- ADMIN/OPERATOR 可建采购单；ADMIN/BUYER 可完成采购；只有 ADMIN 可维护档案和团队。
- 当前 token 为数据库会话 token，便于 MVP 立即运行；可后续替换为 JWT + Refresh Token。

## 后端代码结构

```text
backend/src/main/java/com/yicaitong/
├─ application/     # Spring Boot 主启动类
├─ controller/      # REST 接口与请求/响应模型
├─ domain/          # JPA 实体和业务枚举
├─ exception/       # 统一业务异常和全局异常处理
├─ repository/      # Spring Data JPA 数据查询层
├─ service/         # MinIO、SigLIP、Qdrant 等业务服务
├─ security/        # 登录会话、租户上下文、权限与异常处理
└─ logging/         # HTTP 请求链路日志
```

## 日志

服务同时输出控制台日志和滚动文件日志。默认文件为 `./logs/inventory-api.log`，
归档文件按日期和 100MB 大小切分，压缩后保留 30 天，总容量上限 10GB。
每个请求会返回 `X-Request-Id`，日志中可使用同一 ID 串联接口调用和异常堆栈。

可通过 `.env` 中的 `LOG_LEVEL`、`APP_LOG_LEVEL`、`WEB_LOG_LEVEL`、
`SQL_LOG_LEVEL` 和 `SQL_BIND_LOG_LEVEL` 调整打印级别。生产环境不建议开启
SQL 参数日志，日志中也禁止写入密码、Token、MinIO 密钥或图片内容。

## 数据库迁移

数据库结构由 Flyway 管理，迁移文件位于
`backend/src/main/resources/db/migration`。首次启动会执行
`V1__initialize_inventory_schema.sql`，创建业务表、字段注释、查询索引、
外键约束、数据检查约束和过期会话清理存储过程。

已经在任何环境执行过的迁移文件不可修改。后续结构变更应新增
`V2__描述.sql`、`V3__描述.sql` 等版本文件。Hibernate 只执行结构校验，
不会再使用 `ddl-auto=update` 自动修改生产数据库。
