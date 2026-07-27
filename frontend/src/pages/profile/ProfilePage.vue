<script setup lang="ts">
defineProps<{ user: any }>();
defineEmits<{ logout: [] }>();
</script>
<template>
  <section class="profile">
    <div class="avatar">{{ user.name[0] }}</div>
    <div>
      <div class="eyebrow">{{ user.role }} · 当前账号</div>
      <h2>{{ user.name }}</h2>
      <small>{{ user.company }}采购团队 · 今天在线</small>
    </div>
    <button class="logout-button" @click="$emit('logout')">退出登录</button>
  </section>
  <section class="setting-form">
    <div class="eyebrow">采购主体设置</div>
    <h3>采购公司或团队名称</h3>
    <label>当前名称<input :value="user.company" disabled /></label>
    <p class="permission-hint">当前登录角色：{{ user.role }}</p>
  </section>
  <div class="archive permission-grid">
    <article>
      <h3>管理员</h3>
      <p>全部权限：档案、订单、团队和采购设置</p>
    </article>
    <article>
      <h3>运营</h3>
      <p>选品下单；维护未完成采购任务</p>
    </article>
    <article>
      <h3>买手</h3>
      <p>现场完成采购，填写实采数量和单价</p>
    </article>
    <article>
      <h3>查看者</h3>
      <p>只读查看商品、订单及历史记录</p>
    </article>
  </div>
</template>
