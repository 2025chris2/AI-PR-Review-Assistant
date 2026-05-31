<script setup>
import { provide } from 'vue'
import { useReview } from '@/composables/useReview'
import AppHeader from '@/components/AppHeader/AppHeader.vue'
import InputView from '@/components/InputView/InputView.vue'
import ProgressView from '@/components/ProgressView/ProgressView.vue'
import ResultView from '@/components/ResultView/ResultView.vue'

const review = useReview()
provide('review', review)
</script>

<template>
  <div class="min-h-screen bg-slate-50 flex flex-col">
    <AppHeader />
    <main class="flex-1">
      <InputView v-if="review.stageTab.value === 1" />
      <ProgressView v-if="review.stageTab.value === 2" />
      <ResultView v-if="review.stageTab.value === 3" />
    </main>
  </div>
</template>
