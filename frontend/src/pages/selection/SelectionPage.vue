<script setup lang="ts">
import ImageCarousel from '../../components/ImageCarousel.vue';
defineProps<{
  products: any[];
  query: string;
  appliedQuery: string;
  userRole: string;
  company: string;
  searchPreview: string;
}>();
defineEmits<{
  search: [];
  queryChange: [value: string];
  imageSearch: [event: Event];
  buy: [product: any];
  preview: [];
  clearImage: [];
}>();
</script>
<template>
  <div class="search">
    <input
      :value="query"
      placeholder="搜商品、店铺或档口位置"
      @input="$emit('queryChange', ($event.target as HTMLInputElement).value)"
      @keyup.enter="$emit('search')"
    /><button @click="$emit('search')">搜索</button
    ><label class="image-search"
      >▣ 以图搜图<input hidden type="file" accept="image/*" @change="$emit('imageSearch', $event)"
    /></label>
  </div>
  <div v-if="searchPreview" class="image-search-result">
    <img :src="searchPreview" alt="以图搜图上传图片" @click="$emit('preview')" />
    <span>已上传搜索图片，点击缩略图可放大查看</span>
    <button @click="$emit('clearImage')">清除</button>
  </div>
  <div class="heading">
    <div>
      <div class="eyebrow">{{ company }}采购</div>
      <h2>{{ appliedQuery ? `找到 ${products.length} 个相关商品` : '历史热采' }}</h2>
    </div>
    <span>已下架商品不会展示 · 相似检索 Top 20</span>
  </div>
  <div class="grid">
    <article v-for="product in products" :key="product.id" class="card">
      <ImageCarousel :images="product.images" :alt="product.name" auto />
      <div class="body">
        <div class="loc">
          <span class="map-pin">⌖</span> {{ product.storeLocation || '档口信息已隐藏' }}
        </div>
        <h3>{{ product.name }}</h3>
        <p>
          {{ product.storeName || '供应商信息已隐藏' }} · 历史采购
          {{ product.totalPurchasedQty }} 件
        </p>
        <div class="photo-hint">商品图片可左右切换查看</div>
        <div class="foot">
          <strong>¥{{ Number(product.price).toFixed(2) }}</strong
          ><button v-if="['ADMIN', 'OPERATOR'].includes(userRole)" @click="$emit('buy', product)">
            加入今日采购
          </button>
        </div>
      </div>
    </article>
  </div>
</template>
