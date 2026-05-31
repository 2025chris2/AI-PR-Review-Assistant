# AI-PR-Review-Assistant

## 系统架构

### 总览

```
┌─────────────────────────────────────────────────────────────────────┐
│                           前端 (Vue 3 + Vite)                        │
│   InputView ────→ ProgressView ────→ ResultView                      │
│   (提交PR信息)     (SSE进度推送)       (评审报告)                       │
└────────────┬──────────────┬──────────────────────────────────────────┘
             │   POST /api/v1/reviews    │  GET /{taskId}/events (SSE)
             │   POST /api/v1/reviews/github  │  GET /{taskId}/result
             │   POST /api/v1/reviews/by-url  │
             ▼              ▼                                          │
┌──────────────────────────────────────────────────────────────────────┐
│                      后端 (Spring Boot 3.5.14)                        │
│                                                                      │
│  ReviewController ──→ ReviewOrchestrator ──→ L1 → L2 → L3 Pipeline  │
│        │                     │                                       │
│        │               ReviewEventPublisher                          │
│        │               (SSE事件推送)                                  │
│        ▼                     ▼                                       │
│   GitHubApiClient    ┌──────────────┐                                │
│   (REST API v3)      │  L1: DiffSanitizer + DiffParser               │
│                      │  原始Diff → 去噪 → SanitizedDiff列表           │
│                      ├──────────────┤                                │
│                      │  L2: FileChunkAnalyzer                        │
│                      │  整文件/分块 → Map(AI调用) → Reduce(聚合)     │
│                      │  • ChunkAnalyzer (Spring AI ChatClient)       │
│                      │  • FileReportReducer                          │
│                      ├──────────────┤                                │
│                      │  L3: GlobalAggregator                         │
│                      │  CrossFileAnalyzer(规则引擎) → AI聚合 → 报告   │
│                      │  • GlobalPromptBuilder                        │
│                      │  • RiskPrioritizer                            │
│                      └──────────────┘                                │
│                            │                                         │
│                      DeepSeek API                                    │
│                      (deepseek-chat)                                  │
└──────────────────────────────────────────────────────────────────────┘
```

---

### 后端架构

#### 1. Controller 层 — 请求入口与 SSE 推送

`ReviewController` (`/api/v1/reviews`)

```
POST /api/v1/reviews          ← Raw Diff 文本 + PR 元数据
POST /api/v1/reviews/github   ← owner/repo/prNumber + Token
POST /api/v1/reviews/by-url   ← GitHub PR URL + 可选Token
         │
         ├─→ GitHubApiClient.fetchAll()     (github/by-url端点)
         │     GET /repos/{owner}/{repo}/pulls/{number}
         │     GET /repos/{owner}/{repo}/pulls/{number}/files
         │     GET /repos/{owner}/{repo}/pulls/{number} (Accept: diff)
         │     → GitHubPrData { rawDiff, PrMetadata }
         │
         └─→ startAsyncAnalysis(rawDiff, metadata)
               │  CompletableFuture.runAsync (Virtual Threads)
               │
               ├─→ orchestrator.review(rawDiff, metadata, taskId)
               │     → GlobalReviewReport → resultCache (TTL 30min)
               │     → SSE推送 RESULT 事件
               │
               └─→ 返回 202 Accepted { taskId, eventsUrl, resultUrl }

GET /{taskId}/events           ← SSE连接 (EventSource)
GET /{taskId}/result           ← 查询评审结果 (202 等待 / 200 完成)
```

**SSE 事件类型**: `task.started` → `l1.complete` → `l2.file.start` → `l2.file.complete` → `l2.complete` → `l3.start` → `l3.complete` → `result` | `error`

**ReviewEventPublisher**: 按 taskId 管理 SseEmitter 连接池（CopyOnWriteArrayList），支持多客户端并发推送，emitter 异常/超时自动清理。

---

#### 2. Orchestrator 层 — 流水线编排

`ReviewOrchestrator` — 单入口 `review(rawDiff, metadata, taskId)`

```
ReviewTask (状态机)
  PENDING → L1_SANITIZING → L2_ANALYZING → L3_AGGREGATING → COMPLETED
                                                               ↓
                                                            FAILED (异常)
```

