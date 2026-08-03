import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import http from 'http'

// 为代理创建一个禁用 keep-alive 的 agent，强制每次请求新建 TCP 连接，
// 避免 http-proxy 复用连接时响应不 flush 导致浏览器一直 pending
const proxyAgent = new http.Agent({
  keepAlive: false,
  maxSockets: 20
})

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    host: '0.0.0.0',
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        agent: proxyAgent,
        proxyTimeout: 60000,
        timeout: 60000,
        configure: (proxy) => {
          proxy.on('proxyRes', (proxyRes) => {
            // 强制告诉浏览器这条响应结束后就关闭连接，避免 chunked 流被挂着
            proxyRes.headers['connection'] = 'close'
          })
        }
      },
      '/ws': {
        target: 'ws://localhost:8080',
        ws: true
      }
    }
  }
})
