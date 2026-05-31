<script setup>
defineProps({
  modelValue: { type: String, default: 'raw' },
})

const emit = defineEmits(['update:modelValue'])

const modes = [
  { key: 'raw', label: 'Paste Raw Diff' },
  { key: 'github', label: 'From GitHub PR' },
]
</script>

<template>
  <div class="flex mb-6 bg-slate-100 rounded-lg p-1">
    <button
      v-for="mode in modes"
      :key="mode.key"
      type="button"
      @click="emit('update:modelValue', mode.key)"
      class="flex-1 px-4 py-2 text-sm font-medium rounded-md transition-all duration-200"
      :class="modelValue === mode.key
        ? 'bg-white text-slate-900 shadow-sm'
        : 'text-slate-500 hover:text-slate-700'"
    >
      {{ mode.label }}
    </button>
  </div>
</template>
