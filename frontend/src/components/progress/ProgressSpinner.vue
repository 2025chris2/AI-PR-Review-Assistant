<script setup>
import { inject, computed } from 'vue'

const review = inject('review')

const messages = {
  l1: 'Sanitizing diff...',
  l2: 'Analyzing files...',
  l3: 'Generating global report...',
}

const statusText = computed(() => {
  if (review.pipelineStages.l3 === 'active') return messages.l3
  if (review.pipelineStages.l2 === 'active' || review.currentStage.value === 'l2') return messages.l2
  if (review.pipelineStages.l1 === 'active') return messages.l1
  return 'Starting analysis...'
})
</script>

<template>
  <div class="flex flex-col items-center gap-4">
    <div class="relative w-12 h-12">
      <svg class="animate-spin w-12 h-12 text-blue-500" viewBox="0 0 24 24" fill="none">
        <circle class="opacity-20" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="3" />
        <path class="opacity-90" fill="currentColor"
          d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
      </svg>
    </div>
    <p class="text-sm text-slate-500 animate-pulse">{{ statusText }}</p>
  </div>
</template>
