<script setup>
defineProps({
  level: { type: String, default: 'LOW' },
  size: { type: String, default: 'md' },
})

const colors = {
  HIGH: { bg: 'bg-red-100', text: 'text-red-700', dot: 'bg-red-500' },
  MEDIUM: { bg: 'bg-amber-100', text: 'text-amber-700', dot: 'bg-amber-500' },
  LOW: { bg: 'bg-emerald-100', text: 'text-emerald-700', dot: 'bg-emerald-500' },
}

const sizes = {
  sm: 'text-[10px] px-1.5 py-0.5 gap-1',
  md: 'text-xs px-2.5 py-1 gap-1.5',
  lg: 'text-sm px-3 py-1.5 gap-2',
}
</script>

<template>
  <span class="inline-flex items-center font-semibold rounded-full"
    :class="[colors[level]?.bg || colors.LOW.bg, colors[level]?.text || colors.LOW.text, sizes[size] || sizes.md]">
    <span class="w-1.5 h-1.5 rounded-full"
      :class="colors[level]?.dot || colors.LOW.dot" />
    {{ level }}
  </span>
</template>
