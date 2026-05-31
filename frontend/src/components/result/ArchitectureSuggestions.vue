<script setup>
import { inject } from 'vue'

const review = inject('review')
const suggestions = review.report.value?.architectureSuggestions || []
</script>

<template>
  <div class="bg-white rounded-xl shadow-sm border border-slate-200 p-6">
    <h3 class="text-sm font-semibold text-slate-900 mb-3">Architecture Suggestions</h3>
    <div v-if="suggestions.length === 0" class="text-sm text-slate-400 italic">
      No architecture suggestions
    </div>
    <ul class="space-y-2">
      <li v-for="(s, idx) in suggestions" :key="idx" class="flex gap-3 text-sm">
        <span class="w-1.5 h-1.5 rounded-full bg-blue-500 mt-1.5 shrink-0" />
        <span class="text-slate-700">{{ s }}</span>
      </li>
    </ul>
  </div>
</template>
