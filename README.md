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
