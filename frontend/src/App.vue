<script setup>
import { provide } from 'vue'
import { useReview } from '@/composables/useReview'
import AppHeader from '@/components/AppHeader.vue'
import InputView from '@/components/input/InputView.vue'
import ProgressView from '@/components/progress/ProgressView.vue'
import ResultView from '@/components/result/ResultView.vue'

const review = useReview()
provide('review', review)
</script>

<template>
  <div class="min-h-screen flex flex-col">
    <AppHeader />
    <main class="flex-1">
      <InputView v-if="review.appState.value === 'input'" />
      <ProgressView v-if="review.appState.value === 'progress'" />
      <ResultView v-if="review.appState.value === 'result'" />
    </main>
  </div>
</template>
