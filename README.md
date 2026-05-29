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
