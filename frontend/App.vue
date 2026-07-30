<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import ArchivesPage from './src/pages/archives/ArchivesPage.vue';
import AuthPage from './src/pages/auth/AuthPage.vue';
import ProfilePage from './src/pages/profile/ProfilePage.vue';
import PurchasesPage from './src/pages/purchases/PurchasesPage.vue';
import SelectionPage from './src/pages/selection/SelectionPage.vue';
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
const user = ref<User>(JSON.parse(localStorage.getItem('auth') || 'null') as User),
  tab = ref('goods'),
  products = ref<Product[]>([]),
  archiveProducts = ref<Product[]>([]),
  orders = ref<any[]>([]),
  stores = ref<Store[]>([]),
  members = ref<any[]>([]),
  q = ref(''),
  appliedQ = ref(''),
  searchPreview = ref(''),
  previewOpen = ref(false),
  selectedDate = ref(new Date().toISOString().slice(0, 10)),
  message = ref('');
const authMode = ref<'login' | 'register'>('login'),
  auth = ref({
    company: '',
    name: '',
    account: '',
    phone: '',
    password: '',
    confirmPassword: '',
  });
const dialog = ref<'buy' | 'complete' | 'editOrder' | 'store' | 'product' | 'member' | null>(null),
  confirmState = ref<{
    title: string;
    message: string;
    action: () => Promise<void>;
  }>(),
  activeProduct = ref<Product>(),
  activeOrder = ref<any>(),
  activeStore = ref<Store>(),
  activeMember = ref<any>(),
  productFiles = ref<File[]>([]),
  storefrontFile = ref<File>(),
  form = ref({
    name: '',
    account: '',
    phone: '',
    password: '',
    role: 'OPERATOR',
    active: true,
    location: '',
    price: 0,
    onSale: true,
    qty: 100,
    urgent: false,
    actualPrice: 0,
    remark: '',
    operatorRemark: '',
    buyerRemark: '',
  });