驱动三层流水线，管理任务生命周期，收集各层耗时和错误，最终返回 `GlobalReviewReport`。

---

#### 3. L1 — Diff 去噪

`DiffSanitizer` + `DiffParser`

```
原始 git diff 文本
      │
      ▼
  ParseState 状态机:
    BETWEEN_FILES → IN_FILE_HEADER → IN_HUNK_CONTENT
         ↑               │                  │
         └── diff --git ──┘                  │
                                             │
                    ← ← diff --git ← ← ← ← ← ┘
      │
      ▼
  List<SanitizedDiff>
    ├─ filePath, status (ADDED/MODIFIED/REMOVED/RENAMED)
    ├─ hunks[]: oldStartLine, newStartLine, lines[]
    ├─ sanitizedContent (仅保留 @@ + 代码行)
    ├─ originalLineCount, sanitizedLineCount
    └─ savingsRatio (去噪节省比例)
```

**DiffParser** — 纯函数工具类：行分类、Hunk头解析、路径提取、尾部空白清理。

---

#### 4. L2 — 文件分析 (Map/Reduce)

`FileChunkAnalyzer` — 门面类，每个文件分配独立 Virtual Thread

```
SanitizedDiff
      │
      ├── Token ≤ 8000 ──→ 路径A: 整文件分析
      │     ChunkPromptBuilder.buildWholeFile(diff)
      │         → ChunkAnalyzer (AI调用)
      │         → FileReportReducer.reduce()
      │
      └── Token > 8000 ──→ 路径B: 分块分析
            1. HunkBasedChunkSplitter.split(diff)
            2. FunctionBasedChunkSplitter.refine() (超长块二次拆分)
            3. Map: 并行 ChunkAnalyzer.analyze(prompt, chunkId)
            4. Reduce: FileReportReducer.reduce(chunkResults)
                  • 去重合并同类风险
                  • 风险升级 (任一HIGH → 整体HIGH)
                  • 跨块一致性检查 (crossChunkHints)
                  • 摘要精炼
      │
      ▼
  FileReviewReport
    ├─ filePath, status, overallSummary, riskLevel
    ├─ risks[]: type, line, description
    ├─ suggestions[]: priority, description
    ├─ crossChunkIssues[]
    └─ analysisMethod (WHOLE_FILE / CHUNKED)
```

**ChunkAnalyzer** — 调用 Spring AI `ChatClient`，将 Prompt 发送给 DeepSeek API，解析 JSON 响应为 `ChunkReviewResult`。

**ChunkPromptBuilder** — 系统角色设定（数值计算+通用审查）+ 输出格式约束 + 反幻觉约束。两种Prompt模式：整文件 / 分块（含前序摘要）。

---

#### 5. L3 — 全局聚合

`GlobalAggregator` — 门面类，串行执行5步流程

```
List<FileReviewReport> + PrMetadata
      │
      ├─ Step 1: CrossFileAnalyzer.extractHints(fileReports)
      │     6条纯本地规则（不调用AI）:
      │       规则1: 接口-实现一致性
      │       规则2: 数据库-代码一致性 (Mapper.xml ↔ Entity)
      │       规则3: 重复逻辑检测
      │       规则4: 事务边界检测
      │       规则5: 架构层次一致性 (Controller/Service/Mapper三层)
      │       规则6: 数值稳定性静态扫描 (浮点比较、矩阵维度)
      │     → List<String> hints
      │
      ├─ Step 2: GlobalPromptBuilder.build(fileReports, metadata, hints)
      │     组装完整AI提示词（文件摘要 + PR信息 + 跨文件线索）
      │
      ├─ Step 3: ChatClient AI 单次调用
      │     Prompt → DeepSeek API → JSON响应
      │
      ├─ Step 4: parseResponse(json, fileReports)
      │     解析AI返回JSON:
      │       overallSummary, globalRiskLevel, globalRiskReason,
      │       crossFileIssues[], architectureSuggestions[], topPriorityFiles[]
      │
      └─ Step 5: RiskPrioritizer.prioritize(report)
            后处理与标准化
      │
      ▼
  GlobalReviewReport
    ├─ taskId, overallSummary, globalRiskLevel (HIGH/MEDIUM/LOW)
    ├─ globalRiskReason, prUrl, analysisTimeMs
    ├─ fileReports[] (来自L2的全部文件报告)
    ├─ crossFileIssues[]: issueType, severity, description, involvedFiles, suggestion
    ├─ architectureSuggestions[]
    └─ error (异常时)
```

