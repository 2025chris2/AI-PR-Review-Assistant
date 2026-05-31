<script setup>
import PipelineStages from './PipelineStages.vue'
import FileProgressList from './FileProgressList.vue'
import ProgressSpinner from './ProgressSpinner.vue'

defineProps({
  stages: {
    type: Object,
    default: () => ({ l1: 'pending', l2: 'pending', l3: 'pending' }),
  },
  files: { type: Array, default: () => [] },
  statusText: { type: String, default: 'Starting analysis...' },
  error: { type: String, default: '' },
})

const emit = defineEmits(['reset'])
</script>

<template>
  <div class="max-w-3xl mx-auto px-4 sm:px-6 py-12">
    <PipelineStages :stages="stages" />

    <div v-if="error" class="mt-8 bg-red-50 border border-red-200 rounded-xl p-4 text-center">
      <p class="text-sm text-red-700">{{ error }}</p>
      <button type="button" @click="emit('reset')"
        class="mt-3 text-sm font-medium text-red-600 hover:text-red-800 underline">
        Start a new review
      </button>
    </div>

    <template v-if="!error">
      <FileProgressList :files="files" />
      <div class="mt-10">
        <ProgressSpinner :status-text="statusText" />
      </div>
    </template>
  </div>
</template>
