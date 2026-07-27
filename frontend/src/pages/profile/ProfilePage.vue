<script setup lang="ts">
import { ref } from 'vue';

const props = defineProps<{ user: any; members: any[] }>();
const companyName = ref(props.user.company);
defineEmits<{
  logout: [];
  saveCompany: [name: string];
  createMember: [];
  editMember: [member: any];
  deleteMember: [member: any];
}>();
</script>
<template>
  <section class="profile">
    <div class="avatar">{{ user.name[0] }}</div>
    <div>
      <div class="eyebrow">
        {{ user.role }} ·
        {{ members.find((member) => member.id === user.userId)?.owner ? '注册主账号' : '子账号' }}
      </div>
      <h2>{{ user.name }}</h2>
      <small>{{ user.company }}采购团队 · 今天在线</small>
    </div>
    <button class="logout-button" @click="$emit('logout')">退出登录</button>
  </section>
  <section class="setting-form">
    <div class="eyebrow">采购主体设置</div>
    <h3>采购公司或团队名称</h3>
    <label>当前名称<input v-model="companyName" /></label
    ><button class="primary" @click="$emit('saveCompany', companyName)">保存名称</button>
  </section>
  <section class="member-section">
    <div class="member-head">
      <div>
        <div class="eyebrow">账号配置</div>
        <h3>团队账号</h3>
      </div>
      <button class="primary" @click="$emit('createMember')">＋ 添加子账号</button>
    </div>
    <div class="tenant-notice">
      主账号可以修改自己的密码，并维护当前采购后台的全部子账号。登录账号在系统中全局唯一。
    </div>
    <div class="member-list">
      <div v-for="member in members" :key="member.id" class="member-row">
        <div class="member-avatar">{{ member.name[0] }}</div>
        <div class="member-info">
          <strong
            >{{ member.name }}
            <span class="account-type">{{ member.owner ? '主账号' : '子账号' }}</span></strong
          ><small
            >{{ member.account }} · {{ member.role }} ·
            {{ member.active ? '已启用' : '已停用' }}</small
          >
        </div>
        <div class="member-actions">
          <button @click="$emit('editMember', member)">修改资料/密码</button
          ><button v-if="!member.owner" class="delete" @click="$emit('deleteMember', member)">
            删除
          </button>
        </div>
      </div>
    </div>
  </section>
</template>
