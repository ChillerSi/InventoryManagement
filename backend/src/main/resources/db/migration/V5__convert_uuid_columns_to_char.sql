-- =============================================================================
-- 义采通采购管理系统 - V5 UUID 可读化迁移
-- 数据库：MySQL 8.0+
--
-- 目标：
-- 1. 将业务表中的 UUID 从 BINARY(16) 转换为标准 CHAR(36) 文本。
-- 2. 保留 UUID 原值、主键、索引、外键及删除规则，方便在 DBeaver 中人工辨识。
-- 3. UUID 文本统一使用小写格式：xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx。
--
-- 转换策略：
-- 1. 先删除依赖 UUID 字段的外键，避免父子字段分阶段转换时类型不一致。
-- 2. 将 BINARY(16) 扩展为 VARBINARY(36)，为写入 36 字节 UUID 文本提供空间。
-- 3. 使用 HEX 和 SUBSTRING 按 Java UUID 的原始字节顺序生成带连字符文本。
-- 4. 将中间字段固定为 CHAR(36) CHARACTER SET ascii COLLATE ascii_bin。
-- 5. 按原始名称、引用关系和 ON DELETE/ON UPDATE 规则恢复全部外键。
--
-- 注意：
-- 1. 已执行过的 V1 脚本不得修改，本次结构调整只通过 V5 完成。
-- 2. DDL 在 MySQL 中会隐式提交，生产执行前必须完成数据库备份。
-- 3. ascii_bin 使 UUID 比较大小写敏感；应用统一生成并保存小写 UUID。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 第一阶段：暂时解除全部 UUID 外键。
-- 主键和普通索引保留，字段类型转换完成后继续使用原索引。
-- -----------------------------------------------------------------------------
ALTER TABLE product_images DROP FOREIGN KEY fk_product_images_product;
ALTER TABLE product_images DROP FOREIGN KEY fk_product_images_tenant;
ALTER TABLE products DROP FOREIGN KEY fk_products_store;
ALTER TABLE products DROP FOREIGN KEY fk_products_tenant;
ALTER TABLE purchase_orders DROP FOREIGN KEY fk_purchase_orders_buyer;
ALTER TABLE purchase_orders DROP FOREIGN KEY fk_purchase_orders_creator;
ALTER TABLE purchase_orders DROP FOREIGN KEY fk_purchase_orders_product;
ALTER TABLE purchase_orders DROP FOREIGN KEY fk_purchase_orders_store;
ALTER TABLE purchase_orders DROP FOREIGN KEY fk_purchase_orders_tenant;
ALTER TABLE sessions DROP FOREIGN KEY fk_sessions_tenant;
ALTER TABLE sessions DROP FOREIGN KEY fk_sessions_user;
ALTER TABLE stores DROP FOREIGN KEY fk_stores_tenant;
ALTER TABLE users DROP FOREIGN KEY fk_users_tenant;

-- -----------------------------------------------------------------------------
-- 第二阶段：把固定 16 字节字段扩展成可容纳标准 UUID 文本的二进制字段。
-- 此时原始 16 字节内容保持不变，便于下一阶段无损转成十六进制文本。
-- -----------------------------------------------------------------------------
ALTER TABLE tenants
    MODIFY id VARBINARY(36) NOT NULL COMMENT '租户主键；转换中的标准 UUID 文本';

ALTER TABLE users
    MODIFY id VARBINARY(36) NOT NULL COMMENT '用户主键；转换中的标准 UUID 文本',
    MODIFY tenant_id VARBINARY(36) NOT NULL COMMENT '所属租户主键；转换中的标准 UUID 文本';

ALTER TABLE sessions
    MODIFY user_id VARBINARY(36) NOT NULL COMMENT '会话所属用户主键；转换中的标准 UUID 文本',
    MODIFY tenant_id VARBINARY(36) NOT NULL COMMENT '会话所属租户主键；转换中的标准 UUID 文本';

ALTER TABLE stores
    MODIFY id VARBINARY(36) NOT NULL COMMENT '供应商店铺主键；转换中的标准 UUID 文本',
    MODIFY tenant_id VARBINARY(36) NOT NULL COMMENT '所属租户主键；转换中的标准 UUID 文本';

