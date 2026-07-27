<script setup lang="ts">
defineProps<{ stores: any[]; products: any[]; userRole: string }>();
defineEmits<{
  createStore: [];
  createProduct: [];
  storefront: [event: Event, store: any];
  productImage: [event: Event, product: any];
}>();
</script>
<template>
  <div class="heading">
    <div>
      <div class="eyebrow">供应商档案</div>
      <h2>供应商店铺</h2>
      <span>先建立店铺，再进入店铺维护商品</span>
    </div>
    <div class="heading-actions">
      <button v-if="userRole === 'ADMIN'" class="primary" @click="$emit('createStore')">
        ＋ 新建店铺</button
      ><button v-if="userRole === 'ADMIN'" class="primary" @click="$emit('createProduct')">
        ＋ 新建商品
      </button>
    </div>
  </div>
  <section class="archive-group">
    <div class="group-head">
      <h3>⌖ 全部档口</h3>
      <span>{{ stores.length }} 家店铺</span>
    </div>
    <div class="archive-list">
      <article v-for="store in stores" :key="store.id" class="supplier-record">
        <div class="storefront">
          <img v-if="store.storefrontUrl" :src="store.storefrontUrl" /><span v-else>店</span>
        </div>
        <div class="record-info">
          <div class="loc">{{ store.location }}</div>
          <h3>{{ store.name }}</h3>
          <p>
            已归档 {{ products.filter((product) => product.storeId === store.id).length }} 款商品
          </p>
        </div>
        <div class="record-action">
          <label v-if="userRole === 'ADMIN'" class="ghost"
            >{{ store.storefrontUrl ? '替换门头' : '上传门头'
            }}<input
              hidden
              type="file"
              accept="image/*"
              @change="$emit('storefront', $event, store)"
          /></label>
        </div>
      </article>
    </div>
  </section>
  <section class="archive-group">
    <div class="group-head">
      <h3>商品档案</h3>
      <span>{{ products.length }} 款商品</span>
    </div>
    <div class="archive-list">
      <article v-for="product in products" :key="product.id" class="archive-record">
        <div class="storefront product-thumb">
          <img v-if="product.images[0]" :src="product.images[0]" /><span v-else>饰</span>
        </div>
        <div class="record-info">
          <div class="loc">{{ product.storeLocation }}</div>
          <h3>{{ product.name }}</h3>
          <div class="record-meta">
            <span
              >价格<strong>¥{{ product.price }}</strong></span
            ><span
              >状态<strong>{{ product.onSale ? '上架' : '下架' }}</strong></span
            ><span
              >图片<strong>{{ product.images.length }} 张</strong></span
            >
          </div>
        </div>
        <div class="record-action">
          <label v-if="userRole === 'ADMIN'" class="ghost"
            >上传并框选商品<input
              hidden
              type="file"
              accept="image/*"
              @change="$emit('productImage', $event, product)"
          /></label>
        </div>
      </article>
    </div>
  </section>
</template>
