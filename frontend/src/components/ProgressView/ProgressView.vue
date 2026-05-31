<script setup>
import { inject, computed } from 'vue'
import PipelineStages from './PipelineStages.vue'
import FileProgressList from './FileProgressList.vue'
import ProgressSpinner from './ProgressSpinner.vue'

const review = inject('review')

const spinnerText = computed(() => {
  if (review.pipelineStages.l3 === 'active') return 'Generating global report...'
  if (review.pipelineStages.l2 === 'active' || review.currentStage.value === 'l2') return 'Analyzing files...'
  if (review.pipelineStages.l1 === 'active') return 'Sanitizing diff...'
  return 'Starting analysis...'
})
</script>

<template>
  <div class="max-w-3xl mx-auto px-4 sm:px-6 py-12">
    <PipelineStages :stages="review.pipelineStages" />

    <div v-if="review.error.value" class="mt-8 bg-red-50 border border-red-200 rounded-xl p-4 text-center">
      <p class="text-sm text-red-700">{{ review.error.value }}</p>
      <button type="button" @click="review.reset()"
        class="mt-3 text-sm font-medium text-red-600 hover:text-red-800 underline">
        Start a new review
      </button>
    </div>

    <template v-if="review.appState.value === 'progress' && review.taskId.value && !review.error.value">
      <FileProgressList :files="review.filesInProgress.value" />
      <div class="mt-10">
        <ProgressSpinner :status-text="spinnerText" />
      </div>
    </template>
  </div>
</template>
