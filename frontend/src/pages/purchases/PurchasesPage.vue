<script setup lang="ts">
import ImageCarousel from '../../components/ImageCarousel.vue';

defineProps<{
  orders: any[];
  pendingCount: number;
  completedCount: number;
  actualAmount: number;
  actualQty: number;
  userRole: string;
  selectedDate: string;
  dateCaption: string;
}>();
defineEmits<{
  complete: [order: any];
  edit: [order: any];
  remove: [order: any];
  selectDate: [date: string];
}>();
const dateOffset = (days: number) => {
  const date = new Date();
  date.setDate(date.getDate() - days);
  return date.toISOString().slice(0, 10);
};
const isCarryover = (order: any) =>
  order.status !== 'COMPLETED' && order.createdAt.slice(0, 10) < dateOffset(0);
</script>

<template>
  <div class="date-tabs">
    <button
      :class="{ active: selectedDate === dateOffset(0) }"
      @click="$emit('selectDate', dateOffset(0))"
    >
      今天
    </button>
    <button
      :class="{ active: selectedDate === dateOffset(1) }"
      @click="$emit('selectDate', dateOffset(1))"
    >
      昨天
    </button>
    <button
      :class="{ active: selectedDate === dateOffset(2) }"
      @click="$emit('selectDate', dateOffset(2))"
    >
      前天
    </button>
    <input
      type="date"
      :value="selectedDate"
      :max="dateOffset(0)"
      @change="$emit('selectDate', ($event.target as HTMLInputElement).value)"
    />
    <span class="date-caption">正在查看：{{ dateCaption }}</span>
  </div>
  <div class="summary summary-five">
    <div>
      <span>待跑档口</span><strong>{{ pendingCount }}</strong>
    </div>
    <div>
      <span>计划件数</span
      ><strong>{{ orders.reduce((sum, order) => sum + order.planQty, 0) }}</strong>
    </div>
    <div>
      <span>实际采购件数</span><strong>{{ actualQty }}</strong>
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
  <div class="route">↗ 今日待办已按档口位置排好路线 · 每 2 秒自动刷新</div>
  <article
    v-for="(order, index) in orders"
    :key="order.id"
    class="order"
    :class="{ done: order.status === 'COMPLETED', carryover: isCarryover(order) }"
  >
    <div class="order-gallery">
      <ImageCarousel :images="order.images || []" :alt="order.productName" compact />
    </div>
    <div class="order-location">
      <span class="stop">{{
        order.status === 'COMPLETED' ? '✓ 已完成' : `第 ${index + 1} 站`
      }}</span>
      <strong>⌖ {{ order.storeLocation || '档口信息已隐藏' }}</strong>
      <small>{{ order.storeName || '供应商信息已隐藏' }}　{{ order.productName }}</small>
      <span v-if="isCarryover(order)" class="carryover-note"
        >跨天订单－创建于 {{ order.createdAt.slice(0, 10) }}</span
      >
      <div class="order-audit">
        运营：{{ order.creatorName }} · 创建 {{ order.createdAt.replace('T', ' ').slice(0, 16)
        }}<template v-if="order.buyerName"> · 买手：{{ order.buyerName }}</template
        ><template v-if="order.completedAt">
          · 完成 {{ order.completedAt.replace('T', ' ').slice(0, 16) }}</template
        >
      </div>
      <div class="remarks-row">
        <span v-if="order.operatorRemark" class="order-remark"
          >运营备注：{{ order.operatorRemark }}</span
        ><span v-if="order.buyerRemark" class="buyer-remark"
          >买手备注：{{ order.buyerRemark }}</span
        >
      </div>
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
        v-if="['ADMIN', 'OPERATOR', 'BUYER'].includes(userRole)"
        @click="$emit('edit', order)"
      >
        编辑备注
      </button>
      <button v-if="['ADMIN', 'OPERATOR'].includes(userRole)" @click="$emit('edit', order)">
        修改数量
      </button>
      <button
        v-if="['ADMIN', 'OPERATOR'].includes(userRole)"
        class="delete"
        @click="$emit('remove', order)"
      >
        删除订单
      </button>
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
