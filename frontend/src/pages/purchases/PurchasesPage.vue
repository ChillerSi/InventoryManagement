<script setup lang="ts">
defineProps<{
  orders: any[];
  pendingCount: number;
  completedCount: number;
  actualAmount: number;
  userRole: string;
}>();
defineEmits<{ complete: [order: any] }>();
</script>
<template>
  <div class="date-tabs">
    <button class="active">今天</button><button>昨天</button><button>前天</button
    ><input type="date" :value="new Date().toISOString().slice(0, 10)" /><span class="date-caption"
      >正在查看：今天</span
    >
  </div>
  <div class="summary">
    <div>
      <span>待跑档口</span><strong>{{ pendingCount }}</strong>
    </div>
    <div>
      <span>计划件数</span
      ><strong>{{ orders.reduce((sum, order) => sum + order.planQty, 0) }}</strong>
    </div>
    <div>
      <span>实际采购金额</span><strong>¥{{ actualAmount.toFixed(0) }}</strong>
    </div>
    <div>
      <span>完成进度</span
      ><strong
        >{{ orders.length ? Math.round((completedCount / orders.length) * 100) : 0 }}%</strong
      >
    </div>
  </div>
  <div class="route">↗ 今日待办已按档口位置排好路线 · 当前角色：{{ userRole }}</div>
  <article
    v-for="(order, index) in orders"
    :key="order.id"
    class="order"
    :class="{ done: order.status === 'COMPLETED' }"
  >
    <div class="order-gallery">
      <div class="thumb sage">饰</div>
      <small>1 / 1</small>
    </div>
    <div class="order-location">
      <span class="stop">{{
        order.status === 'COMPLETED' ? '✓ 已完成' : `第 ${index + 1} 站`
      }}</span
      ><strong>⌖ {{ order.storeLocation || '档口信息已隐藏' }}</strong
      ><small>{{ order.storeName || '供应商信息已隐藏' }}　{{ order.productName }}</small>
    </div>
    <div class="qty-focus" :class="{ single: order.status !== 'COMPLETED' }">
      <div>
        <span>计划采购</span><strong>{{ order.planQty }}</strong
        ><em>件</em>
      </div>
      <div v-if="order.status === 'COMPLETED'">
        <span>实际采购</span><strong>{{ order.actualQty }}</strong
        ><em>件</em>
      </div>
    </div>
    <div v-if="order.status === 'COMPLETED'" class="price-focus">
      <span>实际采购价</span><strong>¥{{ Number(order.actualPrice).toFixed(2) }}</strong>
    </div>
    <div v-else class="record-action">
      <button
        v-if="['ADMIN', 'BUYER'].includes(userRole)"
        class="complete-action"
        @click="$emit('complete', order)"
      >
        完成
      </button>
    </div>
  </article>
</template>
