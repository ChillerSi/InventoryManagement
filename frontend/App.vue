<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue';
type User = {
  token: string;
  userId: string;
  name: string;
  company: string;
  role: 'ADMIN' | 'OPERATOR' | 'BUYER' | 'VIEWER';
};
type Product = {
  id: string;
  storeId: string;
  name: string;
  price: number;
  onSale: boolean;
  totalPurchasedQty: number;
  storeName?: string;
  storeLocation?: string;
  images: string[];
};
type Store = {
  id: string;
  name: string;
  location: string;
  storefrontUrl?: string;
};
type SelectionBox = { x: number; y: number; w: number; h: number };
const user = ref<User | null>(JSON.parse(localStorage.getItem('auth') || 'null')),
  tab = ref('goods'),
  products = ref<Product[]>([]),
  orders = ref<any[]>([]),
  stores = ref<Store[]>([]),
  q = ref(''),
  message = ref('');
const authMode = ref<'login' | 'register'>('login'),
  auth = ref({ company: '', name: '', account: '', password: '' });
const api = async (path: string, options: any = {}) => {
  const r = await fetch('/api' + path, {
    ...options,
    headers: {
      ...(options.body instanceof FormData ? {} : { 'Content-Type': 'application/json' }),
      ...(user.value ? { Authorization: `Bearer ${user.value.token}` } : {}),
    },
  });
  if (!r.ok) throw new Error((await r.json()).message);
  return r.status === 204 ? null : r.json();
};
const notify = (s: string) => {
  message.value = s;
  setTimeout(() => (message.value = ''), 2600);
};
const logout = () => {
  window.localStorage.clear();
  window.location.reload();
};
async function submitAuth() {
  try {
    user.value = await api('/auth/' + authMode.value, {
      method: 'POST',
      body: JSON.stringify(auth.value),
    });
    localStorage.setItem('auth', JSON.stringify(user.value));
    await load();
  } catch (e: any) {
    notify(e.message);
  }
}
async function load() {
  if (!user.value) return;
  products.value = await api('/products?q=' + encodeURIComponent(q.value));
  orders.value = await api('/purchase-orders');
  if (['ADMIN', 'BUYER'].includes(user.value.role)) stores.value = await api('/stores');
}
async function buy(p: Product) {
  const qty = Number(prompt('计划采购数量', '10'));
  if (!qty) return;
  await api('/purchase-orders', {
    method: 'POST',
    body: JSON.stringify({
      productId: p.id,
      planQty: qty,
      operatorRemark: prompt('运营备注（可选）', ''),
    }),
  });
  notify('已加入今日采购');
  load();
}
async function complete(o: any) {
  const actualQty = Number(prompt('实际采购数量', o.planQty));
  const actualPrice = Number(prompt('实际采购单价', ''));
  if (!actualQty || !actualPrice) return;
  await api(`/purchase-orders/${o.id}/complete`, {
    method: 'POST',
    body: JSON.stringify({ actualQty, actualPrice, buyerRemark: prompt('买手备注（可选）', '') }),
  });
  load();
}
async function createStore() {
  const name = prompt('店铺名称'),
    location = prompt('档口位置');
  if (name && location) {
    await api('/stores', { method: 'POST', body: JSON.stringify({ name, location }) });
    load();
  }
}
async function createProduct() {
  if (!stores.value.length) return notify('请先创建店铺');
  const name = prompt('商品名称'),
    price = Number(prompt('参考价格')),
    storeId = stores.value[0].id;
  if (name && price) {
    await api('/products', {
      method: 'POST',
      body: JSON.stringify({ name, price, storeId, onSale: true }),
    });
    tab.value = 'archive';
    load();
  }
}
async function uploadStorefront(event: Event, store: Store) {
  const file = (event.target as HTMLInputElement).files?.[0];
  if (!file) return;
  const form = new FormData();
  form.append('file', file);
  try {
    const updated = await api(`/images/stores/${store.id}/storefront`, {
      method: 'POST',
      body: form,
    });
    stores.value = stores.value.map((item) => (item.id === store.id ? updated : item));
    notify('店铺门头图片已更新');
  } catch (error: any) {
    notify(error.message);
  } finally {
    (event.target as HTMLInputElement).value = '';
  }
}
const cropOpen = ref(false),
  cropSrc = ref(''),
  cropFile = ref<File>(),
  cropMode = ref<'search' | 'upload'>('search'),
  cropProduct = ref<Product>(),
  canvas = ref<HTMLCanvasElement>(),
  start = ref({ x: 0, y: 0 }),
  box = ref<SelectionBox>({ x: 20, y: 20, w: 180, h: 180 }),
  boxes = ref<SelectionBox[]>([]),
  imageRect = ref({ x: 0, y: 0, w: 0, h: 0, naturalW: 0, naturalH: 0 });
