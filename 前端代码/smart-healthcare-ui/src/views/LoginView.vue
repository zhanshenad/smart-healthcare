<template>
  <div class="login-page">
    <div class="login-card">
      <img src="@/assets/logo.png" alt="智慧医疗" class="logo" />
      <h1>智慧医疗</h1>
      <p class="subtitle">手机号验证码登录，首次登录自动注册</p>

      <el-form @submit.prevent>
        <el-form-item>
          <el-input
            v-model="phone"
            maxlength="11"
            placeholder="请输入手机号"
            clearable
          />
        </el-form-item>

        <el-form-item>
          <div class="code-row">
            <el-input
              v-model="code"
              maxlength="6"
              placeholder="6位验证码"
              clearable
            />
            <el-button :disabled="countdown > 0" @click="sendCode">
              {{ countdown > 0 ? `${countdown}s` : '获取验证码' }}
            </el-button>
          </div>
        </el-form-item>

        <el-button
          type="primary"
          class="submit-button"
          :loading="loading"
          @click="loginBySms"
        >
          登录 / 注册
        </el-button>
      </el-form>

      <el-divider>或</el-divider>

      <el-button text @click="showPasswordLogin = !showPasswordLogin">
        {{ showPasswordLogin ? '收起密码登录' : '使用密码登录' }}
      </el-button>

      <div v-if="showPasswordLogin" class="password-login">
        <el-input
          v-model="password"
          type="password"
          show-password
          placeholder="请输入密码"
        />
        <el-button :loading="loading" @click="loginByPassword">密码登录</el-button>
      </div>
    </div>

    <el-dialog
      v-model="needSetPassword"
      title="设置登录密码"
      width="380px"
      :close-on-click-modal="false"
      :show-close="false"
    >
      <p class="dialog-tip">设置密码后，下次可以直接使用密码登录。</p>
      <el-input
        v-model="newPassword"
        type="password"
        show-password
        maxlength="64"
        placeholder="至少8位密码"
      />
      <template #footer>
        <el-button @click="skipSetPassword">稍后设置</el-button>
        <el-button type="primary" :loading="loading" @click="setPassword">
          保存密码
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import http, { currentUser, saveAuth } from '@/api/http'

const emit = defineEmits(['logged-in'])

const phone = ref('')
const code = ref('')
const password = ref('')
const newPassword = ref('')
const countdown = ref(0)
const loading = ref(false)
const showPasswordLogin = ref(false)
const needSetPassword = ref(false)

const phonePattern = /^1[3-9]\d{9}$/

const validatePhone = () => {
  if (!phonePattern.test(phone.value)) {
    ElMessage.warning('请输入正确的手机号')
    return false
  }
  return true
}

const sendCode = async () => {
  if (!validatePhone()) return
  try {
    await http.post('/auth/sms/send', { phone: phone.value })
    ElMessage.success('验证码已发送，请查看后端日志')
    countdown.value = 60
    const timer = setInterval(() => {
      countdown.value -= 1
      if (countdown.value <= 0) clearInterval(timer)
    }, 1000)
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '验证码发送失败')
  }
}

const loginBySms = async () => {
  if (!validatePhone() || !/^\d{6}$/.test(code.value)) {
    ElMessage.warning('请输入手机号和6位验证码')
    return
  }
  await login('/auth/login/sms', { phone: phone.value, code: code.value })
}

const loginByPassword = async () => {
  if (!validatePhone() || !password.value) {
    ElMessage.warning('请输入手机号和密码')
    return
  }
  await login('/auth/login/password', {
    phone: phone.value,
    password: password.value,
  })
}

const login = async (url, payload) => {
  loading.value = true
  try {
    const response = await http.post(url, payload)
    const data = response.data.data
    saveAuth(data)
    if (data.needSetPassword) {
      needSetPassword.value = true
    } else {
      finishLogin()
    }
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '登录失败')
  } finally {
    loading.value = false
  }
}

const setPassword = async () => {
  if (newPassword.value.length < 8) {
    ElMessage.warning('密码至少8位')
    return
  }
  loading.value = true
  try {
    await http.post('/auth/password/set', { password: newPassword.value })
    const user = currentUser()
    if (user) {
      user.hasPassword = true
      localStorage.setItem('user_info', JSON.stringify(user))
    }
    ElMessage.success('密码设置成功')
    needSetPassword.value = false
    finishLogin()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '密码设置失败')
  } finally {
    loading.value = false
  }
}

const skipSetPassword = () => {
  needSetPassword.value = false
  finishLogin()
}

const finishLogin = () => {
  emit('logged-in', currentUser())
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #e0f2fe 0%, #f8fafc 50%, #dcfce7 100%);
}

.login-card {
  width: 380px;
  padding: 32px;
  border-radius: 20px;
  background: #fff;
  box-shadow: 0 20px 60px rgba(15, 23, 42, 0.12);
  text-align: center;
}

.logo {
  width: 96px;
  height: 96px;
  object-fit: contain;
}

h1 {
  margin: 12px 0 4px;
  color: #0f172a;
}

.subtitle {
  margin: 0 0 24px;
  color: #64748b;
  font-size: 13px;
}

.code-row {
  width: 100%;
  display: flex;
  gap: 8px;
}

.submit-button {
  width: 100%;
}

.password-login {
  margin-top: 12px;
  display: flex;
  gap: 8px;
}

.dialog-tip {
  margin: 0 0 12px;
  color: #64748b;
  font-size: 13px;
}
</style>
