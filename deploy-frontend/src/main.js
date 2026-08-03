import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import { hasPerm, canDeploy, canEditProject, canDeployProject, user } from './utils/perm'

const app = createApp(App)
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}
app.use(ElementPlus, { locale: zhCn })
app.use(router)

// 全局暴露：模板中可直接使用 v-if="hasPerm('xxx')" 等
app.config.globalProperties.$hasPerm = hasPerm
app.config.globalProperties.$canDeploy = canDeploy
app.config.globalProperties.$canEditProject = canEditProject
app.config.globalProperties.$canDeployProject = canDeployProject
// $user 用 getter 挂载：模板每次访问 this.$user 都拿实时的 user.value
// （globalProperties 不是响应式容器，直接挂 Ref/computed 都不会自动 unwrap）
Object.defineProperty(app.config.globalProperties, '$user', {
  get() { return user.value },
  enumerable: true,
  configurable: true
})

app.mount('#app')
