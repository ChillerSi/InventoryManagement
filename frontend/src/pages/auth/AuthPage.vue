<script setup lang="ts">
defineProps<{ mode: 'login' | 'register'; form: any }>();
defineEmits<{ switch: [mode: 'login' | 'register']; submit: [] }>();
</script>
<template>
  <div class="auth-screen">
    <section class="auth-card">
      <div class="eyebrow">PROCUREMENT</div>
      <h1>欢迎使用采购后台</h1>
      <p class="auth-intro">首次使用请注册主账号；已有主账号或子账户可直接登录。</p>
      <div class="auth-tabs">
        <button :class="{ active: mode === 'login' }" @click="$emit('switch', 'login')">登录</button
        ><button :class="{ active: mode === 'register' }" @click="$emit('switch', 'register')">
          注册
        </button>
      </div>
      <label v-if="mode === 'register'">采购公司或团队名称<input v-model="form.company" /></label>
      <label v-if="mode === 'register'">用户姓名<input v-model="form.name" /></label>
      <label v-if="mode === 'register'"
        >手机号<input v-model="form.phone" inputmode="numeric" maxlength="11"
      /></label>
      <label>登录账号<input v-model="form.account" /></label
      ><label>登录密码<input v-model="form.password" type="password" /></label>
      <label v-if="mode === 'register'"
        >确认密码<input v-model="form.confirmPassword" type="password"
      /></label>
      <button class="auth-primary" @click="$emit('submit')">
        {{ mode === 'login' ? '进入采购后台' : '创建采购后台' }}
      </button>
      <div class="support-contact">采购团队数字化工作台</div>
    </section>
  </div>
</template>
