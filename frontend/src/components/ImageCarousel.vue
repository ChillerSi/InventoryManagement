<script setup lang="ts">
import { ref, watch } from 'vue';

const props = withDefaults(defineProps<{ images: string[]; alt?: string; compact?: boolean }>(), {
  alt: '商品图片',
  compact: false,
});
const index = ref(0);
const lightbox = ref(false);
watch(
  () => props.images,
  () => (index.value = 0),
);
function move(step: number) {
  const count = props.images.length || 1;
  index.value = (index.value + step + count) % count;
}
</script>

<template>
  <div class="carousel" :class="{ compact }">
    <img
      v-if="images.length"
      class="product-photo"
      :src="images[index]"
      :alt="alt"
      @click="lightbox = true"
    />
    <div v-else class="art coral">饰</div>
    <button v-if="images.length > 1" class="arrow prev" @click.stop="move(-1)">‹</button>
    <button v-if="images.length > 1" class="arrow next" @click.stop="move(1)">›</button>
    <span class="counter">{{ images.length ? index + 1 : 1 }} / {{ images.length || 1 }}</span>
    <div class="dots">
      <i
        v-for="(_, dotIndex) in images.length || 1"
        :key="dotIndex"
        :class="{ active: dotIndex === index }"
      ></i>
    </div>
  </div>
  <div v-if="lightbox" class="lightbox show" @click.self="lightbox = false">
    <button class="lightbox-close" @click="lightbox = false">×</button>
    <img v-if="images.length" :src="images[index]" :alt="alt" />
    <div class="lightbox-controls">
      <button v-if="images.length > 1" @click="move(-1)">‹</button>
      <span>{{ index + 1 }} / {{ images.length }}</span>
      <button v-if="images.length > 1" @click="move(1)">›</button>
    </div>
  </div>
</template>
