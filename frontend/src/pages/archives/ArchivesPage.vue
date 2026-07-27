<script setup lang="ts">
import { ref } from 'vue';
import ImageCarousel from '../../components/ImageCarousel.vue';

defineProps<{ stores: any[]; products: any[]; userRole: string }>();
defineEmits<{
  createStore: [];
  createProduct: [store: any];
  editStore: [store: any];
  deleteStore: [store: any];
  storefront: [event: Event, store: any];
  productImage: [event: Event, product: any];
  editProduct: [product: any];
  toggleProduct: [product: any];
  deleteProduct: [product: any];
}>();
const selectedStore = ref<any>();
</script>

<template>
  <template v-if="!selectedStore">
    <div class="heading">
      <div>
        <div class="eyebrow">供应商档案</div>
        <h2>供应商店铺</h2>
        <span>先建立店铺，再进入店铺维护商品</span>
      </div>
      <button v-if="userRole === 'ADMIN'" class="primary" @click="$emit('createStore')">
        ＋ 新建店铺
      </button>
    </div>
    <section class="archive-group">
      <div class="group-head">
        <h3>⌖ 全部档口</h3>
        <span>{{ stores.length }} 家店铺</span>
      </div>
      <div class="archive-list">
        <article v-for="store in stores" :key="store.id" class="supplier-record">
          <div
            class="storefront store-photo"
            @click="store.storefrontUrl && (selectedStore = { ...store, previewOnly: true })"
          >
            <img v-if="store.storefrontUrl" :src="store.storefrontUrl" /><span v-else>店</span>
          </div>
          <div class="record-info">
            <div class="loc">{{ store.location }}</div>
            <h3>{{ store.name }}</h3>
            <p>
              已归档 {{ products.filter((product) => product.storeId === store.id).length }} 款商品
              · 门头照片 {{ store.storefrontUrl ? 1 : 0 }} 张
            </p>
          </div>
          <div class="record-action">
            <button @click="selectedStore = store">进入商品列表</button
            ><button v-if="userRole === 'ADMIN'" @click="$emit('editStore', store)">编辑店铺</button
            ><button
              v-if="userRole === 'ADMIN'"
              class="delete"
              @click="$emit('deleteStore', store)"
            >
              删除店铺
            </button>
          </div>
        </article>
      </div>
    </section>
  </template>

  <template v-else-if="selectedStore.previewOnly">
    <button class="back" @click="selectedStore = undefined">← 返回供应商店铺</button>
    <div class="standalone-preview">
      <img :src="selectedStore.storefrontUrl" :alt="selectedStore.name" />
      <h2>{{ selectedStore.name }} · 门头图片</h2>
    </div>
  </template>

  <template v-else>
    <button class="back" @click="selectedStore = undefined">← 返回供应商店铺</button>
    <div class="heading">
      <div>
        <div class="eyebrow">{{ selectedStore.location }}</div>
        <h2>{{ selectedStore.name }} · 商品档案</h2>
        <span>维护价格、图片和上下架状态</span>
      </div>
      <button
        v-if="userRole === 'ADMIN'"
        class="primary"
        @click="$emit('createProduct', selectedStore)"
      >
        ＋ 添加商品
      </button>
    </div>
    <div class="archive-list">
      <article
        v-for="product in products.filter((item) => item.storeId === selectedStore.id)"
        :key="product.id"
        class="archive-record product-record"
      >
        <div class="archive-carousel">
          <ImageCarousel :images="product.images" :alt="product.name" compact />
        </div>
        <div class="record-info">
          <div class="loc">{{ product.storeLocation }} · {{ product.storeName }}</div>
          <h3>{{ product.name }}</h3>
          <div class="record-meta">
            <span
              >价格<strong>¥{{ product.price }}</strong></span
            ><span
              >累计采购<strong>{{ product.totalPurchasedQty }} 件</strong></span
            ><span
              >图片<strong>{{ product.images.length }} 张</strong></span
            ><span class="status" :class="{ off: !product.onSale }">{{
              product.onSale ? '上架' : '下架'
            }}</span>
          </div>
        </div>
        <div class="record-action">
          <button v-if="userRole === 'ADMIN'" @click="$emit('editProduct', product)">编辑</button
          ><button v-if="userRole === 'ADMIN'" @click="$emit('toggleProduct', product)">
            {{ product.onSale ? '下架' : '上架' }}</button
          ><button
            v-if="userRole === 'ADMIN'"
            class="delete"
            @click="$emit('deleteProduct', product)"
          >
            删除</button
          ><label v-if="userRole === 'ADMIN'" class="ghost"
            >添加图片<input
              hidden
              type="file"
              multiple
              accept="image/*"
              @change="$emit('productImage', $event, product)"
          /></label>
        </div>
      </article>
    </div>
  </template>
</template>
