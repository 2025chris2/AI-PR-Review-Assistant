<script setup>
import { inject } from 'vue'
import PipelineStages from './PipelineStages.vue'
import FileProgressList from './FileProgressList.vue'
import ProgressSpinner from './ProgressSpinner.vue'

const review = inject('review')
</script>

<template>
  <div class="max-w-3xl mx-auto px-4 sm:px-6 py-12">
    <PipelineStages />

    <div v-if="review.error.value" class="mt-8 bg-red-50 border border-red-200 rounded-xl p-4 text-center">
      <p class="text-sm text-red-700">{{ review.error.value }}</p>
      <button @click="review.reset()"
        class="mt-3 text-sm font-medium text-red-600 hover:text-red-800 underline">
        Start a new review
      </button>
    </div>

    <template v-if="!review.error.value">
      <FileProgressList />
      <div class="mt-10">
        <ProgressSpinner />
      </div>
    </template>
  </div>
</template>
