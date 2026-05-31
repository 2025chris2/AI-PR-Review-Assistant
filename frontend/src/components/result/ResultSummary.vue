<script setup>
import { inject } from 'vue'
import RiskBadge from './RiskBadge.vue'

const review = inject('review')
const r = review.report
</script>

<template>
  <div v-if="r" class="bg-white rounded-xl shadow-sm border border-slate-200 p-6 sm:p-8">
    <div class="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-4">
      <div class="min-w-0">
        <h2 class="text-lg font-bold text-slate-900 leading-tight">
          {{ r.overallSummary || 'Review Complete' }}
        </h2>
        <p v-if="r.prUrl" class="mt-1 text-sm text-blue-600 truncate">
          <a :href="r.prUrl" target="_blank" class="hover:underline">{{ r.prUrl }}</a>
        </p>
      </div>
      <RiskBadge :level="r.globalRiskLevel" size="lg" />
    </div>

    <p v-if="r.globalRiskReason" class="mt-4 text-sm text-slate-600 leading-relaxed">
      {{ r.globalRiskReason }}
    </p>

    <div class="mt-6 flex items-center gap-3">
      <button @click="review.reset()"
        class="px-4 py-2 text-sm font-medium text-slate-600 bg-slate-100 hover:bg-slate-200 rounded-lg transition-colors">
        New Review
      </button>
    </div>
  </div>
</template>
