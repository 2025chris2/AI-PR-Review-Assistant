# AI-PR-Review-Assistant

flowchart TD
A["前端 (Vue3)<br/>输入PR → 接收SSE进度 → 展示报告"] -->|HTTP / SSE| B
B["API 网关层 (Controller)<br/>POST /api/review/analyze<br/>GET /api/review/{taskId}/events (SSE)"] --> C
C["编排调度层 (Service)<br/>ReviewOrchestrator<br/>- 驱动三层流水线<br/>- 维护分析状态<br/>- 通过 SSE 推送进度"] --> D
C --> E
C --> F

    D["GitHub 集成模块"] & E["Diff 引擎模块"] & F["AI 引擎(Spring AI)"] --> G

    G["三层分析流水线"] --> G1
    G1["第一层：Diff 去噪(Pre-proc)"] --> G2
    G2["第二层：文件分块(Map-Reduce)"] --> G3
    G3["第三层：全局聚合(Reduce)"]