---

#### 6. 包结构

```
com.prassistant.pr
├── PrApplication.java                  # Spring Boot 入口
├── config/PrReviewProperties.java      # 配置属性 (pr.review.*)
├── diff/
│   ├── model/                          # SanitizedDiff, DiffHunk, FileChangeType, ParseState
│   ├── parser/DiffParser.java          # 纯函数解析工具
│   └── service/DiffSanitizer.java      # L1 去噪引擎
├── review/
│   ├── controller/ReviewController.java  # REST API + SSE
│   ├── FileChunkAnalyzer.java          # L2 门面 (Map/Reduce)
│   ├── model/                          # Chunk, ChunkReviewResult, FileReviewReport
│   ├── analyzer/                       # ChunkAnalyzer, ChunkPromptBuilder, FileReportReducer
│   ├── splitter/                       # HunkBasedChunkSplitter, FunctionBasedChunkSplitter
│   └── util/TokenEstimator.java        # Token 计数
├── aggregation/
│   ├── GlobalAggregator.java           # L3 门面
│   ├── CrossFileAnalyzer.java          # 规则引擎 (6条本地规则)
│   ├── GlobalPromptBuilder.java        # L3 提示词组装
│   ├── RiskPrioritizer.java            # 后处理
│   └── model/                          # GlobalReviewReport, PrMetadata
├── github/
│   ├── GitHubApiClient.java            # GitHub REST API v3 客户端
│   ├── GitHubPrData.java               # PR 数据聚合
│   ├── GitHubPrFile.java               # 文件信息
│   └── GitHubApiException.java         # 自定义异常
└── orchestrator/
    ├── ReviewOrchestrator.java         # 流水线编排
    ├── model/                          # ReviewTask, ReviewState
    └── event/                          # ReviewEvent, ReviewEventPublisher, ReviewEventType
```

---

### 前端架构

#### 1. 应用入口与状态管理

`App.vue` — 提供 `useReview()` 组合式 API 作为全局状态

```
const review = useReview()
provide('review', review)    → 子组件通过 inject('review') 获取

Stage切换 (v-if):
  stageTab === 1  →  InputView      (输入PR信息)
  stageTab === 2  →  ProgressView    (分析进度)
  stageTab === 3  →  ResultView      (评审报告)
```

---

#### 2. useReview — 状态机组合函数

`composables/useReview.js`

```
appState: 'input' ──提交──→ 'progress' ──完成──→ 'result'
                ←──────── reset() ──────────────┘

提交流程:
  1. submitRawDiff(payload) / submitGitHub(payload) / submitByUrl(payload)
  2. POST API → 获取 { taskId, eventsUrl, resultUrl }
  3. connectSSE() — 建立 EventSource 连接，监听进度事件
  4. startPolling() — 每2秒轮询 resultUrl (兜底机制)
  5. SSE RESULT 事件 或 轮询成功 → report.value = data → appState = 'result'

SSE事件处理:
  l2.file.start  → 追加文件到 filesInProgress[]
  l2.file.chunk  → 更新 chunk 计数
  l2.file.complete → 标记文件完成
  *.complete      → 更新 pipelineStages 状态
  result          → 关闭SSE，fetchResult()
  error           → 致命错误时终止

状态暴露:
  report (shallowRef), appState, stageTab, inputMode,
  pipelineStages, filesInProgress, stageMessages, error,
  submitRawDiff, submitGitHub, submitByUrl, reset
```

---

#### 3. useSSE — EventSource 封装

`composables/useSSE.js`

```
useSSE(url, handlers)
  ├─ new EventSource(url)
  ├─ 为每个 eventName 注册 addEventListener
  ├─ JSON.parse 自动解析 data
  ├─ onerror → 调用 handlers.error()
  └─ close() → 清理所有监听器 + eventSource.close()
```

---

#### 4. 组件树