function chooseImage(e: Event, mode: 'search' | 'upload', p?: Product) {
  const f = (e.target as HTMLInputElement).files?.[0];
  if (!f) return;
  cropFile.value = f;
  cropMode.value = mode;
  cropProduct.value = p;
  boxes.value = [];
  box.value = { x: 20, y: 20, w: 180, h: 180 };
  cropSrc.value = URL.createObjectURL(f);
  cropOpen.value = true;
  nextTick(draw);
}
function draw() {
  const c = canvas.value!,
    img = new Image();
  img.onload = () => {
    c.width = 600;
    c.height = 420;
    const scale = Math.min(c.width / img.width, c.height / img.height),
      w = img.width * scale,
      h = img.height * scale,
      x = (c.width - w) / 2,
      y = (c.height - h) / 2,
      context = c.getContext('2d')!;
    imageRect.value = { x, y, w, h, naturalW: img.width, naturalH: img.height };
    context.drawImage(img, x, y, w, h);
    context.lineWidth = 2;
    context.font = '14px sans-serif';
    boxes.value.forEach((selected, index) => {
      context.strokeStyle = '#24a148';
      context.strokeRect(selected.x, selected.y, selected.w, selected.h);
      context.fillStyle = '#24a148';
      context.fillText(String(index + 1), selected.x + 5, selected.y + 18);
    });
    context.strokeStyle = '#ff6b35';
    context.strokeRect(box.value.x, box.value.y, box.value.w, box.value.h);
  };
  img.src = cropSrc.value;
}
function down(e: MouseEvent) {
  start.value = { x: e.offsetX, y: e.offsetY };
  box.value = { x: e.offsetX, y: e.offsetY, w: 0, h: 0 };
}
function move(e: MouseEvent) {
  if (!(e.buttons & 1)) return;
  box.value.w = e.offsetX - start.value.x;
  box.value.h = e.offsetY - start.value.y;
  draw();
}
function finishBox() {
  if (cropMode.value !== 'upload') return;
  const normalized = normalizeBox(box.value);
  if (normalized.w >= 8 && normalized.h >= 8 && boxes.value.length < 20) {
    boxes.value.push(normalized);
    box.value = { x: 0, y: 0, w: 0, h: 0 };
    draw();
  }
}
function normalizeBox(value: SelectionBox): SelectionBox {
  const rect = imageRect.value,
    left = Math.max(rect.x, Math.min(value.x, value.x + value.w)),
    top = Math.max(rect.y, Math.min(value.y, value.y + value.h)),
    right = Math.min(rect.x + rect.w, Math.max(value.x, value.x + value.w)),
    bottom = Math.min(rect.y + rect.h, Math.max(value.y, value.y + value.h));
  return { x: left, y: top, w: Math.max(0, right - left), h: Math.max(0, bottom - top) };
}
function removeBox(index: number) {
  boxes.value.splice(index, 1);
  draw();
}
async function cropSubmit() {
  if (cropMode.value === 'upload') {
    if (!boxes.value.length) return notify('请至少框选一个商品区域');
    const rect = imageRect.value,
      regions = boxes.value.map((selected) => {
        const x = Math.round(((selected.x - rect.x) / rect.w) * rect.naturalW),
          y = Math.round(((selected.y - rect.y) / rect.h) * rect.naturalH),
          right = Math.round(((selected.x + selected.w - rect.x) / rect.w) * rect.naturalW),
          bottom = Math.round(((selected.y + selected.h - rect.y) / rect.h) * rect.naturalH);
        return { x, y, width: right - x, height: bottom - y };
      }),
      fd = new FormData();
    fd.append('file', cropFile.value!);
    fd.append('regions', JSON.stringify(regions));
    try {
      await api('/images/products/' + cropProduct.value!.id + '/regions', {
        method: 'POST',
        body: fd,
      });
      await load();
      cropOpen.value = false;
      notify(`已完成 ${regions.length} 个区域的向量化`);
    } catch (error: any) {
      notify(error.message);
    }
    return;
  }
  const c = canvas.value!,
    b = box.value,
    x = Math.min(b.x, b.x + b.w),
    y = Math.min(b.y, b.y + b.h),
    w = Math.abs(b.w),
    h = Math.abs(b.h);
  const out = document.createElement('canvas');
  out.width = w;
  out.height = h;
  out.getContext('2d')!.drawImage(c, x, y, w, h, 0, 0, w, h);
  out.toBlob(
    async (blob) => {
      if (!blob) return;
      const fd = new FormData();
      fd.append('file', blob, 'crop.jpg');
      if (cropMode.value === 'search') {
        const hits = await api('/images/search', { method: 'POST', body: fd });
        products.value = hits.map((x: any) => ({ ...x.product, similarity: x.similarity }));
        tab.value = 'goods';
      } else {
        await api('/images/products/' + cropProduct.value!.id, { method: 'POST', body: fd });
        await load();
      }
      cropOpen.value = false;
      notify('图片处理完成');
    },
    'image/jpeg',
    0.9,
  );
}
const visibleProducts = computed(() =>
  products.value.filter((p) =>
    (p.name + ' ' + (p.storeName || '') + ' ' + (p.storeLocation || '')).includes(q.value),
  ),
);
onMounted(load);
</script>
<template>
  <div v-if="!user" class="auth">
    <section>
      <div class="eyebrow">YIWU PROCUREMENT</div>
      <h1>义采通</h1>
      <p>让每一次选品、采购与商品沉淀都有迹可循。</p>
      <div class="switch">
        <button @click="authMode = 'login'" :class="{ active: authMode === 'login' }">登录</button
        ><button @click="authMode = 'register'" :class="{ active: authMode === 'register' }">
          注册
        </button>
      </div>
      <input
        v-if="authMode === 'register'"
        v-model="auth.company"
        placeholder="采购公司或团队名称"
      /><input v-if="authMode === 'register'" v-model="auth.name" placeholder="姓名" /><input
        v-model="auth.account"
        placeholder="登录账号"
      /><input v-model="auth.password" type="password" placeholder="登录密码（至少 6 位）" /><button
        class="primary"
        @click="submitAuth"
      >
        {{ authMode === 'login' ? '进入采购后台' : '创建采购后台' }}
      </button>
    </section>
  </div>
  <div v-else class="app">
    <header>
      <div>
        <div class="eyebrow">{{ user.company }}采购</div>
        <h1>
          {{
            tab === 'goods'
              ? '选品中心'
              : tab === 'orders'
                ? '今日采购'
                : tab === 'archive'
                  ? '档案管理'
                  : '我的'
          }}
        </h1>
      </div>
      <div class="avatar">{{ user.name[0] }}</div>
    </header>
    <main>
      <template v-if="tab === 'goods'"
        ><div class="search">
          <input v-model="q" @keyup.enter="load" placeholder="搜索商品、店铺或档口位置" /><button
            @click="load"
          >
            搜索</button
          ><label
            >以图搜图<input
              hidden
              type="file"
              accept="image/*"
              @change="chooseImage($event, 'search')"
          /></label>
        </div>
        <div class="heading">
          <h2>历史热采</h2>
          <span>相似检索最多返回 Top 20</span>
        </div>
        <div class="grid">
          <article v-for="p in visibleProducts" :key="p.id">
            <div class="photo">
              <img v-if="p.images[0]" :src="p.images[0]" /><span v-else>饰</span>
            </div>
            <div class="info">
              <small>{{ p.storeLocation || '档口信息已隐藏' }}</small>
              <h3>{{ p.name }}</h3>
              <p>{{ p.storeName || '供应商信息已隐藏' }} · ¥{{ p.price }}</p>
              <button v-if="['ADMIN', 'OPERATOR'].includes(user.role)" @click="buy(p)">
                加入采购
              </button>
            </div>
          </article>
        </div></template
      >
      <template v-if="tab === 'orders'"
        ><div class="heading">
          <h2>采购任务</h2>
          <span>{{ new Date().toLocaleDateString() }}</span>
        </div>
        <div class="orders">
          <article v-for="o in orders" :key="o.id">
            <div>
              <small>📍 {{ o.storeLocation || '档口信息已隐藏' }}</small>
              <h3>{{ o.productName }}</h3>
              <p>{{ o.storeName || '供应商信息已隐藏' }}</p>
            </div>
            <strong>计划 {{ o.planQty }} 件</strong>
            <div>
              <span :class="o.status">{{ o.status === 'COMPLETED' ? '已完成' : '待采购' }}</span
              ><button
                v-if="o.status === 'PENDING' && ['ADMIN', 'BUYER'].includes(user.role)"
                @click="complete(o)"
              >
                完成采购
              </button>
              <p v-if="o.actualQty">实采 {{ o.actualQty }} · ¥{{ o.actualPrice }}</p>
            </div>
          </article>
        </div></template
      >
      <template v-if="tab === 'archive'"
        ><div class="heading">
          <h2>商品与店铺档案</h2>
          <div>
            <button v-if="user.role === 'ADMIN'" @click="createStore">新增店铺</button>
            <button v-if="user.role === 'ADMIN'" @click="createProduct">新增商品</button>
          </div>
        </div>
        <div class="stores">
          <article v-for="s in stores" :key="s.id" class="store-card">
            <div class="storefront">
              <img v-if="s.storefrontUrl" :src="s.storefrontUrl" :alt="`${s.name}门头图片`" />
              <span v-else>暂无门头图</span>
            </div>
            <div>
              <strong>{{ s.name }}</strong>
              <small>{{ s.location }}</small>
              <label v-if="user.role === 'ADMIN'"
                >{{ s.storefrontUrl ? '替换门头图片' : '上传门头图片'
                }}<input
                  hidden
                  type="file"
                  accept="image/jpeg,image/png,image/webp"
                  @change="uploadStorefront($event, s)"
              /></label>
            </div>
          </article>
        </div>
        <div class="grid">
          <article v-for="p in products" :key="p.id">
            <div class="photo">
              <img v-if="p.images[0]" :src="p.images[0]" /><span v-else>图</span>
            </div>
            <div class="info">
              <small>{{ p.storeLocation }}</small>
              <h3>{{ p.name }}</h3>
              <p>¥{{ p.price }} · {{ p.onSale ? '上架' : '下架' }}</p>
              <label v-if="user.role === 'ADMIN'"
                >上传并框选商品<input
                  hidden
                  type="file"
                  accept="image/*"
                  @change="chooseImage($event, 'upload', p)"
              /></label>
            </div>
          </article></div
      ></template>
      <template v-if="tab === 'mine'"
        ><section class="profile">
          <div class="avatar">{{ user.name[0] }}</div>
          <div>
            <div class="eyebrow">{{ user.role }}</div>
            <h2>{{ user.name }}</h2>
            <p>{{ user.company }} · {{ auth.account }}</p>
          </div>
          <button @click="logout">退出登录</button>
        </section>
        <div class="permission">
          <h3>当前权限</h3>
          <p>管理员维护档案和团队；运营选品下单；买手执行采购；查看者只读浏览。</p>
        </div></template
      >
    </main>
    <nav>
      <button
        @click="
          tab = 'goods';
          load();
        "
        :class="{ active: tab === 'goods' }"
      >
        选品中心</button
      ><button
        @click="
          tab = 'orders';
          load();
        "
        :class="{ active: tab === 'orders' }"
      >
        今日采购</button
      ><button
        v-if="['ADMIN', 'BUYER'].includes(user.role)"
        @click="
          tab = 'archive';
          load();
        "
        :class="{ active: tab === 'archive' }"
      >
        档案管理</button
      ><button @click="tab = 'mine'" :class="{ active: tab === 'mine' }">我的</button>
    </nav>
  </div>
  <div v-if="cropOpen" class="modal">
    <section>
      <h2>{{ cropMode === 'upload' ? '框选多个商品区域' : '框选要搜索的商品' }}</h2>
      <p>
        {{
          cropMode === 'upload'
            ? '每次拖拽添加一个框，最多 20 个；后端将按原图像素坐标分别裁剪并建模。'
            : '按住鼠标拖拽矩形，只使用框选区域进行相似图片检索。'
        }}
      </p>
      <canvas ref="canvas" @mousedown="down" @mousemove="move" @mouseup="finishBox"></canvas>
      <div v-if="cropMode === 'upload' && boxes.length">
        <button v-for="(_, index) in boxes" :key="index" @click="removeBox(index)">
          删除区域 {{ index + 1 }}
        </button>
      </div>
      <div>
        <button @click="cropOpen = false">取消</button
        ><button class="primary" @click="cropSubmit">
          {{ cropMode === 'upload' ? `确认 ${boxes.length} 个区域` : '确认框选' }}
        </button>
      </div>
    </section>
  </div>
  <div v-if="message" class="toast">{{ message }}</div>
</template>
