<script setup>
import { ref, computed } from 'vue'

defineProps({
  loading: { type: Boolean, default: false },
})

const emit = defineEmits(['submit'])

const rawDiff = ref('')
const showMeta = ref(false)
const localError = ref('')
const meta = ref({
  prUrl: '',
  title: '',
  description: '',
  author: '',
  baseBranch: '',
  headBranch: '',
})

const PR_URL_REGEX = /^(?:https?:\/\/)?github\.com\/([^/]+)\/([^/]+)\/pull\/(\d+)(?:\/.*)?$/

const isPrUrl = computed(() => PR_URL_REGEX.test(rawDiff.value.trim()))

function handleSubmit() {
  if (!rawDiff.value.trim()) {
    localError.value = 'Please paste a diff to review'
    return
  }
  localError.value = ''
  emit('submit', { rawDiff: rawDiff.value, isPrUrl: isPrUrl.value, ...meta.value })
}
</script>

<template>
  <form @submit.prevent="handleSubmit" class="space-y-4">
    <div>
      <label class="block text-sm font-medium text-slate-700 mb-1.5">Git Diff</label>
      <textarea
        v-model="rawDiff"
        rows="12"
        placeholder="Paste the output of `git diff` or a unified diff here...&#10;&#10;Or paste a GitHub PR URL like:&#10;https://github.com/owner/repo/pull/123"
        class="w-full px-3 py-2.5 text-sm font-mono border border-slate-300 rounded-lg
               placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500
               resize-y"
      />
      <div v-if="isPrUrl && !loading"
        class="mt-2 flex items-center gap-2 text-xs text-blue-600 bg-blue-50 rounded-lg px-3 py-2">
        <svg class="w-4 h-4 shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
            d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
        <span>Detected GitHub PR URL — will fetch the diff automatically (public repo, no token needed)</span>
      </div>
    </div>

    <button type="button" @click="showMeta = !showMeta"
      class="text-xs text-blue-600 hover:text-blue-700 font-medium">
      {{ showMeta ? 'Hide' : 'Show' }} optional metadata
    </button>

    <div v-if="showMeta" class="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-1">
      <div>
        <label class="block text-xs font-medium text-slate-600 mb-1">PR URL</label>
        <input v-model="meta.prUrl" type="url" placeholder="https://github.com/..." class="w-full px-3 py-2 text-sm border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500" />
      </div>
      <div>
        <label class="block text-xs font-medium text-slate-600 mb-1">Title</label>
        <input v-model="meta.title" type="text" placeholder="PR title" class="w-full px-3 py-2 text-sm border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500" />
      </div>
      <div class="sm:col-span-2">
        <label class="block text-xs font-medium text-slate-600 mb-1">Description</label>
        <input v-model="meta.description" type="text" placeholder="PR description" class="w-full px-3 py-2 text-sm border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500" />
      </div>
      <div>
        <label class="block text-xs font-medium text-slate-600 mb-1">Author</label>
        <input v-model="meta.author" type="text" placeholder="github-username" class="w-full px-3 py-2 text-sm border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500" />
      </div>
      <div>
        <label class="block text-xs font-medium text-slate-600 mb-1">Base Branch</label>
        <input v-model="meta.baseBranch" type="text" placeholder="main" class="w-full px-3 py-2 text-sm border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500" />
      </div>
      <div>
        <label class="block text-xs font-medium text-slate-600 mb-1">Head Branch</label>
        <input v-model="meta.headBranch" type="text" placeholder="feature-branch" class="w-full px-3 py-2 text-sm border border-slate-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500" />
      </div>
    </div>

    <div v-if="localError" class="text-sm text-red-600 bg-red-50 rounded-lg px-3 py-2">
      {{ localError }}
    </div>

    <button type="submit" :disabled="loading"
      class="w-full py-2.5 px-4 text-sm font-semibold text-white bg-blue-600 rounded-lg
             hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed
             transition-colors flex items-center justify-center gap-2">
      <svg v-if="loading" class="animate-spin h-4 w-4" viewBox="0 0 24 24" fill="none">
        <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
        <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
      </svg>
      {{ loading ? 'Starting Review...' : 'Start Review' }}
    </button>
  </form>
</template>