```
App.vue
├─ AppHeader.vue                        # 顶部导航栏
├─ InputView.vue (stageTab=1)
│   ├─ InputMethodToggle.vue            # raw / github 模式切换
│   ├─ RawDiffForm.vue                  # 粘贴 diff 文本 或 GitHub URL
│   └─ GitHubForm.vue                   # owner/repo/pr# + token
├─ ProgressView.vue (stageTab=2)
│   ├─ PipelineStages.vue               # L1/L2/L3 进度指示器
│   ├─ FileProgressList.vue             # 各文件分析进度列表
│   └─ ProgressSpinner.vue              # 加载动画
└─ ResultView.vue (stageTab=3)
    ├─ ResultSummary.vue                # PR标题 / 风险等级 / URL / 原因
    ├─ 左侧: 文件列表 (按风险排序)
    │   └─ RiskBadge.vue                # HIGH/MEDIUM/LOW 风险徽标
    └─ 右侧: Tab切换
        ├─ 文件建议详情 (风险 / 建议 / 跨块一致性)
        ├─ CrossFileIssues.vue          # 跨文件问题 (手风琴列表)
        └─ ArchitectureSuggestions.vue  # 架构建议 (列表)
```

---

#### 5. API 服务

`services/api.js`

```
API_BASE = '/api/v1/reviews'

createReview(payload)         → POST /api/v1/reviews         (raw diff)
createReviewFromGitHub(payload) → POST /api/v1/reviews/github
createReviewByUrl(payload)    → POST /api/v1/reviews/by-url
fetchResult(resultUrl)        → GET /api/v1/reviews/{taskId}/result
                                 (202 → 分析中, 200 → 返回报告JSON)
```

所有请求含 30s `AbortController` 超时控制。

---

#### 6. 常量定义

`constants/pipeline.js`

```
STAGES: l1(Sanitize Diff), l2(Analyze Files), l3(Aggregate)

EVENT_TYPES:
  task.started, l1.complete, l2.file.start, l2.file.chunk,
  l2.file.complete, l2.complete, l3.start, l3.complete,
  result, error
```

---

### 后端 → 前端 数据流

```
                        后端分析完成
                             │
              ┌──────────────┴──────────────┐
              │   SSE: event=result          │
              │   GET /{taskId}/result       │
              └──────────────┬──────────────┘
                             │
                             ▼
              ┌──────────────────────────────────┐
              │       GlobalReviewReport JSON     │
              │                                   │
              │  {                                │
              │    "taskId": "a1b2c3d4",          │
              │    "overallSummary": "...",       │  → ResultSummary 标题
              │    "globalRiskLevel": "MEDIUM",   │  → ResultSummary 风险徽标
              │    "globalRiskReason": "...",     │  → ResultSummary 原因说明
              │    "prUrl": "https://...",        │  → ResultSummary PR链接
              │    "analysisTimeMs": 12345,       │  → 侧边栏耗时统计
              │    "fileReports": [              │
              │      {                            │
              │        "filePath": "...",         │  → 左侧文件列表
              │        "status": "MODIFIED",      │  → 状态标签 (A/M/D/R)
              │        "riskLevel": "HIGH",       │  → RiskBadge + 排序依据
              │        "overallSummary": "...",   │  → 文件详情标题
              │        "risks": [                 │  → 风险列表
              │          { "type": "...",         │
              │            "line": 42,            │
              │            "description": "..." } │
              │        ],                         │
              │        "suggestions": [           │  → 建议列表
              │          { "priority": 1,         │
              │            "description": "..." } │
              │        ],                         │
              │        "crossChunkIssues": [...]  │  → 跨块一致性问题
              │      }                            │
              │    ],                             │
              │    "crossFileIssues": [           │  → CrossFileIssues 组件
              │      {                            │
              │        "issueType": "...",        │
              │        "severity": "HIGH",        │
              │        "description": "...",      │
              │        "involvedFiles": [...],    │
              │        "suggestion": "..."        │
              │      }                            │
              │    ],                             │
              │    "architectureSuggestions": [   │  → ArchitectureSuggestions
              │      "建议1",                     │
              │      "建议2"                      │
              │    ],                             │
              │    "error": null                  │  → 错误提示
              │  }                                │
              └──────────────────────────────────┘
                             │
                             ▼
                      ResultView.vue
                    inject('review').report
```

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