ALTER TABLE products
    MODIFY id VARBINARY(36) NOT NULL COMMENT '商品主键；转换中的标准 UUID 文本',
    MODIFY tenant_id VARBINARY(36) NOT NULL COMMENT '所属租户主键；转换中的标准 UUID 文本',
    MODIFY store_id VARBINARY(36) NOT NULL COMMENT '所属供应商店铺主键；转换中的标准 UUID 文本';

ALTER TABLE product_images
    MODIFY id VARBINARY(36) NOT NULL COMMENT '商品图片主键；转换中的标准 UUID 文本',
    MODIFY tenant_id VARBINARY(36) NOT NULL COMMENT '所属租户主键；转换中的标准 UUID 文本',
    MODIFY product_id VARBINARY(36) NOT NULL COMMENT '所属商品主键；转换中的标准 UUID 文本';

ALTER TABLE purchase_orders
    MODIFY id VARBINARY(36) NOT NULL COMMENT '采购单主键；转换中的标准 UUID 文本',
    MODIFY tenant_id VARBINARY(36) NOT NULL COMMENT '所属租户主键；转换中的标准 UUID 文本',
    MODIFY product_id VARBINARY(36) NOT NULL COMMENT '关联商品主键；转换中的标准 UUID 文本',
    MODIFY store_id VARBINARY(36) NOT NULL COMMENT '关联供应商店铺主键；转换中的标准 UUID 文本',
    MODIFY creator_user_id VARBINARY(36) NOT NULL COMMENT '采购单创建用户主键；转换中的标准 UUID 文本',
    MODIFY buyer_user_id VARBINARY(36) NULL COMMENT '实际采购买手用户主键；转换中的标准 UUID 文本';

-- -----------------------------------------------------------------------------
-- 第三阶段：将每个 16 字节 UUID 转为带连字符的 36 位小写文本。
-- buyer_user_id 允许为空；MySQL 的 CONCAT 对 NULL 返回 NULL，因此空值保持不变。
-- -----------------------------------------------------------------------------
UPDATE tenants
SET id = LOWER(CONCAT(
    SUBSTRING(HEX(id), 1, 8), '-',
    SUBSTRING(HEX(id), 9, 4), '-',
    SUBSTRING(HEX(id), 13, 4), '-',
    SUBSTRING(HEX(id), 17, 4), '-',
    SUBSTRING(HEX(id), 21, 12)
));

UPDATE users
SET id = LOWER(CONCAT(
        SUBSTRING(HEX(id), 1, 8), '-',
        SUBSTRING(HEX(id), 9, 4), '-',
        SUBSTRING(HEX(id), 13, 4), '-',
        SUBSTRING(HEX(id), 17, 4), '-',
        SUBSTRING(HEX(id), 21, 12)
    )),
    tenant_id = LOWER(CONCAT(
        SUBSTRING(HEX(tenant_id), 1, 8), '-',
        SUBSTRING(HEX(tenant_id), 9, 4), '-',
        SUBSTRING(HEX(tenant_id), 13, 4), '-',
        SUBSTRING(HEX(tenant_id), 17, 4), '-',
        SUBSTRING(HEX(tenant_id), 21, 12)
    ));

UPDATE sessions
SET user_id = LOWER(CONCAT(
        SUBSTRING(HEX(user_id), 1, 8), '-',
        SUBSTRING(HEX(user_id), 9, 4), '-',
        SUBSTRING(HEX(user_id), 13, 4), '-',
        SUBSTRING(HEX(user_id), 17, 4), '-',
        SUBSTRING(HEX(user_id), 21, 12)
    )),
    tenant_id = LOWER(CONCAT(
        SUBSTRING(HEX(tenant_id), 1, 8), '-',
        SUBSTRING(HEX(tenant_id), 9, 4), '-',
        SUBSTRING(HEX(tenant_id), 13, 4), '-',
        SUBSTRING(HEX(tenant_id), 17, 4), '-',
        SUBSTRING(HEX(tenant_id), 21, 12)
    ));

UPDATE stores
SET id = LOWER(CONCAT(
        SUBSTRING(HEX(id), 1, 8), '-',
        SUBSTRING(HEX(id), 9, 4), '-',
        SUBSTRING(HEX(id), 13, 4), '-',
        SUBSTRING(HEX(id), 17, 4), '-',
        SUBSTRING(HEX(id), 21, 12)
    )),
    tenant_id = LOWER(CONCAT(
        SUBSTRING(HEX(tenant_id), 1, 8), '-',
        SUBSTRING(HEX(tenant_id), 9, 4), '-',
        SUBSTRING(HEX(tenant_id), 13, 4), '-',
        SUBSTRING(HEX(tenant_id), 17, 4), '-',
        SUBSTRING(HEX(tenant_id), 21, 12)
    ));

