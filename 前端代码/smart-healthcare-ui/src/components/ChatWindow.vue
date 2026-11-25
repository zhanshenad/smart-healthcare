<template>
  <div class="app-layout">
    <div class="sidebar">
      <div class="logo-section">
        <img src="@/assets/logo.png" alt="智慧医疗" width="160" height="160" />
        <span class="logo-text">智慧医疗 AI 助手</span>
      </div>
      <div v-if="user" class="user-section">
        <span class="user-name">{{ user.nickname || user.phone }}</span>
        <el-button text size="small" @click="logout">退出</el-button>
      </div>
      <el-button class="new-chat-button" @click="newChat">
        <i class="fa-solid fa-plus"></i>
        &nbsp;新会话
      </el-button>
    </div>
    <div class="main-content">
      <div class="chat-container">
        <div class="message-list" ref="messaggListRef">
          <div
            v-for="(message, index) in messages"
            :key="index"
            :class="
              message.isUser ? 'message user-message' : 'message bot-message'
            "
          >
            <!-- 会话图标 -->
            <i
              :class="
                message.isUser
                  ? 'fa-solid fa-user message-icon'
                  : 'fa-solid fa-robot message-icon'
              "
            ></i>
            <!-- 会话内容 -->
            <span>
              <span v-html="message.content"></span>
              <!-- loading -->
              <span
                class="loading-dots"
                v-if="message.isThinking || message.isTyping"
              >
                <span class="dot"></span>
                <span class="dot"></span>
              </span>
            </span>
          </div>
        </div>
        <div class="model-bar">
          <el-select
            v-model="selectedModelId"
            size="small"
            placeholder="选择模型"
            style="width: 180px"
            @change="rememberModel"
          >
            <el-option
              v-for="option in modelOptions"
              :key="option.id"
              :label="option.modelName || option.id"
              :value="option.id"
            />
          </el-select>
          <el-tag v-if="currentStatus" size="small" :type="currentStatus.type">
            {{ currentStatus.text }}
          </el-tag>
          <el-button text size="small" @click="openModelSettings">模型设置</el-button>
        </div>
        <div class="input-container">
          <el-input
            v-model="inputMessage"
            placeholder="请输入消息"
            @keyup.enter="sendMessage"
          ></el-input>
          <el-button @click="sendMessage" :disabled="isSending" type="primary"
            >发送</el-button
          >
        </div>
      </div>
    </div>

    <el-dialog v-model="modelDialogVisible" title="模型 Key 设置" width="520px">
      <div v-for="option in providerOptions" :key="option.provider" class="model-setting-row">
        <div class="model-setting-head">
          <span>{{ option.modelName || option.id }}</span>
          <el-tag size="small" :type="modelStatus(option).type">{{ modelStatus(option).text }}</el-tag>
        </div>
        <div class="model-setting-body">
          <el-input
            v-model="keyInputs[option.provider]"
            placeholder="输入该 provider 的 API Key"
            show-password
            clearable
          />
          <el-button
            type="primary"
            size="small"
            :loading="savingProvider === option.provider"
            @click="saveKey(option.provider)"
          >
            保存
          </el-button>
          <el-button
            v-if="option.configured"
            type="danger"
            text
            size="small"
            @click="deleteKey(option.provider)"
          >
            删除
          </el-button>
        </div>
        <div class="model-setting-hint">{{ modelStatus(option).hint }}</div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { v4 as uuidv4 } from 'uuid'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api/http'

const props = defineProps({
  user: {
    type: Object,
    default: null,
  },
})
const emit = defineEmits(['logout'])

const messaggListRef = ref()
const isSending = ref(false)
const uuid = ref()
const inputMessage = ref('')
const messages = ref([])

const modelOptions = ref([])
const selectedModelId = ref(localStorage.getItem('chat_model_id') || '')
const modelDialogVisible = ref(false)
const keyInputs = reactive({})
const savingProvider = ref('')

const currentOption = computed(() =>
  modelOptions.value.find((option) => option.id === selectedModelId.value)
)

// 同一个 provider 只展示一次 Key 配置
const providerOptions = computed(() => {
  const seen = new Set()
  return modelOptions.value.filter((option) => {
    if (seen.has(option.provider)) {
      return false
    }
    seen.add(option.provider)
    return true
  })
})

