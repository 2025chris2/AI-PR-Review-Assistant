<script setup>
import { inject, ref } from 'vue'

const review = inject('review')

const owner = ref('')
const repo = ref('')
const prNumber = ref('')
const token = ref('')
const showToken = ref(false)
const submitting = ref(false)
const localError = ref('')

async function handleSubmit() {
  if (!owner.value.trim() || !repo.value.trim() || !prNumber.value || !token.value.trim()) {
    localError.value = 'All fields are required'
    return
  }
  if (!/^\d+$/.test(prNumber.value) || Number(prNumber.value) <= 0) {
    localError.value = 'PR number must be a positive integer'
    return
  }
  localError.value = ''
  submitting.value = true
  try {
    await review.submitGitHub({
      owner: owner.value.trim(),
      repo: repo.value.trim(),
      prNumber: Number(prNumber.value),
      token: token.value.trim(),
    })
  } catch (e) {
    localError.value = e.message
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <form @submit.prevent="handleSubmit" class="space-y-4">
    <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
      <div>
        <label class="block text-sm font-medium text-slate-700 mb-1.5">Owner</label>
        <input v-model="owner" type="text" placeholder="e.g. facebook" autocomplete="off"
          class="w-full px-3 py-2 text-sm border border-slate-300 rounded-lg
                 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500" />
      </div>
      <div>
        <label class="block text-sm font-medium text-slate-700 mb-1.5">Repository</label>
        <input v-model="repo" type="text" placeholder="e.g. react" autocomplete="off"
          class="w-full px-3 py-2 text-sm border border-slate-300 rounded-lg
                 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500" />
      </div>
      <div>
        <label class="block text-sm font-medium text-slate-700 mb-1.5">PR Number</label>
        <input v-model="prNumber" type="number" min="1" placeholder="e.g. 12345"
          class="w-full px-3 py-2 text-sm border border-slate-300 rounded-lg
                 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500" />
      </div>
      <div>
        <label class="block text-sm font-medium text-slate-700 mb-1.5">GitHub Token</label>
        <div class="relative">
          <input :type="showToken ? 'text' : 'password'" v-model="token" placeholder="ghp_..."
            autocomplete="off"
            class="w-full px-3 py-2 text-sm border border-slate-300 rounded-lg pr-10
                   focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500" />
          <button type="button" @click="showToken = !showToken"
            class="absolute right-2.5 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600">
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path v-if="!showToken" stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
              <path v-if="!showToken" stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
              <path v-if="showToken" stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                d="M13.875 18.825A10.05 10.05 0 0112 19c-4.478 0-8.268-2.943-9.543-7a9.97 9.97 0 011.563-3.029m5.858.908a3 3 0 114.243 4.243M9.878 9.878l4.242 4.242M9.88 9.88l-3.29-3.29m7.532 7.532l3.29 3.29M3 3l3.59 3.59m0 0A9.953 9.953 0 0112 5c4.478 0 8.268 2.943 9.543 7a10.025 10.025 0 01-4.132 5.411m0 0L21 21" />
            </svg>
          </button>
        </div>
      </div>
    </div>

    <p class="text-xs text-slate-400">
      Token requires <code class="text-xs bg-slate-100 px-1 rounded">repo</code> scope for private repos.
      <a href="https://github.com/settings/tokens" target="_blank" class="text-blue-600 hover:underline">Generate one</a>
    </p>

    <div v-if="localError" class="text-sm text-red-600 bg-red-50 rounded-lg px-3 py-2">
      {{ localError }}
    </div>

    <button type="submit" :disabled="submitting"
      class="w-full py-2.5 px-4 text-sm font-semibold text-white bg-blue-600 rounded-lg
             hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed
             transition-colors flex items-center justify-center gap-2">
      <svg v-if="submitting" class="animate-spin h-4 w-4" viewBox="0 0 24 24" fill="none">
        <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
        <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
      </svg>
      {{ submitting ? 'Fetching & Analyzing...' : 'Start Review' }}
    </button>
  </form>
</template>
