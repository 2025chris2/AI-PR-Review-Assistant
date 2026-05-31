<script setup>
import { ref, computed } from 'vue'
import ResultSummary from './ResultSummary.vue'
import CrossFileIssues from './CrossFileIssues.vue'
import ArchitectureSuggestions from './ArchitectureSuggestions.vue'
import RiskBadge from './RiskBadge.vue'

const props = defineProps({
  report: { type: Object, default: null },
})

const emit = defineEmits(['new-review'])

const sortedFiles = computed(() => {
  const reports = props.report?.fileReports || []
  const order = { HIGH: 0, MEDIUM: 1, LOW: 2 }
  return [...reports].sort((a, b) => (order[a.riskLevel] ?? 3) - (order[b.riskLevel] ?? 3))
})

const selectedIdx = ref(0)
const selectedFile = computed(() => sortedFiles.value[selectedIdx.value] || null)
const rightTab = ref('file')

const tabs = [
  { key: 'file', label: '文件建议详情' },
  { key: 'cross', label: '跨文件问题' },
  { key: 'arch', label: '架构建议' },
]

const sidebarMetrics = computed(() => {
  const reports = props.report?.fileReports
  if (!reports) return []
  const totalRisks = reports.reduce((sum, f) => sum + (f.risks?.length || 0), 0)
  const totalSuggestions = reports.reduce((sum, f) => sum + (f.suggestions?.length || 0), 0)
  const time = props.report?.analysisTimeMs
  return [
    { label: 'Files', value: reports.length },
    { label: 'Time', value: time ? `${(time / 1000).toFixed(1)}s` : '-' },
    { label: 'Risks', value: totalRisks },
    { label: 'Suggestions', value: totalSuggestions },
  ]
})

const statusColors = {
  ADDED: 'bg-emerald-100 text-emerald-700',
  MODIFIED: 'bg-blue-100 text-blue-700',
  REMOVED: 'bg-red-100 text-red-700',
  RENAMED: 'bg-amber-100 text-amber-700',
}
const defaultStatus = 'bg-slate-100 text-slate-600'
const statusAbbr = { ADDED: 'A', MODIFIED: 'M', REMOVED: 'D', RENAMED: 'R' }
</script>

