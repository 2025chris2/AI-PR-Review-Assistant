<script setup>
import { inject } from 'vue'

const review = inject('review')
const files = review.report.value?.topPriorityFiles || []
</script>

<template>
  <div class="bg-white rounded-xl shadow-sm border border-slate-200 p-6">
    <h3 class="text-sm font-semibold text-slate-900 mb-3">Top Priority Files</h3>
    <div v-if="files.length === 0" class="text-sm text-slate-400 italic">
      No priority files identified
    </div>
    <ol class="space-y-1.5">
      <li v-for="(file, idx) in files" :key="idx"
        class="flex items-center gap-3 text-sm">
        <span class="w-5 h-5 rounded-full bg-slate-100 text-slate-500 text-xs flex items-center justify-center font-medium shrink-0">
          {{ idx + 1 }}
        </span>
        <span class="font-mono text-xs text-slate-700 break-all">{{ file }}</span>
      </li>
    </ol>
  </div>
</template>
