<template>
  <section class="section container">
    <div class="grid grid-2" style="align-items:start">
      <div>
        <h2 style="font-weight:500;letter-spacing:4px">预约咨询</h2>
        <p class="muted" style="line-height:2">留下联系方式，顾问将在 30 分钟内与您联系。<br />也可直接到店：上海市静安区示例路 88 号。</p>
        <div v-for="s in stores" :key="s.id" style="border-left:3px solid var(--gold);padding-left:14px;margin:18px 0">
          <b>{{ s.store_name }}</b>
          <p class="muted">{{ s.address }} · {{ s.phone }}</p>
        </div>
      </div>
      <form class="card card-body" @submit.prevent="submit">
        <div class="form-row"><label>姓名 *</label><input v-model="form.name" required /></div>
        <div class="form-row"><label>手机号 *</label><input v-model="form.phone" required pattern="1[0-9]{10}" /></div>
        <div class="form-row"><label>意向城市</label><input v-model="form.city" /></div>
        <div class="form-row"><label>意向套系</label><input v-model="form.packageName" /></div>
        <div class="form-row"><label>需求说明</label><textarea v-model="form.requirement" rows="4" /></div>
        <button class="btn solid" type="submit" :disabled="submitting">{{ submitting ? '提交中…' : '提交预约' }}</button>
        <p v-if="done" style="color:#3a7a4a">提交成功，我们会尽快联系您。</p>
        <p v-if="error" style="color:#b43c3c">{{ error }}</p>
      </form>
    </div>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { get, pub, submitLead } from '../api'

const stores = ref([])
const done = ref(false)
const error = ref('')
const submitting = ref(false)
const form = reactive({ name: '', phone: '', city: '', packageName: '', requirement: '', sourcePage: 'pc-contact' })

async function submit() {
  submitting.value = true; error.value = ''; done.value = false
  try {
    await submitLead(form)
    done.value = true
    Object.assign(form, { name: '', phone: '', city: '', packageName: '', requirement: '' })
  } catch (e) { error.value = e.message || '提交失败，请稍后再试' }
  finally { submitting.value = false }
}
onMounted(async () => { try { stores.value = await get(pub('/stores')) } catch (e) { stores.value = [] } })
</script>