UPDATE products
SET id = LOWER(CONCAT(
        SUBSTRING(HEX(id), 1, 8), '-',
        SUBSTRING(HEX(id), 9, 4), '-',
        SUBSTRING(HEX(id), 13, 4), '-',
        SUBSTRING(HEX(id), 17, 4), '-',
        SUBSTRING(HEX(id), 21, 12)
    )),
    tenant_id = LOWER(CONCAT(
        SUBSTRING(HEX(tenant_id), 1, 8), '-',
        SUBSTRING(HEX(tenant_id), 9, 4), '-',
        SUBSTRING(HEX(tenant_id), 13, 4), '-',
        SUBSTRING(HEX(tenant_id), 17, 4), '-',
        SUBSTRING(HEX(tenant_id), 21, 12)
    )),
    store_id = LOWER(CONCAT(
        SUBSTRING(HEX(store_id), 1, 8), '-',
        SUBSTRING(HEX(store_id), 9, 4), '-',
        SUBSTRING(HEX(store_id), 13, 4), '-',
        SUBSTRING(HEX(store_id), 17, 4), '-',
        SUBSTRING(HEX(store_id), 21, 12)
    ));

UPDATE product_images
SET id = LOWER(CONCAT(
        SUBSTRING(HEX(id), 1, 8), '-',
        SUBSTRING(HEX(id), 9, 4), '-',
        SUBSTRING(HEX(id), 13, 4), '-',
        SUBSTRING(HEX(id), 17, 4), '-',
        SUBSTRING(HEX(id), 21, 12)
    )),
    tenant_id = LOWER(CONCAT(
        SUBSTRING(HEX(tenant_id), 1, 8), '-',
        SUBSTRING(HEX(tenant_id), 9, 4), '-',
        SUBSTRING(HEX(tenant_id), 13, 4), '-',
        SUBSTRING(HEX(tenant_id), 17, 4), '-',
        SUBSTRING(HEX(tenant_id), 21, 12)
    )),
    product_id = LOWER(CONCAT(
        SUBSTRING(HEX(product_id), 1, 8), '-',
        SUBSTRING(HEX(product_id), 9, 4), '-',
        SUBSTRING(HEX(product_id), 13, 4), '-',
        SUBSTRING(HEX(product_id), 17, 4), '-',
        SUBSTRING(HEX(product_id), 21, 12)
    ));

UPDATE purchase_orders
SET id = LOWER(CONCAT(
        SUBSTRING(HEX(id), 1, 8), '-',
        SUBSTRING(HEX(id), 9, 4), '-',
        SUBSTRING(HEX(id), 13, 4), '-',
        SUBSTRING(HEX(id), 17, 4), '-',
        SUBSTRING(HEX(id), 21, 12)
    )),
    tenant_id = LOWER(CONCAT(
        SUBSTRING(HEX(tenant_id), 1, 8), '-',
        SUBSTRING(HEX(tenant_id), 9, 4), '-',
        SUBSTRING(HEX(tenant_id), 13, 4), '-',
        SUBSTRING(HEX(tenant_id), 17, 4), '-',
        SUBSTRING(HEX(tenant_id), 21, 12)
    )),
    product_id = LOWER(CONCAT(
        SUBSTRING(HEX(product_id), 1, 8), '-',
        SUBSTRING(HEX(product_id), 9, 4), '-',
        SUBSTRING(HEX(product_id), 13, 4), '-',
        SUBSTRING(HEX(product_id), 17, 4), '-',
        SUBSTRING(HEX(product_id), 21, 12)
    )),
    store_id = LOWER(CONCAT(
        SUBSTRING(HEX(store_id), 1, 8), '-',
        SUBSTRING(HEX(store_id), 9, 4), '-',
        SUBSTRING(HEX(store_id), 13, 4), '-',
        SUBSTRING(HEX(store_id), 17, 4), '-',
        SUBSTRING(HEX(store_id), 21, 12)
    )),
    creator_user_id = LOWER(CONCAT(
        SUBSTRING(HEX(creator_user_id), 1, 8), '-',
        SUBSTRING(HEX(creator_user_id), 9, 4), '-',
        SUBSTRING(HEX(creator_user_id), 13, 4), '-',
        SUBSTRING(HEX(creator_user_id), 17, 4), '-',
        SUBSTRING(HEX(creator_user_id), 21, 12)
    )),
    buyer_user_id = LOWER(CONCAT(
        SUBSTRING(HEX(buyer_user_id), 1, 8), '-',
        SUBSTRING(HEX(buyer_user_id), 9, 4), '-',
        SUBSTRING(HEX(buyer_user_id), 13, 4), '-',
        SUBSTRING(HEX(buyer_user_id), 17, 4), '-',
        SUBSTRING(HEX(buyer_user_id), 21, 12)
    ));