onMounted(async () => {
  initUUID()
  // 移除 setInterval，改用手动滚动
  watch(messages, () => scrollToBottom(), { deep: true })
  await loadModels()
  hello()
})

const scrollToBottom = () => {
  if (messaggListRef.value) {
    messaggListRef.value.scrollTop = messaggListRef.value.scrollHeight
  }
}

const hello = () => {
  sendRequest('你好')
}

const sendMessage = () => {
  if (inputMessage.value.trim()) {
    sendRequest(inputMessage.value.trim())
    inputMessage.value = ''
  }
}

const sendRequest = (message) => {
  isSending.value = true
  const userMsg = {
    isUser: true,
    content: message,
    isTyping: false,
    isThinking: false,
  }
  //第一条默认发送的用户消息”你好“不放入会话列表
  if(messages.value.length > 0){
    messages.value.push(userMsg)
  }


  // 添加机器人加载消息
  const botMsg = {
    isUser: false,
    content: '', // 增量填充
    isTyping: true, // 显示加载动画
    isThinking: false,
  }
  messages.value.push(botMsg)
  const lastMsg = messages.value[messages.value.length - 1]
  scrollToBottom()

  http
    .post(
      '/medical/chat',
      { memoryId: uuid.value, message, modelId: selectedModelId.value || undefined },
      {
        responseType: 'stream', // 按流读取，逐步拼接响应文本
        onDownloadProgress: (e) => {
          const fullText = e.event.target.responseText // 累积的完整文本
          let newText = fullText.substring(lastMsg.content.length)
          lastMsg.content += newText //增量更新
          console.log(lastMsg)
          scrollToBottom() // 实时滚动
        },
      }
    )
    .then(() => {
      // 流结束后隐藏加载动画
      messages.value.at(-1).isTyping = false
      isSending.value = false
    })
    .catch((error) => {
      console.error('流式错误:', error)
      messages.value.at(-1).content = '请求失败，请重试'
      messages.value.at(-1).isTyping = false
      isSending.value = false
    })
}

const loadModels = async () => {
  try {
    const { data } = await http.get('/medical/models')
    const payload = data.data || {}
    modelOptions.value = payload.models || []

    const stored = localStorage.getItem('chat_model_id')
    const exists = (id) => modelOptions.value.some((option) => option.id === id)
    if (stored && exists(stored)) {
      selectedModelId.value = stored
    } else if (!exists(selectedModelId.value)) {
      selectedModelId.value = payload.defaultModelId || modelOptions.value[0]?.id || ''
    }
  } catch (error) {
    console.error('加载模型列表失败', error)
  }
}

const rememberModel = () => {
  if (selectedModelId.value) {
    localStorage.setItem('chat_model_id', selectedModelId.value)
  }
}

// 模型状态的文案、标签颜色和提示集中在一处
const modelStatus = (option) => {
  if (option.configured) {
    return { text: '已配置', type: 'success', hint: `当前使用：${option.maskedKey}` }
  }
  if (option.fallback) {
    return { text: '服务端默认', type: 'info', hint: '未配置，使用服务端默认 Key' }
  }
  return { text: '未配置', type: 'warning', hint: '未配置，请填写 Key 后再使用' }
}

const currentStatus = computed(() =>
  currentOption.value ? modelStatus(currentOption.value) : null
)

const openModelSettings = async () => {
  await loadModels()
  modelDialogVisible.value = true
}

const saveKey = async (provider) => {
  const apiKey = (keyInputs[provider] || '').trim()
  if (!apiKey) {
    ElMessage.warning('请输入 API Key')
    return
  }

  savingProvider.value = provider
  try {
    // 保存前服务端会做对话和流式校验，留足超时
    await http.put('/medical/model-keys', { provider, apiKey }, { timeout: 120000 })
    ElMessage.success('API Key 已保存并启用')
    keyInputs[provider] = ''
    await loadModels()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '保存失败')
  } finally {
    savingProvider.value = ''
  }
}

const deleteKey = async (provider) => {
  try {
    await ElMessageBox.confirm('删除后将回退到服务端默认 Key，确认删除？', '提示', {
      type: 'warning',
    })
    await http.delete(`/medical/model-keys/${provider}`)
    ElMessage.success('已删除')
    await loadModels()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error.response?.data?.message || '删除失败')
    }
  }
}

