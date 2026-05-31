<script setup>
import { ref } from 'vue'
import AppHeader from '@/components/AppHeader/AppHeader.vue'
import InputView from '@/components/InputView/InputView.vue'
import ProgressView from '@/components/ProgressView/ProgressView.vue'
import ResultView from '@/components/ResultView/ResultView.vue'

const stageTab = ref(1)

const sampleReport = {
  overallSummary: '合并登录模块重构，涉及 Controller/Service/DAO 三层，优化了认证流程',
  globalRiskLevel: 'MEDIUM',
  globalRiskReason: '涉及 Token 刷新逻辑变更，与现有 Session 管理存在潜在兼容性问题',
  prUrl: 'https://github.com/user/repo/pull/42',
  analysisTimeMs: 3842,
  crossFileIssues: [
    {
      issueType: 'INTERFACE_MISMATCH',
      description: 'AuthController 调用新的 AuthService.refreshToken() 方法，但返回类型不一致',
      involvedFiles: ['AuthController.java', 'AuthService.java'],
      severity: 'HIGH',
      suggestion: '统一返回类型为 TokenResponse',
    },
  ],
  architectureSuggestions: [
    '建议将 Token 校验逻辑抽取为独立 Filter，避免重复',
    'Session 和 Token 双模式存在安全盲区，建议统一为 Token 模式',
  ],
  fileReports: [
    {
      filePath: 'AuthController.java',
      status: 'MODIFIED',
      riskLevel: 'HIGH',
      overallSummary: '重构登录接口，新增 refreshToken 端点',
      risks: [
        { type: 'SECURITY', line: 25, description: 'refreshToken 未校验用户登出状态' },
        { type: 'COMPATIBILITY', line: 42, description: '返回类型变更未做版本兼容' },
      ],
      suggestions: [
        { priority: 1, description: '添加 @PreAuthorize 注解控制访问权限' },
        { priority: 2, description: '保留旧接口标记 @Deprecated' },
      ],
    },
    {
      filePath: 'AuthService.java',
      status: 'MODIFIED',
      riskLevel: 'MEDIUM',
      overallSummary: '新增 Token 刷新逻辑',
      risks: [
        { type: 'PERFORMANCE', line: 88, description: '每次刷新都查数据库，建议改用 Redis 缓存' },
      ],
      suggestions: [
        { priority: 3, description: '引入 Redis 缓存 Token，设置 15 分钟过期' },
      ],
    },
    {
      filePath: 'UserDao.java',
      status: 'MODIFIED',
      riskLevel: 'LOW',
      overallSummary: '新增按用户名查询接口',
      suggestions: [
        { priority: 5, description: '添加分页参数防止全表扫描' },
      ],
    },
  ],
}
</script>

<template>
  <div class="min-h-screen bg-slate-50 flex flex-col">
    <AppHeader
      :stage-tab="stageTab"
      task-id="a1b2c3d4"
      app-state="result"
      @tab-change="stageTab = $event"
    />
    <main class="flex-1">
      <InputView v-if="stageTab === 1" />
      <ProgressView
        v-if="stageTab === 2"
        :stages="{ l1: 'complete', l2: 'active', l3: 'pending' }"
        :files="[
          { path: 'AuthController.java', status: 'complete' },
          { path: 'AuthService.java', status: 'analyzing' },
        ]"
        status-text="Analyzing files..."
      />
      <ResultView
        v-if="stageTab === 3"
        :report="sampleReport"
        @new-review="stageTab = 1"
      />
    </main>
  </div>
</template>
