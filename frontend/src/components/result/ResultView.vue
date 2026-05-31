<script setup>
import { inject, computed } from 'vue'
import ResultSummary from './ResultSummary.vue'
import MetricsBar from './MetricsBar.vue'
import TopPriorityFiles from './TopPriorityFiles.vue'
import CrossFileIssues from './CrossFileIssues.vue'
import ArchitectureSuggestions from './ArchitectureSuggestions.vue'
import FileReportCard from './FileReportCard.vue'

const review = inject('review')
const r = review.report

const sortedFiles = computed(() => {
  const reports = r.value?.fileReports || []
  const order = { HIGH: 0, MEDIUM: 1, LOW: 2, null: 3 }
  return [...reports].sort((a, b) => (order[a.riskLevel] ?? 3) - (order[b.riskLevel] ?? 3))
})
</script>

<template>
  <div class="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-6">
    <ResultSummary />
    <MetricsBar />

    <div v-if="r && r.error" class="bg-red-50 border border-red-200 rounded-xl p-4">
      <p class="text-sm text-red-700">{{ r.error }}</p>
    </div>

    <TopPriorityFiles />
    <CrossFileIssues />
    <ArchitectureSuggestions />

    <!-- File Reports -->
    <div>
      <h3 class="text-sm font-semibold text-slate-900 mb-3">File Reports</h3>
      <div class="space-y-2">
        <FileReportCard v-for="(file, idx) in sortedFiles" :key="idx" :report="file" />
      </div>
      <div v-if="!r?.fileReports?.length" class="text-sm text-slate-400 italic bg-white rounded-xl shadow-sm border border-slate-200 p-6">
        No files were analyzed
      </div>
    </div>
  </div>
</template>
