<script setup>
defineProps({
  files: { type: Array, default: () => [] },
})
</script>

<template>
  <div v-if="files.length > 0" class="mt-10">
    <h3 class="text-sm font-semibold text-slate-700 mb-3">Files</h3>
    <div class="max-h-64 overflow-y-auto space-y-1 rounded-lg border border-slate-200 bg-white p-2">
      <div v-for="(file, idx) in files" :key="idx + '-' + (file.path || idx)"
        class="flex items-center gap-3 px-3 py-2 rounded-md text-sm transition-colors"
        :class="file.status === 'analyzing' ? 'bg-blue-50' : ''">
        <svg class="w-4 h-4 shrink-0 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
            d="M7 21h10a2 2 0 002-2V9.414a1 1 0 00-.293-.707l-5.414-5.414A1 1 0 0012.586 3H7a2 2 0 00-2 2v14a2 2 0 002 2z" />
        </svg>
        <span class="font-mono text-xs text-slate-700 truncate flex-1">{{ file.path }}</span>
        <span v-if="file.status === 'analyzing' && file.chunksDone > 0"
          class="text-[10px] text-blue-500 font-medium shrink-0">
          {{ file.chunksDone }} chunks
        </span>
        <svg v-if="file.status === 'complete'" class="w-4 h-4 shrink-0 text-emerald-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M5 13l4 4L19 7" />
        </svg>
        <svg v-else class="animate-spin w-4 h-4 shrink-0 text-blue-500" fill="none" viewBox="0 0 24 24">
          <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="3" />
          <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
        </svg>
      </div>
    </div>
  </div>
</template>
