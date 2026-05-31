# AI-PR-Review-Assistant

# 系统整体架构

1. **前端 (Vue3)**
   输入PR → 接收SSE进度 → 展示报告
   ↓ HTTP / SSE

2. **API 网关层 (Controller)**
   - POST /api/review/analyze
   - GET /api/review/{taskId}/events (SSE)
     ↓

3. **编排调度层 (Service) - ReviewOrchestrator**
   - 驱动三层流水线
   - 维护分析状态
   - 通过 SSE 推送进度
     ↓ 并行调用

   ├─ GitHub 集成模块
   ├─ Diff 引擎模块
   └─ AI 引擎 (Spring AI)
   ↓ 汇总

4. **三层分析流水线**
   第一层(Diff去噪) → 第二层(文件分块) → 第三层(全局聚合)

## 依赖项

### 后端 (Java / Spring Boot)

| 依赖 | 版本 | 用途 |
|------|------|------|
| Spring Boot (Parent BOM) | 3.5.14 | 应用框架，依赖管理与自动配置 |
| spring-boot-starter | — | 核心 Starter，含 Logback 日志 |
| spring-boot-starter-web | — | REST API 与嵌入式 Tomcat |
| Spring AI (spring-ai-starter-model-openai) | 1.1.7 | AI API 抽象层，兼容 OpenAI 接口 |
| Lombok | 1.18.34 | 编译期注解，减少样板代码 |
| jtokkit | 1.1.0 | Token 计数，用于 AI 提示词长度估算 |
| DeepSeek API (deepseek-chat) | — | 外部 AI 服务提供方 |
| Java | 21 | 运行环境 |
| spring-boot-starter-test | — | 测试框架 (JUnit 5 + Mockito + AssertJ) |
| Maven | 3.9.16 (via Wrapper) | 构建工具 |

### 前端 (Vue 3 / Vite)

| 依赖 | 版本 | 用途 |
|------|------|------|
| Vue | 3.5.32 | UI 框架 |
| Vite | 8.0.8 | 开发服务器与构建工具 |
| @vitejs/plugin-vue | 6.0.6 | Vite Vue 插件 |
| Tailwind CSS | 4.3.0 | 原子化 CSS 框架 |
| @tailwindcss/vite | 4.3.0 | Tailwind CSS Vite 集成 |
| Vitest | 4.1.7 | 单元测试框架 |
| @vue/test-utils | 2.4.10 | Vue 组件测试工具 |
| jsdom | 29.1.1 | 测试用 DOM 模拟环境 |
| vite-plugin-vue-devtools | 8.1.1 | Vue DevTools 集成 |
| Node.js | ^20.19.0 \|\| >=22.12.0 | 运行环境 |
