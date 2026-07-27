-- =============================================================================
-- 义采通采购管理系统 - V2 重算商品累计采购件数
--
-- 目的：
-- 早期版本完成采购单时未同步 products.total_purchased_qty，导致选品中心
-- “历史采购”显示为 0。本迁移按所有已完成采购单的实际采购数量重新计算，
-- 之后由 PurchaseController 在完成订单事务中持续增量维护。
--
-- 幂等说明：
-- 使用覆盖赋值而非累加，重复验证计算结果不会造成累计件数翻倍。
-- =============================================================================

UPDATE products p
LEFT JOIN (
    SELECT
        product_id,
        COALESCE(SUM(actual_qty), 0) AS purchased_qty
    FROM purchase_orders
    WHERE status = 'COMPLETED'
    GROUP BY product_id
) completed ON completed.product_id = p.id
SET p.total_purchased_qty = COALESCE(completed.purchased_qty, 0);