-- -----------------------------------------------------------------------------
-- 第四阶段：固定 UUID 字段为可读 CHAR(36)。
-- 使用单字节 ASCII 字符集控制索引体积，ascii_bin 保证主外键精确比较。
-- -----------------------------------------------------------------------------
ALTER TABLE tenants
    MODIFY id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT '租户主键；标准小写 UUID，格式 xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx';

ALTER TABLE users
    MODIFY id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT '用户主键；标准小写 UUID',
    MODIFY tenant_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT '所属租户主键；关联 tenants.id';

ALTER TABLE sessions
    MODIFY user_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT '会话所属用户主键；关联 users.id',
    MODIFY tenant_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT '会话所属租户主键；用于可信租户上下文';

ALTER TABLE stores
    MODIFY id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT '供应商店铺主键；标准小写 UUID',
    MODIFY tenant_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT '所属租户主键；关联 tenants.id';

ALTER TABLE products
    MODIFY id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT '商品主键；标准小写 UUID',
    MODIFY tenant_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT '所属租户主键；关联 tenants.id',
    MODIFY store_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT '所属供应商店铺主键；关联 stores.id';

ALTER TABLE product_images
    MODIFY id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT '商品图片主键；标准小写 UUID',
    MODIFY tenant_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT '所属租户主键；用于对象和向量隔离',
    MODIFY product_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT '所属商品主键；关联 products.id';

ALTER TABLE purchase_orders
    MODIFY id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT '采购单主键；标准小写 UUID',
    MODIFY tenant_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT '所属租户主键；关联 tenants.id',
    MODIFY product_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT '关联商品主键；关联 products.id',
    MODIFY store_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT '关联供应商店铺主键；关联 stores.id',
    MODIFY creator_user_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT '创建采购任务的用户主键；关联 users.id',
    MODIFY buyer_user_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL
        COMMENT '实际完成采购的买手用户主键；关联 users.id';

-- -----------------------------------------------------------------------------
-- 第五阶段：恢复全部外键和原有更新、删除规则。
-- -----------------------------------------------------------------------------
ALTER TABLE users
    ADD CONSTRAINT fk_users_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE sessions
    ADD CONSTRAINT fk_sessions_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON UPDATE RESTRICT ON DELETE CASCADE,
    ADD CONSTRAINT fk_sessions_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id)
        ON UPDATE RESTRICT ON DELETE CASCADE;

ALTER TABLE stores
    ADD CONSTRAINT fk_stores_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE products
    ADD CONSTRAINT fk_products_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    ADD CONSTRAINT fk_products_store
        FOREIGN KEY (store_id) REFERENCES stores (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT;

ALTER TABLE product_images
    ADD CONSTRAINT fk_product_images_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    ADD CONSTRAINT fk_product_images_product
        FOREIGN KEY (product_id) REFERENCES products (id)
        ON UPDATE RESTRICT ON DELETE CASCADE;

ALTER TABLE purchase_orders
    ADD CONSTRAINT fk_purchase_orders_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    ADD CONSTRAINT fk_purchase_orders_product
        FOREIGN KEY (product_id) REFERENCES products (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    ADD CONSTRAINT fk_purchase_orders_store
        FOREIGN KEY (store_id) REFERENCES stores (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    ADD CONSTRAINT fk_purchase_orders_creator
        FOREIGN KEY (creator_user_id) REFERENCES users (id)
        ON UPDATE RESTRICT ON DELETE RESTRICT,
    ADD CONSTRAINT fk_purchase_orders_buyer
        FOREIGN KEY (buyer_user_id) REFERENCES users (id)
        ON UPDATE RESTRICT ON DELETE SET NULL;