const routeByTab: Record<string, string> = {
  goods: '/selection',
  orders: '/purchases',
  archive: '/archives',
  mine: '/profile',
};
function navigate(nextTab: string) {
  tab.value = nextTab;
  history.pushState({}, '', routeByTab[nextTab]);
  if (nextTab !== 'mine') load();
}
function syncRoute() {
  const route = Object.entries(routeByTab).find(([, path]) => path === location.pathname);
  if (route) tab.value = route[0];
  else if (!user.value) authMode.value = location.pathname === '/register' ? 'register' : 'login';
  else {
    tab.value = 'goods';
    history.replaceState({}, '', '/selection');
  }
}
function switchAuth(mode: 'login' | 'register') {
  authMode.value = mode;
  history.pushState({}, '', mode === 'login' ? '/login' : '/register');
}
const api = async (path: string, options: any = {}) => {
  const r = await fetch('/api' + path, {
    ...options,
    headers: {
      ...(options.body instanceof FormData ? {} : { 'Content-Type': 'application/json' }),
      ...(user.value ? { Authorization: `Bearer ${user.value.token}` } : {}),
    },
  });
  if (!r.ok) {
    let errorMessage = `请求失败（${r.status}）`;
    try {
      const errorBody = await r.json();
      if (errorBody?.message) errorMessage = errorBody.message;
    } catch {
      // 非 JSON 错误响应保留包含 HTTP 状态码的通用提示。
    }
    throw new Error(errorMessage);
  }
  return r.status === 204 ? null : r.json();
};
const notify = (s: string) => {
  message.value = s;
  setTimeout(() => (message.value = ''), 2600);
};
const logout = () => {
  window.localStorage.clear();
  history.replaceState({}, '', '/login');
  window.location.reload();
};
async function submitAuth() {
  try {
    if (authMode.value === 'register' && auth.value.password !== auth.value.confirmPassword) {
      return notify('登录密码和确认密码不一致');
    }
    if (authMode.value === 'register' && !/^1[3-9]\d{9}$/.test(auth.value.phone)) {
      return notify('请输入正确的手机号码');
    }
    user.value = await api('/auth/' + authMode.value, {
      method: 'POST',
      body: JSON.stringify(auth.value),
    });
    localStorage.setItem('auth', JSON.stringify(user.value));
    history.replaceState({}, '', '/selection');
    tab.value = 'goods';
    await load();
  } catch (e: any) {
    notify(e.message);
  }
}
async function load() {
  if (!user.value) return;
  await Promise.all([loadProducts(), loadOrders()]);
  if (['ADMIN', 'BUYER'].includes(user.value.role)) {
    stores.value = await api('/stores');
    archiveProducts.value = await api('/products?archive=true');
  }
  if (user.value.role === 'ADMIN') members.value = await api('/users');
}
async function loadProducts() {
  products.value = await api('/products?q=' + encodeURIComponent(q.value));
  appliedQ.value = q.value;
}
async function loadOrders() {
  orders.value = await api('/purchase-orders?date=' + selectedDate.value);
}
async function selectDate(date: string) {
  selectedDate.value = date;
  await loadOrders();
}
async function buy(p: Product) {
  activeProduct.value = p;
  form.value = {
    ...form.value,
    qty: 100,
    urgent: false,
    operatorRemark: '',
    buyerRemark: '',
  };
  dialog.value = 'buy';
}
async function complete(o: any) {
  activeOrder.value = o;
  form.value = {
    ...form.value,
    qty: o.planQty,
    actualPrice: Number(o.productPrice || 0),
    operatorRemark: o.operatorRemark || '',
    buyerRemark: o.buyerRemark || '',
  };
  dialog.value = 'complete';
}
async function createStore() {
  activeStore.value = undefined;
  storefrontFile.value = undefined;
  form.value = { ...form.value, name: '', location: '' };
  dialog.value = 'store';
}
function editStore(store: Store) {
  activeStore.value = store;
  storefrontFile.value = undefined;
  form.value = { ...form.value, name: store.name, location: store.location };
  dialog.value = 'store';
}
async function deleteStore(store: Store) {
  confirmState.value = {
    title: '确认删除店铺',
    message: `删除“${store.name}”后，该店铺及其商品将不再展示。是否继续？`,
    action: async () => {
      await api(`/stores/${store.id}`, { method: 'DELETE' });
      notify('店铺已删除');
      await load();
    },
  };
}
async function createProduct(store?: Store) {
  if (!stores.value.length) return notify('请先创建店铺');
  activeStore.value = store || stores.value[0];
  activeProduct.value = undefined;
  productFiles.value = [];
  form.value = { ...form.value, name: '', price: 0, onSale: true };
  dialog.value = 'product';
}
function editProduct(product: Product) {
  activeProduct.value = product;
  activeStore.value = stores.value.find((store) => store.id === product.storeId);
  productFiles.value = [];
  form.value = {
    ...form.value,
    name: product.name,
    price: product.price,
    onSale: product.onSale,
  };
  dialog.value = 'product';
}
async function toggleProduct(product: Product) {
  await api(`/products/${product.id}`, {
    method: 'PATCH',
    body: JSON.stringify({ onSale: !product.onSale }),
  });
  await load();
}
async function deleteProduct(product: Product) {
  confirmState.value = {
    title: '确认删除商品',
    message: `确认删除商品“${product.name}”吗？删除后将不再展示。`,
    action: async () => {
      await api(`/products/${product.id}`, { method: 'DELETE' });
      notify('商品已删除');
      await load();
    },
  };
}
function editOrder(order: any) {
  activeOrder.value = order;
  form.value = {
    ...form.value,
    qty: order.planQty,
    operatorRemark: order.operatorRemark || '',
    buyerRemark: order.buyerRemark || '',
  };
  dialog.value = 'editOrder';
}
async function deleteOrder(order: any) {
  confirmState.value = {
    title: '警告：删除采购订单',
    message: `确认删除“${order.productName}”采购任务吗？此操作不可撤销。`,
    action: async () => {
      await api(`/purchase-orders/${order.id}`, { method: 'DELETE' });
      await loadOrders();
      notify('采购订单已删除');
    },
  };
}
function createMember() {
  activeMember.value = undefined;
  form.value = {
    ...form.value,
    name: '',
    account: '',
    phone: '',
    password: '',
    role: 'OPERATOR',
    active: true,
  };
  dialog.value = 'member';
}
function editMember(member: any) {
  activeMember.value = member;
  form.value = {
    ...form.value,
    name: member.name,
    account: member.account,
    phone: member.phone || '',
    password: '',
    role: member.role,
    active: member.active,
  };
  dialog.value = 'member';
}
async function deleteMember(member: any) {
  if (!confirm(`确认删除子账号“${member.name}”吗？`)) return;
  await api(`/users/${member.id}`, { method: 'DELETE' });
  members.value = await api('/users');
}
async function executeConfirm() {
  const pending = confirmState.value;
  confirmState.value = undefined;
  if (pending) await pending.action();
}
async function saveCompany(name: string) {
  const result = await api('/users/company', {
    method: 'PATCH',
    body: JSON.stringify({ name }),
  });
  user.value.company = result.name;
  localStorage.setItem('auth', JSON.stringify(user.value));
  notify('采购主体名称已保存');
}
async function submitDialog() {
  try {
    await submitDialogRequest();
  } catch (error: any) {
    notify(error?.message || '操作失败，请稍后重试');
  }
}

