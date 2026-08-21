<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    width="70%"
    top="5vh"
    :close-on-click-modal="true"
    @update:model-value="close"
    @closed="onClosed"
  >
    <div class="log-status">
      <el-tag v-if="status === 'RUNNING'" type="warning" effect="dark">部署中...</el-tag>
      <el-tag v-else-if="status === 'SUCCESS'" type="success" effect="dark">部署成功</el-tag>
      <el-tag v-else-if="status === 'FAILED'" type="danger" effect="dark">部署失败</el-tag>
      <el-tag v-else-if="status === 'CANCELLED'" type="info" effect="dark">部署已取消</el-tag>
      <el-button v-if="status === 'RUNNING' && isSuperAdmin" type="danger" size="small"
                 :loading="cancelling" @click="cancelDeploy" style="margin-left: 12px">取消部署</el-button>
    </div>
    <pre ref="logBox" class="log-box">等待日志输出...</pre>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deployApi } from '../api'
import { isSuperAdmin } from '../utils/perm'

const props = defineProps({
  modelValue: Boolean,
  recordId: Number,
  // live: 连 WebSocket 实时看；static: 从接口取历史日志
  mode: { type: String, default: 'live' },
  title: { type: String, default: '部署日志' }
})
const emit = defineEmits(['update:modelValue', 'finished'])

const status = ref('RUNNING')
const logBox = ref(null)
const cancelling = ref(false)
let ws = null

const FINISH_PREFIX = '__DEPLOY_FINISHED__:'

// ===== 性能优化：不依赖 Vue 响应式，用 rAF 把高频 WS 消息合并成一次 DOM 更新 =====
let lines = []           // 所有日志行（普通数组，不参与响应式）
let pendingLines = []    // 一帧内新到的消息缓冲
let rafHandle = null     // requestAnimationFrame 句柄
let scrollHandle = null  // 节流 scroll 的 rAF 句柄

function appendRaw(text) {
  pendingLines.push(text)
  if (rafHandle == null) {
    rafHandle = requestAnimationFrame(flushToDom)
  }
}

function flushToDom() {
  rafHandle = null
  if (!pendingLines.length) return
  lines = lines.concat(pendingLines)
  pendingLines = []
  if (logBox.value) {
    logBox.value.textContent = lines.join('\n')
    scheduleScroll()
  }
}

function scheduleScroll() {
  if (scrollHandle != null) return
  scrollHandle = requestAnimationFrame(() => {
    scrollHandle = null
    if (logBox.value) logBox.value.scrollTop = logBox.value.scrollHeight
  })
}

function resetBuffer() {
  lines = []
  pendingLines = []
  if (rafHandle != null) { cancelAnimationFrame(rafHandle); rafHandle = null }
  if (scrollHandle != null) { cancelAnimationFrame(scrollHandle); scrollHandle = null }
}
// =====================================================================

watch(() => props.modelValue, (visible) => {
  if (visible && props.recordId) {
    resetBuffer()
    if (logBox.value) logBox.value.textContent = '等待日志输出...'
    if (props.mode === 'live') {
      status.value = 'RUNNING'
      connectWs()
    } else {
      loadStatic()
    }
  }
})

function connectWs() {
  const protocol = location.protocol === 'https:' ? 'wss' : 'ws'
  ws = new WebSocket(`${protocol}://${location.host}/ws/log/${props.recordId}`)
  ws.onmessage = (e) => {
    const msg = e.data
    if (msg.startsWith(FINISH_PREFIX)) {
      // 刷掉缓冲后再更新状态
      flushToDom()
      status.value = msg.substring(FINISH_PREFIX.length)
      emit('finished', status.value)
      return
    }
    appendRaw(msg)
  }
  ws.onerror = () => {
    // 连接失败时兜底走静态查询（可能任务已结束）
    loadStatic()
  }
}

async function loadStatic() {
  try {
    const record = await deployApi.recordDetail(props.recordId)
    status.value = record.status
    lines = (record.log || '(无日志)').split('\n')
    if (logBox.value) logBox.value.textContent = lines.join('\n')
    scheduleScroll()
  } catch (e) { /* 拦截器已提示 */ }
}

function close() {
  emit('update:modelValue', false)
}

async function cancelDeploy() {
  try {
    await ElMessageBox.confirm('确定要取消当前部署吗？', '确认取消', { type: 'warning' })
  } catch { return }
  cancelling.value = true
  try {
    await deployApi.cancel(props.recordId)
    appendRaw('[CANCEL] 已发送取消请求，将在当前步骤完成后立即终止')
    scheduleScroll()
  } catch (e) { /* 拦截器已提示 */ } finally {
    cancelling.value = false
  }
}

function onClosed() {
  if (ws) {
    ws.close()
    ws = null
  }
  resetBuffer()
}
</script>

<style scoped>
.log-status { margin-bottom: 10px; }
.log-box {
  background: #1e1e1e; color: #d4d4d4; padding: 12px;
  height: 60vh; overflow: auto; border-radius: 4px;
  font-family: Consolas, 'Courier New', monospace; font-size: 13px;
  white-space: pre-wrap; word-break: break-all; margin: 0;
}
</style>
