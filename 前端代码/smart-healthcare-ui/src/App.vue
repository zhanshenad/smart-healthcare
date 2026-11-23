<template>
  <LoginView v-if="!loggedIn" @logged-in="onLoggedIn" />
  <ChatWindow v-else :user="user" @logout="onLogout" />
</template>

<script setup>
import { ref } from 'vue'
import ChatWindow from '@/components/ChatWindow.vue'
import LoginView from '@/views/LoginView.vue'
import { clearAuth, currentUser } from '@/api/http'

const loggedIn = ref(Boolean(localStorage.getItem('access_token')))
const user = ref(currentUser())

const onLoggedIn = (loginUser) => {
  user.value = loginUser
  loggedIn.value = true
}

const onLogout = () => {
  clearAuth()
  localStorage.removeItem('chat_memory_id')
  loggedIn.value = false
  user.value = null
}
</script>

<style>
html,
body {
  margin: 0;
  overflow: hidden;
}
</style>
