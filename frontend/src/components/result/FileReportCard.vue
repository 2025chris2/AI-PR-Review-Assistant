<script setup>
import { ref } from 'vue'
import RiskBadge from './RiskBadge.vue'

const props = defineProps({
  report: { type: Object, required: true },
})

const open = ref(false)

const statusColors = {
  ADDED: 'bg-emerald-100 text-emerald-700',
  MODIFIED: 'bg-blue-100 text-blue-700',
  REMOVED: 'bg-red-100 text-red-700',
  RENAMED: 'bg-amber-100 text-amber-700',
}
const defaultStatus = 'bg-slate-100 text-slate-600'
</script>

<template>
  <div class="bg-white rounded-xl shadow-sm border border-slate-200 overflow-hidden">
    <!-- Header -->
    <button @click="open = !open"
      class="w-full flex items-center gap-3 px-4 py-3 hover:bg-slate-50 transition-colors text-left">
      <svg class="w-4 h-4 shrink-0 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
          d="M7 21h10a2 2 0 002-2V9.414a1 1 0 00-.293-.707l-5.414-5.414A1 1 0 0012.586 3H7a2 2 0 00-2 2v14a2 2 0 002 2z" />
      </svg>

      <span class="font-mono text-xs text-slate-700 truncate flex-1">{{ report.filePath }}</span>

      <!-- Status badge -->
      <span v-if="report.status"
        class="text-[10px] font-semibold px-1.5 py-0.5 rounded shrink-0"
        :class="statusColors[report.status] || defaultStatus">
        {{ report.status }}
      </span>

      <RiskBadge v-if="report.riskLevel" :level="report.riskLevel" size="sm" />

      <!-- Expand icon -->
      <svg class="w-4 h-4 text-slate-400 shrink-0 transition-transform" :class="open ? 'rotate-180' : ''"
        fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
      </svg>
    </button>

    <!-- Body -->
    <div v-if="open" class="border-t border-slate-100 px-4 py-3 space-y-4">
      <div v-if="report.error" class="text-sm text-red-600 bg-red-50 rounded-lg px-3 py-2">
        {{ report.error }}
      </div>

      <div v-else>
        <p v-if="report.overallSummary" class="text-sm text-slate-700 mb-3">{{ report.overallSummary }}</p>

        <!-- Risks -->
        <div v-if="report.risks?.length" class="mb-4">
          <h4 class="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-2">Risks</h4>
          <div class="space-y-1.5">
            <div v-for="(risk, i) in report.risks" :key="i"
              class="flex items-start gap-2 text-sm">
              <span class="w-1.5 h-1.5 rounded-full bg-red-400 mt-1.5 shrink-0" />
              <div>
                <span class="text-[10px] font-mono text-slate-400" v-if="risk.line">L{{ risk.line }}</span>
                <span class="text-slate-700"> {{ risk.description }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- Suggestions -->
        <div v-if="report.suggestions?.length" class="mb-4">
          <h4 class="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-2">Suggestions</h4>
          <div class="space-y-1.5">
            <div v-for="(s, i) in report.suggestions" :key="i" class="flex items-start gap-2 text-sm">
              <span class="w-1.5 h-1.5 rounded-full bg-blue-400 mt-1.5 shrink-0" />
              <span class="text-slate-700">{{ s.description }}</span>
            </div>
          </div>
        </div>

        <!-- Cross-chunk issues -->
        <div v-if="report.crossChunkIssues?.length">
          <h4 class="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-2">Cross-Chunk Consistency</h4>
          <div class="space-y-1">
            <p v-for="(issue, i) in report.crossChunkIssues" :key="i"
              class="text-sm text-slate-600 flex items-start gap-2">
              <span class="w-1.5 h-1.5 rounded-full bg-amber-400 mt-1.5 shrink-0" />
              {{ issue }}
            </p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
