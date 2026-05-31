<script setup>
import RiskBadge from './RiskBadge.vue'

defineProps({
  overallSummary: { type: String, default: '' },
  prUrl: { type: String, default: '' },
  globalRiskReason: { type: String, default: '' },
  riskLevel: { type: String, default: '' },
})

const emit = defineEmits(['new-review'])
</script>

<template>
  <div class="bg-white rounded-xl shadow-sm border border-slate-200 px-5 py-4">
    <div class="flex items-center justify-between gap-4">
      <div class="min-w-0 flex-1">
        <div class="flex items-center gap-3">
          <h2 class="text-base font-bold text-slate-900 truncate">
            {{ overallSummary || 'Review Complete' }}
          </h2>
          <RiskBadge v-if="riskLevel" :level="riskLevel" size="sm" />
        </div>
        <p v-if="prUrl" class="mt-0.5 text-xs text-blue-600 truncate">
          <a :href="prUrl" target="_blank" rel="noopener noreferrer" class="hover:underline">{{ prUrl }}</a>
        </p>
        <p v-if="globalRiskReason" class="mt-1 text-xs text-slate-500 leading-relaxed">
          {{ globalRiskReason }}
        </p>
      </div>
      <button type="button" @click="emit('new-review')"
        class="shrink-0 px-3 py-1.5 text-xs font-medium text-slate-600 bg-slate-100 hover:bg-slate-200 rounded-lg transition-colors">
        New Review
      </button>
    </div>
  </div>
</template>
