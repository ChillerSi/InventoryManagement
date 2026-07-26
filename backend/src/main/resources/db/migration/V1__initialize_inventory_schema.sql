-- =============================================================================
-- 义采通采购管理系统 - V1 基础数据库结构
-- 数据库：MySQL 8.0+
-- 字符集：utf8mb4，支持中文及扩展字符
--
-- 维护约定：
-- 1. 已发布的迁移脚本禁止修改；结构变更必须新增 V2、V3 等后续迁移。
-- 2. tenant_id 是租户隔离字段，业务查询必须同时携带该条件。
-- 3. UUID 使用 BINARY(16) 存储，减少索引体积。
-- 4. 密码当前按业务要求明文保存；生产环境应改为强哈希并新增迁移脚本。
-- =============================================================================

-- 租户表：一个注册主账号对应一个独立采购后台。
CREATE TABLE tenants (
    id BINARY(16) NOT NULL COMMENT '租户主键，Java UUID 的 16 字节表示',
    name VARCHAR(255) NOT NULL COMMENT '采购公司或采购团队名称',
    created_at DATETIME(6) NOT NULL COMMENT '租户创建时间，精确到微秒',
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '采购租户；店铺、商品、账号和采购单均按租户隔离';

-- 用户表：保存租户主账号与运营、买手、查看者等子账号。
CREATE TABLE users (
    id BINARY(16) NOT NULL COMMENT '用户主键，Java UUID 的 16 字节表示',
    tenant_id BINARY(16) NOT NULL COMMENT '所属租户主键，禁止从客户端直接指定',
    name VARCHAR(255) NOT NULL COMMENT '用户显示姓名，允许在同一租户内重名',
    login_account VARCHAR(255) NOT NULL COMMENT '全局唯一登录账号，不区分大小写使用',
    password VARCHAR(255) NOT NULL COMMENT '登录密码；当前按需求明文保存，禁止写入日志',
    role ENUM('ADMIN', 'OPERATOR', 'BUYER', 'VIEWER') NOT NULL COMMENT '角色：管理员、运营、买手、查看者',
    active BIT(1) NOT NULL DEFAULT b'1' COMMENT '账号是否启用；停用后不得登录',
    owner BIT(1) NOT NULL DEFAULT b'0' COMMENT '是否为不可删除、不可停用的租户主账号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_login_account (login_account)
        COMMENT '保证登录账号全局唯一',
    KEY idx_users_tenant_role (tenant_id, role)
        COMMENT '按租户和角色查询团队成员',
    -- 外键：用户必须归属于一个有效租户；租户存在业务数据时禁止删除。
    CONSTRAINT fk_users_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '系统登录账号，包含租户主账号和子账号';

-- 会话表：保存服务端登录令牌，支持主动失效和到期清理。
CREATE TABLE sessions (
    token VARCHAR(255) NOT NULL COMMENT '随机会话令牌，仅通过 Authorization 请求头传输',
    user_id BINARY(16) NOT NULL COMMENT '会话所属用户主键',
    tenant_id BINARY(16) NOT NULL COMMENT '会话所属租户，用于构建可信租户上下文',
    role ENUM('ADMIN', 'OPERATOR', 'BUYER', 'VIEWER') NOT NULL COMMENT '签发会话时的角色快照',
    expires_at DATETIME(6) NOT NULL COMMENT '会话失效时间',
    PRIMARY KEY (token),
    KEY idx_sessions_user (user_id)
        COMMENT '按用户查找和批量注销会话',
    KEY idx_sessions_expires_at (expires_at)
        COMMENT '支持快速清理过期会话',
    KEY idx_sessions_tenant (tenant_id)
        COMMENT '按租户定位活动会话',
    -- 外键：删除子账号时同步清理其全部登录会话。
    CONSTRAINT fk_sessions_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    -- 外键：会话中的租户必须真实存在。
    CONSTRAINT fk_sessions_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id)
        ON UPDATE RESTRICT ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '服务端登录会话和令牌有效期';

-- 店铺表：保存供应商店铺、档口位置和 MinIO 门头图片对象键。
CREATE TABLE stores (
    id BINARY(16) NOT NULL COMMENT '供应商店铺主键',
    tenant_id BINARY(16) NOT NULL COMMENT '所属租户主键',
    name VARCHAR(255) NOT NULL COMMENT '供应商店铺名称，属于敏感字段',
    location VARCHAR(255) NOT NULL COMMENT '市场、楼层、街道和档口位置，属于敏感字段',
    storefront_object_key VARCHAR(255) NULL COMMENT 'MinIO 中的门头照片对象键，不保存临时签名 URL',
    deleted BIT(1) NOT NULL DEFAULT b'0' COMMENT '软删除标记；1 时业务列表不再展示',
    PRIMARY KEY (id),
    KEY idx_stores_tenant_deleted_location (tenant_id, deleted, location)
        COMMENT '按租户、有效状态和档口位置查询店铺',
    KEY idx_stores_tenant_name (tenant_id, name)
        COMMENT '支持选品中心按店铺名称搜索',
    -- 外键：店铺属于租户；存在店铺时禁止直接删除租户。
    CONSTRAINT fk_stores_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '供应商店铺与档口档案';

-- 商品表：保存商品的结构化档案，图片和向量由独立资源管理。
CREATE TABLE products (
    id BINARY(16) NOT NULL COMMENT '商品主键',
    tenant_id BINARY(16) NOT NULL COMMENT '所属租户主键',
    store_id BINARY(16) NOT NULL COMMENT '所属供应商店铺主键',
    name VARCHAR(255) NOT NULL COMMENT '商品名称，用于结构化关键词搜索',
    price DECIMAL(38, 2) NOT NULL COMMENT '商品参考价格，不代表采购单最终实际价格',
    on_sale BIT(1) NOT NULL DEFAULT b'1' COMMENT '是否上架；下架商品不进入选品和搜图结果',
    deleted BIT(1) NOT NULL DEFAULT b'0' COMMENT '软删除标记；历史采购快照不受影响',
    total_purchased_qty BIGINT NOT NULL DEFAULT 0 COMMENT '历史累计采购数量，用于热采展示',
    PRIMARY KEY (id),
    KEY idx_products_tenant_sale_deleted (tenant_id, on_sale, deleted, id)
        COMMENT '选品中心按租户、上架和有效状态分页查询',
    KEY idx_products_store (store_id)
        COMMENT '按店铺查询商品档案',
    KEY idx_products_tenant_name (tenant_id, name)
        COMMENT '按租户和商品名称进行结构化搜索',
    -- 外键：商品必须属于有效租户。
    CONSTRAINT fk_products_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    -- 外键：店铺存在商品时禁止物理删除，业务使用软删除。
    CONSTRAINT fk_products_store
        FOREIGN KEY (store_id) REFERENCES stores (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '商品档案和上下架状态';

-- 商品图片表：MySQL 只保存 MinIO 对象键和向量化状态，向量值保存在 Qdrant。
CREATE TABLE product_images (
    id BINARY(16) NOT NULL COMMENT '商品图片主键，同时作为 Qdrant point id',
    tenant_id BINARY(16) NOT NULL COMMENT '所属租户主键，用于对象和向量双重隔离',
    product_id BINARY(16) NOT NULL COMMENT '所属商品主键',
    object_key VARCHAR(255) NOT NULL COMMENT 'MinIO 私有 Bucket 中的原图对象键',
    model_version VARCHAR(255) NULL COMMENT '生成向量时使用的 SigLIP 模型版本',
    vector_status VARCHAR(255) NOT NULL DEFAULT 'PENDING' COMMENT '向量状态：PENDING、READY 或 FAILED',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '商品多图的前端展示顺序，数值越小越靠前',
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_images_object_key (object_key)
        COMMENT '避免同一 MinIO 对象被重复登记',
    KEY idx_product_images_product_sort (product_id, sort_order)
        COMMENT '按商品和顺序加载全部图片',
    KEY idx_product_images_tenant_vector_status (tenant_id, vector_status)
        COMMENT '按租户补偿扫描待处理或失败的向量任务',
    -- 外键：图片属于租户。
    CONSTRAINT fk_product_images_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    -- 外键：物理删除商品时级联清理图片元数据；MinIO 对象另行延迟清理。
    CONSTRAINT fk_product_images_product
        FOREIGN KEY (product_id) REFERENCES products (id)
        ON UPDATE RESTRICT ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '商品图片元数据、MinIO 对象键和向量化状态';

-- 采购单表：同时保存业务关联 ID 与不可变快照，保证档案变更后历史仍可阅读。
CREATE TABLE purchase_orders (
    id BINARY(16) NOT NULL COMMENT '采购单主键',
    tenant_id BINARY(16) NOT NULL COMMENT '所属租户主键',
    product_id BINARY(16) NOT NULL COMMENT '关联商品主键',
    store_id BINARY(16) NOT NULL COMMENT '关联供应商店铺主键',
    creator_user_id BINARY(16) NOT NULL COMMENT '创建采购任务的管理员或运营用户主键',
    buyer_user_id BINARY(16) NULL COMMENT '实际完成采购的管理员或买手用户主键',
    plan_qty INT NOT NULL COMMENT '计划采购数量，必须大于零',
    actual_qty INT NULL COMMENT '实际采购数量；待采购时为空',
    actual_price DECIMAL(38, 2) NULL COMMENT '实际采购单价；完成采购时填写',
    operator_remark VARCHAR(1000) NULL COMMENT '运营创建或维护的采购备注',
    buyer_remark VARCHAR(1000) NULL COMMENT '买手执行采购时填写的备注',
    status ENUM('PENDING', 'COMPLETED', 'CANCELLED') NOT NULL DEFAULT 'PENDING'
        COMMENT '采购状态：待采购、已完成、已取消',
    created_at DATETIME(6) NOT NULL COMMENT '采购任务创建时间',
    completed_at DATETIME(6) NULL COMMENT '实际完成采购时间，用于跨天归档',
    product_name_snapshot VARCHAR(255) NULL COMMENT '创建采购单时的商品名称快照',
    store_name_snapshot VARCHAR(255) NULL COMMENT '创建采购单时的店铺名称快照',
    store_location_snapshot VARCHAR(255) NULL COMMENT '创建采购单时的档口位置快照',
    PRIMARY KEY (id),
    KEY idx_purchase_orders_tenant_created (tenant_id, created_at)
        COMMENT '查询指定租户某日创建的采购单',
    KEY idx_purchase_orders_tenant_completed (tenant_id, completed_at)
        COMMENT '查询指定租户某日完成的采购单',
    KEY idx_purchase_orders_tenant_status (tenant_id, status, created_at)
        COMMENT '查询历史遗留的待采购任务',
    KEY idx_purchase_orders_product (product_id)
        COMMENT '统计单个商品的历史采购记录',
    -- 外键：采购单属于租户，历史数据存在时禁止删除租户。
    CONSTRAINT fk_purchase_orders_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    -- 外键：采购记录关联商品；业务删除商品采用软删除。
    CONSTRAINT fk_purchase_orders_product
        FOREIGN KEY (product_id) REFERENCES products (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    -- 外键：采购记录关联店铺；业务删除店铺采用软删除。
    CONSTRAINT fk_purchase_orders_store
        FOREIGN KEY (store_id) REFERENCES stores (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    -- 外键：创建人不可在存在采购记录时物理删除，便于审计。
    CONSTRAINT fk_purchase_orders_creator
        FOREIGN KEY (creator_user_id) REFERENCES users (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    -- 外键：买手账号删除时保留订单，并将买手引用置空。
    CONSTRAINT fk_purchase_orders_buyer
        FOREIGN KEY (buyer_user_id) REFERENCES users (id)
        ON UPDATE RESTRICT ON DELETE SET NULL,
    CONSTRAINT chk_purchase_orders_plan_qty
        CHECK (plan_qty > 0),
    CONSTRAINT chk_purchase_orders_actual_qty
        CHECK (actual_qty IS NULL OR actual_qty > 0),
    CONSTRAINT chk_purchase_orders_actual_price
        CHECK (actual_price IS NULL OR actual_price >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '采购任务、实际采购结果及历史业务快照';

-- -----------------------------------------------------------------------------
-- 存储过程：删除全部已过期会话。
-- 使用场景：由定时任务或运维任务周期调用，避免 sessions 表持续增长。
-- 返回值：result_code=0 表示成功，deleted_rows 表示本次删除数量。
-- -----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS sp_cleanup_expired_sessions;

DELIMITER $$
CREATE PROCEDURE sp_cleanup_expired_sessions()
    COMMENT '清理 expires_at 早于当前数据库时间的登录会话'
BEGIN
    DELETE FROM sessions WHERE expires_at < CURRENT_TIMESTAMP(6);
    SELECT 0 AS result_code, ROW_COUNT() AS deleted_rows;
END$$
DELIMITER ;
