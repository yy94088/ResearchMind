import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import './styles/main.css'
import { useAppStore } from './stores/app'

async function bootstrap() {
  const app = createApp(App)
  const pinia = createPinia()

  app.use(pinia)
  await useAppStore(pinia).restoreSession()
  app.use(router)
  app.use(ElementPlus)

  window.addEventListener('researchmind:unauthorized', () => {
    useAppStore(pinia).clearSession()
    if (router.currentRoute.value.name !== 'login') {
      router.replace({
        name: 'login',
        query: { redirect: router.currentRoute.value.fullPath }
      })
    }
  })

  app.mount('#app')
}

bootstrap()
