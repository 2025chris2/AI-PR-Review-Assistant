<script setup>
defineProps({
  stageTab: { type: Number, default: 1 },
  taskId: { type: String, default: '' },
  appState: { type: String, default: 'input' },
})

const emit = defineEmits(['tab-change'])

const labels = ['输入', '分析', '结果']
const stepNames = ['一', '二', '三']
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
      <div class="flex items-center gap-2">
        <div class="flex bg-slate-100 rounded-lg p-0.5 gap-0.5">
          <button v-for="(label, idx) in labels" :key="idx"
            type="button"
            @click="emit('tab-change', idx + 1)"
            class="px-3 py-1.5 text-xs font-medium rounded-md transition-colors"
            :class="stageTab === idx + 1
              ? 'bg-white text-slate-900 shadow-sm'
              : 'text-slate-500 hover:text-slate-700'">
            第{{ stepNames[idx] }}步
          </button>
        </div>
        <div v-if="taskId && appState !== 'input'" class="flex items-center gap-2 ml-1">
          <span class="text-xs text-slate-400 font-mono">#{{ taskId }}</span>
          <span class="w-1.5 h-1.5 rounded-full"
            :class="appState === 'progress' ? 'bg-amber-400 animate-pulse' : 'bg-emerald-500'" />
        </div>
      </div>
    </div>
  </header>
</template>
