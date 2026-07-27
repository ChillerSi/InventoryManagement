<script setup lang="ts">
defineProps<{ mode: 'login' | 'register'; form: any }>();
defineEmits<{ switch: [mode: 'login' | 'register']; submit: [] }>();
</script>
<template>
  <div class="auth-screen">
    <section class="auth-card">
      <div class="eyebrow">YIWU PROCUREMENT</div>
      <h1>义采通</h1>
      <p class="auth-intro">让每一次选品、采购与商品沉淀都有迹可循。</p>
      <div class="auth-tabs">
        <button :class="{ active: mode === 'login' }" @click="$emit('switch', 'login')">登录</button
        ><button :class="{ active: mode === 'register' }" @click="$emit('switch', 'register')">
          注册
        </button>
      </div>
      <label v-if="mode === 'register'">采购公司或团队名称<input v-model="form.company" /></label>
      <label v-if="mode === 'register'">用户姓名<input v-model="form.name" /></label>
      <label>登录账号<input v-model="form.account" /></label
      ><label>登录密码<input v-model="form.password" type="password" /></label>
      <button class="auth-primary" @click="$emit('submit')">
        {{ mode === 'login' ? '进入采购后台' : '创建采购后台' }}
      </button>
      <div class="support-contact">义乌采购团队数字化工作台</div>
    </section>
  </div>
</template>
