<script setup>
import { inject, computed } from 'vue'

const review = inject('review')

const metrics = computed(() => {
  const r = review.report.value
  if (!r) return []

  const totalRisks = r.fileReports?.reduce((sum, f) => sum + (f.risks?.length || 0), 0) || 0
  const totalSuggestions = r.fileReports?.reduce((sum, f) => sum + (f.suggestions?.length || 0), 0) || 0
  const totalFiles = r.fileReports?.length || 0
  const time = r.analysisTimeMs

  return [
    { label: 'Files Analyzed', value: totalFiles },
    { label: 'Analysis Time', value: time ? `${(time / 1000).toFixed(1)}s` : '-' },
    { label: 'Risks Found', value: totalRisks },
    { label: 'Suggestions', value: totalSuggestions },
  ]
})
</script>

<template>
  <div class="grid grid-cols-2 sm:grid-cols-4 gap-3">
    <div v-for="m in metrics" :key="m.label"
      class="bg-white rounded-xl shadow-sm border border-slate-200 px-4 py-3 text-center">
      <div class="text-xl font-bold text-slate-900">{{ m.value }}</div>
      <div class="text-xs text-slate-500 mt-0.5">{{ m.label }}</div>
    </div>
  </div>
</template>