// 初始化 UUID
const initUUID = () => {
  let storedUUID = localStorage.getItem('chat_memory_id')
  if (!storedUUID) {
    storedUUID = uuidv4()
    localStorage.setItem('chat_memory_id', storedUUID)
  }
  uuid.value = storedUUID
}

const logout = () => {
  localStorage.removeItem('chat_memory_id')
  emit('logout')
}

// 转换特殊字符
const convertStreamOutput = (output) => {
  return output
    .replace(/\n/g, '<br>')
    .replace(/\t/g, '&nbsp;&nbsp;&nbsp;&nbsp;')
    .replace(/&/g, '&amp;') // 新增转义，避免 HTML 注入
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

const newChat = () => {
  localStorage.removeItem('chat_memory_id')
  window.location.reload()
}

</script>
<style scoped>
.app-layout {
  display: flex;
  height: 100vh;
}

.sidebar {
  width: 200px;
  background-color: #f4f4f9;
  padding: 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.logo-section {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.logo-text {
  font-size: 18px;
  font-weight: bold;
  margin-top: 10px;
}

.user-section {
  width: 100%;
  margin-top: 18px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  color: #475569;
  font-size: 13px;
}

.user-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.new-chat-button {
  width: 100%;
  margin-top: 20px;
}

.main-content {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
}
.chat-container {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 10px;
  border: 1px solid #e0e0e0;
  border-radius: 4px;
  background-color: #fff;
  margin-bottom: 10px;
  display: flex;
  flex-direction: column;
}

.message {
  margin-bottom: 10px;
  padding: 10px;
  border-radius: 4px;
  display: flex;
  /* align-items: center; */
}

.user-message {
  max-width: 70%;
  background-color: #e1f5fe;
  align-self: flex-end;
  flex-direction: row-reverse;
}

.bot-message {
  max-width: 100%;
  background-color: #f1f8e9;
  align-self: flex-start;
}

.message-icon {
  margin: 0 10px;
  font-size: 1.2em;
}

.loading-dots {
  padding-left: 5px;
}

.dot {
  display: inline-block;
  margin-left: 5px;
  width: 8px;
  height: 8px;
  background-color: #000000;
  border-radius: 50%;
  animation: pulse 1.2s infinite ease-in-out both;
}

.dot:nth-child(2) {
  animation-delay: -0.6s;
}

@keyframes pulse {
  0%,
  100% {
    transform: scale(0.6);
    opacity: 0.4;
  }

  50% {
    transform: scale(1);
    opacity: 1;
  }
}
.model-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}

.model-setting-row {
  padding: 12px 0;
  border-bottom: 1px solid #ebeef5;
}

.model-setting-row:last-child {
  border-bottom: none;
}

.model-setting-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  font-weight: 600;
}

.model-setting-body {
  display: flex;
  gap: 8px;
}

.model-setting-body .el-input {
  flex: 1;
}

.model-setting-hint {
  margin-top: 6px;
  color: #909399;
  font-size: 12px;
}

.input-container {
  display: flex;
}

.input-container .el-input {
  flex: 1;
  margin-right: 10px;
}

/* 媒体查询，当设备宽度小于等于 768px 时应用以下样式 */
@media (max-width: 768px) {
  .main-content {
    padding: 10px 0 10px 0;
  }
  .app-layout {
    flex-direction: column;
  }

  .sidebar {
    /* display: none; */
    width: 100%;
    flex-direction: row;
    justify-content: space-between;
    align-items: center;
    padding: 10px;
  }

  .logo-section {
    flex-direction: row;
    align-items: center;
  }

  .logo-text {
    font-size: 20px;
  }

  .logo-section img {
    width: 40px;
    height: 40px;
  }

  .new-chat-button {
    margin-right: 30px;
    width: auto;
    margin-top: 5px;
  }
}

/* 媒体查询，当设备宽度大于 768px 时应用原来的样式 */
@media (min-width: 769px) {
  .main-content {
    padding: 0 0 10px 10px;
  }

  .app-layout {
    display: flex;
    height: 100vh;
  }

  .sidebar {
    width: 200px;
    background-color: #f4f4f9;
    padding: 20px;
    display: flex;
    flex-direction: column;
    align-items: center;
  }

  .logo-section {
    display: flex;
    flex-direction: column;
    align-items: center;
  }

  .logo-text {
    font-size: 18px;
    font-weight: bold;
    margin-top: 10px;
  }

  .new-chat-button {
    width: 100%;
    margin-top: 20px;
  }
}
</style>
