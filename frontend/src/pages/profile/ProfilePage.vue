<script setup lang="ts">
import { ref } from 'vue';

const props = defineProps<{ user: any; members: any[]; canManageOwner: boolean }>();
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
    ><button v-if="canManageOwner" class="primary" @click="$emit('saveCompany', companyName)">
      保存名称
    </button>
    <p v-else class="permission-hint">仅主账号可以修改采购公司或团队名称。</p>
  </section>
  <section class="member-section">
    <div class="member-head">
      <div>
        <div class="eyebrow">账号配置</div>
        <h3>团队账号</h3>
      </div>
      <button v-if="canManageOwner" class="primary" @click="$emit('createMember')">
        ＋ 添加子账号
      </button>
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
            >{{ member.account }} · {{ member.phone || '未填写手机号' }} · {{ member.role }} ·
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
  <section class="role-guide">
    <div class="eyebrow">角色权限说明</div>
    <h3>不同角色可以使用哪些功能？</h3>
    <div class="role-grid">
      <article>
        <strong>管理员</strong>
        <p>
          维护店铺和商品档案、查看供应商信息、管理采购订单；主账号额外管理团队账号与采购主体名称。
        </p>
      </article>
      <article>
        <strong>运营</strong>
        <p>搜索选品、创建采购订单、修改计划数量和运营备注、删除未完成订单。</p>
      </article>
      <article>
        <strong>买手</strong>
        <p>查看供应商与档口、维护买手备注、填写实际数量与价格并完成采购。</p>
      </article>
      <article>
        <strong>查看者</strong>
        <p>只读查看商品、订单和历史采购记录，不能创建或修改业务数据。</p>
      </article>
    </div>
  </section>
</template>