<template>
  <div class="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 py-6 space-y-4">
    <ResultSummary
      v-if="report"
      :overall-summary="report.overallSummary"
      :pr-url="report.prUrl"
      :global-risk-reason="report.globalRiskReason"
      :risk-level="report.globalRiskLevel"
      @new-review="emit('new-review')"
    />

    <div v-if="report?.error" class="bg-red-50 border border-red-200 rounded-xl p-4">
      <p class="text-sm text-red-700">{{ report.error }}</p>
    </div>

    <div v-if="report && !report.error" class="flex flex-col md:flex-row gap-4">
      <!-- Sidebar -->
      <div class="w-full md:w-1/4 shrink-0">
        <div class="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
          <div class="px-3 py-2.5 border-b border-slate-100 flex items-center gap-2">
            <h3 class="text-xs font-semibold text-slate-500 uppercase tracking-wider">Files</h3>
            <span class="text-[9px] text-slate-400 font-medium space-x-1.5" v-for="m in sidebarMetrics" :key="m.label">
              {{ m.value }}{{ m.label }}
            </span>
          </div>
          <div class="max-h-[70vh] overflow-y-auto">
            <button
              v-for="(file, idx) in sortedFiles"
              :key="file.filePath"
              type="button"
              @click="selectedIdx = idx"
              class="w-full flex items-center gap-2 px-3 py-2.5 text-left transition-colors border-b border-slate-50 last:border-0"
              :class="selectedIdx === idx ? 'bg-blue-50 border-l-2 border-l-blue-500' : 'hover:bg-slate-50 border-l-2 border-l-transparent'"
            >
              <span v-if="file.status"
                class="text-[9px] font-semibold px-1 py-0.5 rounded shrink-0"
                :class="statusColors[file.status] || defaultStatus">
                {{ statusAbbr[file.status] || 'R' }}
              </span>
              <span class="font-mono text-[11px] text-slate-700 truncate flex-1">{{ file.filePath }}</span>
              <RiskBadge v-if="file.riskLevel" :level="file.riskLevel" size="sm" />
            </button>
          </div>
          <div v-if="!sortedFiles.length" class="px-3 py-4 text-xs text-slate-400 italic text-center">
            No files analyzed
          </div>
        </div>
      </div>

      <!-- Right Panel -->
      <div class="flex-1 min-w-0 min-h-0 space-y-3 flex flex-col">
        <div class="flex gap-1 bg-slate-100 rounded-lg p-1">
          <button v-for="t in tabs" :key="t.key" type="button" @click="rightTab = t.key"
            class="px-3 py-1.5 text-xs font-medium rounded-md transition-colors"
            :class="rightTab === t.key ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-500 hover:text-slate-700'">
            {{ t.label }}
          </button>
        </div>

        <!-- File Detail Tab -->
        <div v-if="rightTab === 'file' && selectedFile" class="bg-white rounded-xl shadow-sm border border-slate-200 p-5 flex-1 overflow-auto">
          <div class="flex items-start justify-between gap-3 mb-3">
            <div class="min-w-0">
              <h3 class="text-sm font-semibold text-slate-900 font-mono truncate">{{ selectedFile.filePath }}</h3>
              <p v-if="selectedFile.overallSummary" class="text-xs text-slate-500 mt-0.5">{{ selectedFile.overallSummary }}</p>
            </div>
            <div class="flex items-center gap-2 shrink-0">
              <span v-if="selectedFile.status"
                class="text-[10px] font-semibold px-1.5 py-0.5 rounded"
                :class="statusColors[selectedFile.status] || defaultStatus">
                {{ selectedFile.status }}
              </span>
              <RiskBadge v-if="selectedFile.riskLevel" :level="selectedFile.riskLevel" size="sm" />
            </div>
          </div>

          <div v-if="selectedFile.error" class="text-sm text-red-600 bg-red-50 rounded-lg px-3 py-2 mb-3">
            {{ selectedFile.error }}
          </div>

          <div v-else class="space-y-3">
            <div v-if="selectedFile.risks?.length">
              <h4 class="text-[11px] font-semibold text-slate-500 uppercase tracking-wider mb-1.5">风险</h4>
              <div class="space-y-1">
                <div v-for="(risk, i) in selectedFile.risks" :key="i" class="flex items-start gap-2 text-sm py-1">
                  <span class="w-1.5 h-1.5 rounded-full bg-red-400 mt-1.5 shrink-0" />
                  <div>
                    <span class="text-[10px] font-mono text-slate-400" v-if="risk.line">L{{ risk.line }}</span>
                    <span class="text-slate-700"> {{ risk.description }}</span>
                  </div>
                </div>
              </div>
            </div>

            <div v-if="selectedFile.suggestions?.length">
              <h4 class="text-[11px] font-semibold text-slate-500 uppercase tracking-wider mb-1.5">建议</h4>
              <div class="space-y-1">
                <div v-for="(s, i) in selectedFile.suggestions" :key="i" class="flex items-start gap-2 text-sm py-1">
                  <span class="w-1.5 h-1.5 rounded-full bg-blue-400 mt-1.5 shrink-0" />
                  <span class="text-slate-700">{{ s.description }}</span>
                </div>
              </div>
            </div>

            <div v-if="selectedFile.crossChunkIssues?.length">
              <h4 class="text-[11px] font-semibold text-slate-500 uppercase tracking-wider mb-1.5">跨块一致性问题</h4>
              <div class="space-y-1">
                <p v-for="(issue, i) in selectedFile.crossChunkIssues" :key="i" class="text-sm text-slate-600 flex items-start gap-2 py-0.5">
                  <span class="w-1.5 h-1.5 rounded-full bg-amber-400 mt-1.5 shrink-0" />
                  {{ issue }}
                </p>
              </div>
            </div>
          </div>
        </div>

        <CrossFileIssues v-if="rightTab === 'cross' && report?.crossFileIssues" :issues="report.crossFileIssues" />
        <ArchitectureSuggestions v-if="rightTab === 'arch' && report?.architectureSuggestions" :suggestions="report.architectureSuggestions" />
      </div>
    </div>
  </div>
</template>
