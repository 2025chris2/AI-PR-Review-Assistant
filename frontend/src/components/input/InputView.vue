<script setup>
import { inject } from 'vue'
import InputMethodToggle from './InputMethodToggle.vue'
import RawDiffForm from './RawDiffForm.vue'
import GitHubForm from './GitHubForm.vue'

const review = inject('review')
</script>

<template>
  <div class="min-h-[calc(100vh-3.5rem)] flex items-start justify-center pt-12 sm:pt-24 px-4">
    <div class="w-full max-w-2xl">
      <div class="text-center mb-8">
        <h2 class="text-2xl font-bold text-slate-900">Review a Pull Request</h2>
        <p class="mt-2 text-sm text-slate-500">Paste a diff or connect a GitHub PR for AI-powered analysis</p>
      </div>

      <div class="bg-white rounded-xl shadow-sm border border-slate-200 p-6">
        <InputMethodToggle />

        <RawDiffForm v-if="review.inputMode.value === 'raw'" />
        <GitHubForm v-else />
      </div>
    </div>
  </div>
</template>
