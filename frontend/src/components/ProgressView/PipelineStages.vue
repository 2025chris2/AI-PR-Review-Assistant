<script setup>
defineProps({
  stages: {
    type: Object,
    default: () => ({ l1: 'pending', l2: 'pending', l3: 'pending' }),
  },
})

const stageList = [
  { id: 'l1', label: 'Sanitize Diff', desc: 'Strip metadata noise' },
  { id: 'l2', label: 'Analyze Files', desc: 'Review each file' },
  { id: 'l3', label: 'Aggregate', desc: 'Cross-file analysis' },
]

function stageIcon(state) {
  if (state === 'complete') return 'check'
  if (state === 'active') return 'spinner'
  if (state === 'error') return 'error'
  return 'pending'
}

function stageColor(state) {
  if (state === 'complete') return 'bg-emerald-500'
  if (state === 'active') return 'bg-blue-500'
  if (state === 'error') return 'bg-red-500'
  return 'bg-slate-300'
}

function connectorColor(state) {
  if (state === 'complete') return 'bg-emerald-400'
  return 'bg-slate-200'
}
</script>

<template>
  <div class="flex items-center justify-center gap-0">
    <template v-for="(stage, idx) in stageList" :key="stage.id">
      <div class="flex flex-col items-center">
        <div class="w-10 h-10 rounded-full flex items-center justify-center transition-colors duration-300"
          :class="stageColor(stages[stage.id])">
          <svg v-if="stageIcon(stages[stage.id]) === 'check'" class="w-5 h-5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M5 13l4 4L19 7" />
          </svg>
          <svg v-else-if="stageIcon(stages[stage.id]) === 'spinner'" class="animate-spin w-5 h-5 text-white" fill="none" viewBox="0 0 24 24">
            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="3" />
            <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
          </svg>
          <svg v-else-if="stageIcon(stages[stage.id]) === 'error'" class="w-5 h-5 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
          </svg>
          <div v-else class="w-2.5 h-2.5 rounded-full bg-white/60" />
        </div>
        <span class="mt-2 text-xs font-medium"
          :class="stages[stage.id] === 'active' ? 'text-blue-600' : 'text-slate-500'">
          {{ stage.label }}
        </span>
        <span class="text-[10px] text-slate-400 mt-0.5">{{ stage.desc }}</span>
      </div>

      <div v-if="idx < stageList.length - 1" class="w-16 sm:w-24 h-0.5 mx-2 sm:mx-3 rounded-full transition-colors duration-500"
        :class="connectorColor(stages[stage.id])" />
    </template>
  </div>
</template>