async function submitDialogRequest() {
  if (dialog.value === 'buy' && activeProduct.value) {
    await api('/purchase-orders', {
      method: 'POST',
      body: JSON.stringify({
        productId: activeProduct.value.id,
        planQty: form.value.qty,
        urgent: form.value.urgent,
        operatorRemark: form.value.operatorRemark,
      }),
    });
    notify('已加入今日采购单');
  } else if (dialog.value === 'complete' && activeOrder.value) {
    await api(`/purchase-orders/${activeOrder.value.id}/complete`, {
      method: 'POST',
      body: JSON.stringify({
        actualQty: form.value.qty,
        actualPrice: form.value.actualPrice,
        buyerRemark: form.value.buyerRemark,
      }),
    });
  } else if (dialog.value === 'editOrder' && activeOrder.value) {
    await api(`/purchase-orders/${activeOrder.value.id}`, {
      method: 'PATCH',
      body: JSON.stringify({
        planQty: form.value.qty,
        operatorRemark: form.value.operatorRemark,
        buyerRemark: form.value.buyerRemark,
      }),
    });
  } else if (dialog.value === 'store') {
    const store = await api(activeStore.value ? `/stores/${activeStore.value.id}` : '/stores', {
      method: activeStore.value ? 'PATCH' : 'POST',
      body: JSON.stringify({ name: form.value.name, location: form.value.location }),
    });
    if (storefrontFile.value) await uploadStorefrontFile(storefrontFile.value, store);
  } else if (dialog.value === 'product') {
    const product = await api(
      activeProduct.value ? `/products/${activeProduct.value.id}` : '/products',
      {
        method: activeProduct.value ? 'PATCH' : 'POST',
        body: JSON.stringify({
          name: form.value.name,
          price: form.value.price,
          storeId: activeStore.value?.id,
          onSale: form.value.onSale,
        }),
      },
    );
    const files = [...productFiles.value];
    if (files.length) {
      dialog.value = null;
      await load();
      beginRegionUploads(files, product);
      return;
    }
  } else if (dialog.value === 'member') {
    await api(activeMember.value ? `/users/${activeMember.value.id}` : '/users', {
      method: activeMember.value ? 'PATCH' : 'POST',
      body: JSON.stringify({
        name: form.value.name,
        account: form.value.account,
        phone: form.value.phone,
        password: form.value.password,
        role: form.value.role,
        active: form.value.active,
      }),
    });
  }
  dialog.value = null;
  await load();
}
async function uploadStorefrontFile(file: File, store: Store) {
  const data = new FormData();
  data.append('file', file);
  return api(`/images/stores/${store.id}/storefront`, { method: 'POST', body: data });
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
function uploadProductImages(event: Event, product: Product) {
  const input = event.target as HTMLInputElement;
  const files = Array.from(input.files || []);
  input.value = '';
  if (files.length) beginRegionUploads(files, product);
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
  uploadQueue = ref<File[]>([]),
  uploadTotal = ref(0),
  imageRect = ref({ x: 0, y: 0, w: 0, h: 0, naturalW: 0, naturalH: 0 });
function openCropFile(file: File, mode: 'search' | 'upload', product?: Product) {
  cropFile.value = file;
  cropMode.value = mode;
  cropProduct.value = product;
  boxes.value = [];
  box.value = { x: 20, y: 20, w: 180, h: 180 };
  if (cropSrc.value) URL.revokeObjectURL(cropSrc.value);
  cropSrc.value = URL.createObjectURL(file);
  if (mode === 'search') searchPreview.value = cropSrc.value;
  cropOpen.value = true;
  nextTick(draw);
}
function beginRegionUploads(files: File[], product: Product) {
  uploadQueue.value = [...files];
  uploadTotal.value = files.length;
  openCropFile(uploadQueue.value[0], 'upload', product);
}
function chooseImage(e: Event, mode: 'search' | 'upload', p?: Product) {
  const input = e.target as HTMLInputElement;
  const files = Array.from(input.files || []);
  const f = files[0];
  input.value = '';
  if (!f) return;
  if (mode === 'upload' && p) beginRegionUploads(files, p);
  else openCropFile(f, mode, p);
}
function cancelCrop() {
  uploadQueue.value = [];
  uploadTotal.value = 0;
  cropOpen.value = false;
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
function canvasPoint(e: MouseEvent) {
  const target = canvas.value!,
    rect = target.getBoundingClientRect();
  return {
    x: ((e.clientX - rect.left) / rect.width) * target.width,
    y: ((e.clientY - rect.top) / rect.height) * target.height,
  };
}
function down(e: MouseEvent) {
  const point = canvasPoint(e);
  start.value = point;
  box.value = { x: point.x, y: point.y, w: 0, h: 0 };
}
function move(e: MouseEvent) {
  if (!(e.buttons & 1)) return;
  const point = canvasPoint(e);
  box.value.w = point.x - start.value.x;
  box.value.h = point.y - start.value.y;
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
      uploadQueue.value.shift();
      if (uploadQueue.value.length) {
        openCropFile(uploadQueue.value[0], 'upload', cropProduct.value);
        notify(`当前图片已完成 ${regions.length} 个区域建模，请继续框选下一张图片`);
      } else {
        const total = uploadTotal.value;
        uploadTotal.value = 0;
        cropOpen.value = false;
        await load();
        notify(`已完成 ${total} 张图片的框选与向量化`);
      }
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
const visibleProducts = computed(() => products.value);
const pendingOrders = computed(() => orders.value.filter((order) => order.status !== 'COMPLETED'));
const completedOrders = computed(() =>
  orders.value.filter((order) => order.status === 'COMPLETED'),
);
const actualAmount = computed(() =>
  completedOrders.value.reduce(
    (sum, order) => sum + Number(order.actualQty || 0) * Number(order.actualPrice || 0),
    0,
  ),
);
const actualQty = computed(() =>
  completedOrders.value.reduce((sum, order) => sum + Number(order.actualQty || 0), 0),
);
const canManageOwner = computed(
  () => members.value.find((member) => member.id === user.value?.userId)?.owner === true,
);
const dateCaption = computed(() => {
  const offset = (days: number) => {
    const date = new Date();
    date.setDate(date.getDate() - days);
    return date.toISOString().slice(0, 10);
  };
  if (selectedDate.value === offset(0)) return '今天';
  if (selectedDate.value === offset(1)) return '昨天';
  if (selectedDate.value === offset(2)) return '前天';
  return selectedDate.value;
});
let refreshTimer: number | undefined;
onMounted(() => {
  syncRoute();
  window.addEventListener('popstate', syncRoute);
  if (!user.value && !['/login', '/register'].includes(location.pathname)) {
    history.replaceState({}, '', '/login');
  }
  load();
  refreshTimer = window.setInterval(() => {
    if (tab.value === 'orders') loadOrders();
  }, 2000);
});
onBeforeUnmount(() => {
  window.removeEventListener('popstate', syncRoute);
  if (refreshTimer) window.clearInterval(refreshTimer);
});
</script>
<template>
  <AuthPage v-if="!user" :mode="authMode" :form="auth" @switch="switchAuth" @submit="submitAuth" />

  <div v-else class="app">
    <header>
      <div>
        <div class="eyebrow">{{ user.company }}采购</div>
        <h1>
          {{
            tab === 'goods'
              ? '今天，要找什么好货？'
              : tab === 'orders'
                ? '今天，要跑哪些档口？'
                : tab === 'archive'
                  ? '供应商与商品档案'
                  : '我的采购后台'
          }}
        </h1>
      </div>
      <button class="avatar" @click="navigate('mine')">{{ user.name[0] }}</button>
    </header>

    <main>
      <SelectionPage
        v-if="tab === 'goods'"
        :products="visibleProducts"
        :query="q"
        :applied-query="appliedQ"
        :search-preview="searchPreview"
        :user-role="user.role"
        :company="user.company"
        @query-change="q = $event"
        @search="loadProducts"
        @image-search="chooseImage($event, 'search')"
        @preview="previewOpen = true"
        @clear-image="
          searchPreview = '';
          q = '';
          loadProducts();
        "
        @buy="buy"
      />
      <PurchasesPage
        v-else-if="tab === 'orders'"
        :orders="orders"
        :pending-count="pendingOrders.length"
        :completed-count="completedOrders.length"
        :actual-amount="actualAmount"
        :actual-qty="actualQty"
        :user-role="user.role"
        :selected-date="selectedDate"
        :date-caption="dateCaption"
        @complete="complete"
        @edit="editOrder"
        @remove="deleteOrder"
        @select-date="selectDate"
      />
      <ArchivesPage
        v-else-if="tab === 'archive'"
        :stores="stores"
        :products="archiveProducts"
        :user-role="user.role"
        @create-store="createStore"
        @create-product="createProduct"
        @edit-store="editStore"
        @delete-store="deleteStore"
        @storefront="uploadStorefront"
        @product-image="uploadProductImages"
        @edit-product="editProduct"
        @toggle-product="toggleProduct"
        @delete-product="deleteProduct"
      />
      <ProfilePage
        v-else
        :user="user"
        :members="members"
        :can-manage-owner="canManageOwner"
        @logout="logout"
        @save-company="saveCompany"
        @create-member="createMember"
        @edit-member="editMember"
        @delete-member="deleteMember"
      />

      <template v-if="false">
        <div class="search">
          <input v-model="q" @keyup.enter="load" placeholder="搜商品、店铺或档口位置" />
          <button @click="load">搜索</button>
          <label class="image-search"
            >▣ 以图搜图<input
              hidden
              type="file"
              accept="image/*"
              @change="chooseImage($event, 'search')"
          /></label>
        </div>
        <div class="heading">
          <div>
            <div class="eyebrow">{{ user.company }}采购</div>
            <h2>{{ q ? `找到 ${visibleProducts.length} 个相关商品` : '历史热采' }}</h2>
          </div>
          <span>已下架商品不会展示 · 相似检索 Top 20</span>
        </div>
        <div class="grid">
          <article v-for="p in visibleProducts" :key="p.id" class="card">
            <div class="carousel">
              <img v-if="p.images[0]" class="product-photo" :src="p.images[0]" :alt="p.name" />
              <div v-else class="art coral">饰</div>
              <div class="dots"><i class="active"></i><i></i><i></i></div>
              <span class="counter">{{ p.images.length || 1 }} 张</span>
            </div>
            <div class="body">
              <div class="loc">⌖ {{ p.storeLocation || '档口信息已隐藏' }}</div>
              <h3>{{ p.name }}</h3>
              <p>{{ p.storeName || '供应商信息已隐藏' }} · 历史采购 {{ p.totalPurchasedQty }} 件</p>
              <div class="photo-hint">商品图片可左右切换查看</div>
              <div class="foot">
                <strong>¥{{ Number(p.price).toFixed(2) }}</strong>
                <button v-if="['ADMIN', 'OPERATOR'].includes(user.role)" @click="buy(p)">
                  加入今日采购
                </button>
              </div>
            </div>
          </article>
        </div>
      </template>

      <template v-if="false">
        <div class="date-tabs">
          <button class="active">今天</button><button>昨天</button><button>前天</button>
          <input type="date" :value="new Date().toISOString().slice(0, 10)" />
          <span class="date-caption">正在查看：今天</span>
        </div>
        <div class="summary">
          <div>
            <span>待跑档口</span><strong>{{ pendingOrders.length }}</strong>
          </div>
          <div>
            <span>计划件数</span><strong>{{ orders.reduce((s, o) => s + o.planQty, 0) }}</strong>
          </div>
          <div>
            <span>实际采购金额</span><strong>¥{{ actualAmount.toFixed(0) }}</strong>
          </div>
          <div>
            <span>完成进度</span>
            <strong
              >{{
                orders.length ? Math.round((completedOrders.length / orders.length) * 100) : 0
              }}%</strong
            >
          </div>
        </div>
        <div class="route">↗ 今日待办已按档口位置排好路线 · 当前角色：{{ user.role }}</div>
        <article
          v-for="(o, index) in orders"
          :key="o.id"
          class="order"
          :class="{ done: o.status === 'COMPLETED' }"
        >
          <div class="order-gallery">
            <img v-if="o.images?.[0]" :src="o.images[0]" />
            <div v-else class="thumb sage">饰</div>
            <small>1 / 1</small>
          </div>
          <div class="order-location">
            <span class="stop">{{
              o.status === 'COMPLETED' ? '✓ 已完成' : `第 ${index + 1} 站`
            }}</span>
            <strong>⌖ {{ o.storeLocation || '档口信息已隐藏' }}</strong>
            <small>{{ o.storeName || '供应商信息已隐藏' }}　{{ o.productName }}</small>
          </div>
          <div class="qty-focus" :class="{ single: o.status !== 'COMPLETED' }">
            <div>
              <span>计划采购</span><strong>{{ o.planQty }}</strong
              ><em>件</em>
            </div>
            <div v-if="o.status === 'COMPLETED'">
              <span>实际采购</span><strong>{{ o.actualQty }}</strong
              ><em>件</em>
            </div>
          </div>
          <div v-if="o.status === 'COMPLETED'" class="price-focus">
            <span>实际采购价</span><strong>¥{{ Number(o.actualPrice).toFixed(2) }}</strong>
          </div>
          <div v-else class="record-action">
            <button
              v-if="['ADMIN', 'BUYER'].includes(user.role)"
              class="complete-action"
              @click="complete(o)"
            >
              完成
            </button>
          </div>
        </article>
      </template>

      <template v-if="false">
        <div class="heading">
          <div>
            <div class="eyebrow">供应商档案</div>
            <h2>供应商店铺</h2>
            <span>先建立店铺，再进入店铺维护商品</span>
          </div>
          <div class="heading-actions">
            <button v-if="user.role === 'ADMIN'" class="primary" @click="createStore">
              ＋ 新建店铺
            </button>
            <button v-if="user.role === 'ADMIN'" class="primary" @click="createProduct()">
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
            <article v-for="s in stores" :key="s.id" class="supplier-record">
              <div class="storefront">
                <img v-if="s.storefrontUrl" :src="s.storefrontUrl" :alt="`${s.name}门头`" />
                <span v-else>店</span>
              </div>
              <div class="record-info">
                <div class="loc">{{ s.location }}</div>
                <h3>{{ s.name }}</h3>
                <p>已归档 {{ products.filter((p) => p.storeId === s.id).length }} 款商品</p>
              </div>
              <div class="record-action">
                <label v-if="user.role === 'ADMIN'" class="ghost"
                  >{{ s.storefrontUrl ? '替换门头' : '上传门头'
                  }}<input
                    hidden
                    type="file"
                    accept="image/*"
                    @change="uploadStorefront($event, s)"
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
            <article v-for="p in products" :key="p.id" class="archive-record">
              <div class="storefront product-thumb">
                <img v-if="p.images[0]" :src="p.images[0]" :alt="p.name" /><span v-else>饰</span>
              </div>
              <div class="record-info">
                <div class="loc">{{ p.storeLocation }}</div>
                <h3>{{ p.name }}</h3>
                <div class="record-meta">
                  <span
                    >价格<strong>¥{{ p.price }}</strong></span
                  >
                  <span
                    >状态<strong>{{ p.onSale ? '上架' : '下架' }}</strong></span
                  >
                  <span
                    >图片<strong>{{ p.images.length }} 张</strong></span
                  >
                </div>
              </div>
              <div class="record-action">
                <label v-if="user.role === 'ADMIN'" class="ghost"
                  >上传并框选商品<input
                    hidden
                    type="file"
                    accept="image/*"
                    @change="chooseImage($event, 'upload', p)"
                /></label>
              </div>
            </article>
          </div>
        </section>
      </template>

      <template v-if="false">
        <section class="profile">
          <div class="avatar">{{ user.name[0] }}</div>
          <div>
            <div class="eyebrow">{{ user.role }} · 当前账号</div>
            <h2>{{ user.name }}</h2>
            <small>{{ user.company }}采购团队 · 今天在线</small>
          </div>
          <button class="logout-button" @click="logout">退出登录</button>
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
    </main>

    <nav>
      <button :class="{ active: tab === 'goods' }" @click="navigate('goods')">
        <b>⌕</b>选品中心
      </button>
      <button :class="{ active: tab === 'orders' }" @click="navigate('orders')">
        <b>✓</b>今日采购
      </button>
      <button
        v-if="['ADMIN', 'BUYER'].includes(user.role)"
        :class="{ active: tab === 'archive' }"
        @click="navigate('archive')"
      >
        <b>▤</b>档案管理
      </button>
      <button :class="{ active: tab === 'mine' }" @click="navigate('mine')"><b>●</b>我的</button>
    </nav>
  </div>

  <div v-if="confirmState" class="modal show warning-modal" @click.self="confirmState = undefined">
    <section class="confirm-dialog">
      <div class="warning-icon">!</div>
      <h2>{{ confirmState.title }}</h2>
      <p>{{ confirmState.message }}</p>
      <div class="confirm-actions">
        <button class="ghost" @click="confirmState = undefined">取消</button>
        <button class="danger-button" @click="executeConfirm">确认删除</button>
      </div>
    </section>
  </div>

  <div v-if="dialog" class="modal show" @click.self="dialog = null">
    <section class="sheet">
      <div class="eyebrow">
        {{
          dialog === 'store'
            ? '供应商店铺'
            : dialog === 'product'
              ? '商品档案'
              : dialog === 'member'
                ? '团队账号'
                : '采购记录'
        }}
      </div>
      <h2>
        {{
          dialog === 'buy'
            ? '加入今日采购'
            : dialog === 'complete'
              ? '确认实际采购'
              : dialog === 'editOrder'
                ? '编辑未完成采购单'
                : dialog === 'store'
                  ? activeStore
                    ? '编辑店铺'
                    : '新建店铺'
                  : dialog === 'product'
                    ? activeProduct
                      ? '编辑商品档案'
                      : '添加商品档案'
                    : activeMember
                      ? '修改账号'
                      : '添加子账号'
        }}
      </h2>
      <strong v-if="activeProduct && ['buy', 'product'].includes(dialog)"
        >{{ activeProduct.name }} · {{ activeProduct.storeLocation }}</strong
      >
      <label v-if="['store', 'product', 'member'].includes(dialog)"
        ><p>
          {{ dialog === 'store' ? '店铺名称' : dialog === 'member' ? '用户名称' : '商品名称' }}
        </p>
        <input v-model="form.name"
      /></label>
      <label v-if="dialog === 'store'"
        ><p>档口位置</p>
        <input v-model="form.location" placeholder="例如：1区 3楼 12街 18390"
      /></label>
      <label v-if="dialog === 'store'"
        ><p>门头图片</p>
        <input
          type="file"
          accept="image/*"
          @change="storefrontFile = ($event.target as HTMLInputElement).files?.[0]"
      /></label>
      <label v-if="dialog === 'product'"
        ><p>商品价格</p>
        <input v-model.number="form.price" type="number" step=".1"
      /></label>
      <label v-if="dialog === 'product'"
        ><p>商品图片（可多选，保存后逐张框选商品）</p>
        <input
          type="file"
          multiple
          accept="image/*"
          @change="productFiles = Array.from(($event.target as HTMLInputElement).files || [])"
      /></label>
      <label v-if="dialog === 'product'"
        ><p>商品状态</p>
        <select v-model="form.onSale">
          <option :value="true">上架</option>
          <option :value="false">下架</option>
        </select></label
      >
      <label v-if="['buy', 'complete', 'editOrder'].includes(dialog)"
        ><p>{{ dialog === 'complete' ? '实际采购数量' : '计划采购数量' }}</p>
        <input v-model.number="form.qty" type="number" min="1"
      /></label>
      <label v-if="dialog === 'buy'" class="urgent-option">
        <input v-model="form.urgent" type="checkbox" />
        <span><strong>急采</strong><small>勾选后，今日采购任务会显示火焰标识</small></span>
      </label>
      <label v-if="dialog === 'complete'"
        ><p>实际采购单价</p>
        <input v-model.number="form.actualPrice" type="number" min="0" step=".01"
      /></label>
      <label
        v-if="['buy', 'editOrder'].includes(dialog) && ['ADMIN', 'OPERATOR'].includes(user?.role)"
        ><p>运营备注</p>
        <textarea v-model="form.operatorRemark" rows="3" />
      </label>
      <label
        v-if="['complete', 'editOrder'].includes(dialog) && ['ADMIN', 'BUYER'].includes(user?.role)"
        ><p>买手备注</p>
        <textarea v-model="form.buyerRemark" rows="3" />
      </label>
      <template v-if="dialog === 'member'">
        <label
          ><p>登录账号</p>
          <input v-model="form.account" :disabled="!!activeMember"
        /></label>
        <label
          ><p>手机号（选填）</p>
          <input v-model="form.phone" inputmode="numeric" maxlength="11"
        /></label>
        <label
          ><p>{{ activeMember ? '新密码（留空不修改）' : '登录密码' }}</p>
          <input v-model="form.password" type="password"
        /></label>
        <label v-if="!activeMember?.owner"
          ><p>角色</p>
          <select v-model="form.role">
            <option value="ADMIN">管理员</option>
            <option value="OPERATOR">运营</option>
            <option value="BUYER">买手</option>
            <option value="VIEWER">查看者</option>
          </select></label
        >
        <label v-if="!activeMember?.owner"
          ><p>账号状态</p>
          <select v-model="form.active">
            <option :value="true">启用</option>
            <option :value="false">停用</option>
          </select></label
        >
      </template>
      <button class="primary" @click="submitDialog">确认</button>
    </section>
  </div>

  <div v-if="cropOpen" class="modal show" @click.self="cancelCrop">
    <section class="sheet crop-sheet">
      <div class="eyebrow">SIGLIP2 IMAGE SEARCH</div>
      <h2>
        {{
          cropMode === 'upload'
            ? `框选多个商品区域（第 ${uploadTotal - uploadQueue.length + 1}/${uploadTotal} 张）`
            : '框选要搜索的商品'
        }}
      </h2>
      <p>
        {{
          cropMode === 'upload'
            ? '每次拖拽添加一个框，最多 20 个；裁剪小图仅向量化，不保存。'
            : '拖拽矩形，只使用框选区域进行相似图片检索。'
        }}
      </p>
      <canvas ref="canvas" @mousedown="down" @mousemove="move" @mouseup="finishBox"></canvas>
      <div class="region-list" v-if="cropMode === 'upload'">
        <button v-for="(_, index) in boxes" :key="index" @click="removeBox(index)">
          删除区域 {{ index + 1 }}
        </button>
      </div>
      <div class="sheet-actions">
        <button class="ghost" @click="cancelCrop">取消</button>
        <button class="primary" @click="cropSubmit">
          {{ cropMode === 'upload' ? `确认 ${boxes.length} 个区域` : '确认框选' }}
        </button>
      </div>
    </section>
  </div>
  <div v-if="previewOpen && searchPreview" class="lightbox show" @click.self="previewOpen = false">
    <button class="lightbox-close" @click="previewOpen = false">×</button>
    <img :src="searchPreview" alt="以图搜图上传原图" />
  </div>
  <div v-if="message" class="toast show">{{ message }}</div>
</template>
