package com.yicaitong.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.*;

public final class Domain {
  private Domain() {}

  public enum Role {
    ADMIN,
    OPERATOR,
    BUYER,
    VIEWER
  }

  public enum OrderStatus {
    PENDING,
    COMPLETED,
    CANCELLED
  }

  @Entity
  @Table(name = "tenants")
  @Getter
  @Setter
  @NoArgsConstructor
  public static class Tenant {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @Column(nullable = false)
    String name;

    LocalDateTime createdAt = LocalDateTime.now();
  }

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
    UUID id;

    @Column(nullable = false)
    UUID tenantId;

    @Column(nullable = false)
    String name;

    @Column(nullable = false, unique = true)
    String loginAccount;

    @Column(nullable = false)
    String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    Domain.Role role;

    boolean active = true;
    boolean owner;
  }

  @Entity
  @Table(name = "sessions")
  @Getter
  @Setter
  @NoArgsConstructor
  public static class Session {
    @Id String token;

    @Column(nullable = false)
    UUID userId;

    @Column(nullable = false)
    UUID tenantId;

    @Enumerated(EnumType.STRING)
    Domain.Role role;

    LocalDateTime expiresAt;
  }

  @Entity
  @Table(name = "stores")
  @Getter
  @Setter
  @NoArgsConstructor
  public static class Store {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @Column(nullable = false)
    UUID tenantId;

    @Column(nullable = false)
    String name;

    @Column(nullable = false)
    String location;

    String storefrontObjectKey;
    boolean deleted;
  }

  @Entity
  @Table(name = "products")
  @Getter
  @Setter
  @NoArgsConstructor
  public static class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @Column(nullable = false)
    UUID tenantId;

    @Column(nullable = false)
    UUID storeId;

    @Column(nullable = false)
    String name;

    @Column(nullable = false)
    BigDecimal price;

    boolean onSale = true;
    boolean deleted;
    long totalPurchasedQty;
  }

  @Entity
  @Table(name = "product_images")
  @Getter
  @Setter
  @NoArgsConstructor
  public static class ProductImage {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @Column(nullable = false)
    UUID tenantId;

    @Column(nullable = false)
    UUID productId;

    @Column(nullable = false)
    String objectKey;

    String modelVersion;
    String vectorStatus = "PENDING";
    int sortOrder;
  }

  @Entity
  @Table(name = "purchase_orders")
  @Getter
  @Setter
  @NoArgsConstructor
  public static class PurchaseOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @Column(nullable = false)
    UUID tenantId;

    @Column(nullable = false)
    UUID productId;

    @Column(nullable = false)
    UUID storeId;

    @Column(nullable = false)
    UUID creatorUserId;

    UUID buyerUserId;
    int planQty;
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
