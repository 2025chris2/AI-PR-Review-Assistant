<script setup>
import { inject, ref } from 'vue'

const review = inject('review')
const issues = review.report.value?.crossFileIssues || []
const openIdx = ref(null)

function severityColor(s) {
  if (!s) return 'bg-slate-100 text-slate-600'
  const s2 = s.toLowerCase()
  if (s2.includes('high') || s2 === 'critical') return 'bg-red-100 text-red-700'
  if (s2.includes('medium') || s2 === 'major') return 'bg-amber-100 text-amber-700'
  return 'bg-slate-100 text-slate-600'
}
</script>

<template>
  <div class="bg-white rounded-xl shadow-sm border border-slate-200 p-6">
    <h3 class="text-sm font-semibold text-slate-900 mb-3">Cross-File Issues</h3>
    <div v-if="issues.length === 0" class="flex items-center gap-2 text-sm text-emerald-600">
      <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M5 13l4 4L19 7" />
      </svg>
      No cross-file issues detected
    </div>
    <div class="space-y-2">
      <div v-for="(issue, idx) in issues" :key="idx"
        class="border border-slate-200 rounded-lg overflow-hidden">
        <button @click="openIdx = openIdx === idx ? null : idx"
          class="w-full flex items-center justify-between px-4 py-3 text-left hover:bg-slate-50 transition-colors">
          <div class="flex items-center gap-2 min-w-0">
            <span class="text-[10px] font-mono uppercase tracking-wider text-slate-400 shrink-0">
              {{ issue.issueType?.replace(/_/g, ' ') || 'ISSUE' }}
            </span>
            <span v-if="issue.severity"
              class="text-[10px] font-semibold px-1.5 py-0.5 rounded"
              :class="severityColor(issue.severity)">
              {{ issue.severity }}
            </span>
          </div>
          <svg class="w-4 h-4 text-slate-400 transition-transform" :class="openIdx === idx ? 'rotate-180' : ''"
            fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
          </svg>
        </button>
        <div v-if="openIdx === idx" class="px-4 pb-3 space-y-2 border-t border-slate-100 pt-2">
          <p class="text-sm text-slate-700">{{ issue.description }}</p>
          <div v-if="issue.involvedFiles?.length" class="flex flex-wrap gap-1.5">
            <span v-for="f in issue.involvedFiles" :key="f"
              class="text-[10px] font-mono bg-slate-100 text-slate-600 px-1.5 py-0.5 rounded">
              {{ f }}
            </span>
          </div>
          <p v-if="issue.suggestion" class="text-xs text-slate-500">{{ issue.suggestion }}</p>
        </div>
      </div>
    </div>
  </div>
</template>
