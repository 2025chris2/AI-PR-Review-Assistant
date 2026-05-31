<script setup>
import { inject } from 'vue'

const review = inject('review')
</script>

<template>
  <header class="bg-white border-b border-slate-200 sticky top-0 z-10">
    <div class="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 h-14 flex items-center justify-between">
      <div class="flex items-center gap-3">
        <div class="w-8 h-8 rounded-lg bg-blue-600 flex items-center justify-center text-white font-bold text-sm">
          PR
        </div>
        <div>
          <h1 class="text-sm font-semibold text-slate-900 leading-tight">PR Review Assistant</h1>
          <p class="text-[11px] text-slate-500 leading-tight">AI-powered code review</p>
        </div>
      </div>
      <div v-if="review.taskId.value && review.appState.value !== 'input'" class="flex items-center gap-2">
        <span class="text-xs text-slate-400 font-mono">#{{ review.taskId.value }}</span>
        <span class="w-1.5 h-1.5 rounded-full"
          :class="review.appState.value === 'progress' ? 'bg-amber-400 animate-pulse' : 'bg-emerald-500'" />
      </div>
    </div>
  </header>
</template>
