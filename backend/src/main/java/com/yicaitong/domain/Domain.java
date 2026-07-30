package com.yicaitong.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 集中定义当前 MVP 的持久化实体和稳定业务枚举。 */
public final class Domain {
  private Domain() {}

  /** 系统角色；权限判断必须在后端完成，不能仅依赖前端隐藏按钮。 */
  public enum Role {
    ADMIN,
    OPERATOR,
    BUYER,
    VIEWER
  }

  /** 采购单生命周期状态。 */
  public enum OrderStatus {
    PENDING,
    COMPLETED,
    CANCELLED
  }

  /** 独立采购后台，是所有业务数据的租户隔离根节点。 */
  @Entity
  @Table(name = "tenants")
  @Getter
  @Setter
  @NoArgsConstructor
  public static class Tenant {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.CHAR)
    UUID id;

    @Column(nullable = false)
    String name;

    LocalDateTime createdAt = LocalDateTime.now();
  }

  /** 主账号或子账号；loginAccount 在全局范围内唯一。 */
  @Entity
  @Table(
      name = "users",
      indexes = @Index(name = "uk_login", columnList = "loginAccount", unique = true))
  @Getter
  @Setter
  @NoArgsConstructor
  public static class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.CHAR)
    UUID id;

    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    UUID tenantId;

    @Column(nullable = false)
    String name;

    @Column(nullable = false, unique = true)
    String loginAccount;

    @Column(nullable = false)
    String password;

    String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    Domain.Role role;

    boolean active = true;
    boolean owner;
  }

  /** 服务端登录会话；Token 到期或账号删除后不可继续使用。 */
  @Entity
  @Table(name = "sessions")
  @Getter
  @Setter
  @NoArgsConstructor
  public static class Session {
    @Id String token;

    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    UUID userId;

    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    UUID tenantId;

    @Enumerated(EnumType.STRING)
    Domain.Role role;

    LocalDateTime expiresAt;
  }

  /** 供应商店铺和档口档案。 */
  @Entity
  @Table(name = "stores")
  @Getter
  @Setter
  @NoArgsConstructor
  public static class Store {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.CHAR)
    UUID id;

    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    UUID tenantId;

    @Column(nullable = false)
    String name;

    @Column(nullable = false)
    String location;

    String storefrontObjectKey;
    boolean deleted;
  }

  /** 商品结构化档案；图片和向量由独立实体及外部存储管理。 */
  @Entity
  @Table(name = "products")
  @Getter
  @Setter
  @NoArgsConstructor
  public static class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.CHAR)
    UUID id;

    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    UUID tenantId;

    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    UUID storeId;

    @Column(nullable = false)
    String name;

    @Column(nullable = false)
    BigDecimal price;

    boolean onSale = true;
    boolean deleted;
    long totalPurchasedQty;
  }

  /** 商品图片元数据；对象正文位于 MinIO，向量位于 Qdrant。 */
  @Entity
  @Table(name = "product_images")
  @Getter
  @Setter
  @NoArgsConstructor
  public static class ProductImage {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.CHAR)
    UUID id;

    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    UUID tenantId;

    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    UUID productId;

    @Column(nullable = false)
    String objectKey;

    String modelVersion;
    String vectorStatus = "PENDING";
    int sortOrder;
  }

  /** 采购任务及实采结果，同时保存关键字段快照以保证历史可读。 */
  @Entity
  @Table(name = "purchase_orders")
  @Getter
  @Setter
  @NoArgsConstructor
  public static class PurchaseOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.CHAR)
    UUID id;

    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    UUID tenantId;

    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    UUID productId;

    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    UUID storeId;

    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    UUID creatorUserId;

    @JdbcTypeCode(SqlTypes.CHAR)
    UUID buyerUserId;

    int planQty;
    boolean urgent;
    Integer actualQty;
    BigDecimal actualPrice;

    @Column(length = 1000)
    String operatorRemark;

    @Column(length = 1000)
    String buyerRemark;

    @Enumerated(EnumType.STRING)
    Domain.OrderStatus status = Domain.OrderStatus.PENDING;

    LocalDateTime createdAt = LocalDateTime.now();
    LocalDateTime completedAt;
    String productNameSnapshot;
    String storeNameSnapshot;
    String storeLocationSnapshot;
  }
}
