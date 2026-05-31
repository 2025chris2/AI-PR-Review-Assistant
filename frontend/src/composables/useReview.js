import { ref, reactive, shallowRef, watch } from 'vue'
import { createReview, createReviewFromGitHub, createReviewByUrl, fetchResult as fetchResultApi } from '@/services/api'
import { useSSE } from '@/composables/useSSE'
import { EVENT_TYPES, INITIAL_STAGES } from '@/constants/pipeline'

export function useReview() {
  const appState = ref('input')
  const stageTab = ref(1)
  const inputMode = ref('raw')

  watch(appState, (state) => {
    if (state === 'input') stageTab.value = 1
    else if (state === 'progress') stageTab.value = 2
    else if (state === 'result') {
      stageTab.value = 3
      pipelineStages.l1 = 'complete'
      pipelineStages.l2 = 'complete'
      pipelineStages.l3 = 'complete'
    }
  })

  const taskId = ref(null)
  const eventsUrl = ref(null)
  const resultUrl = ref(null)
  const pipelineStages = reactive({ ...INITIAL_STAGES })
  const currentStage = ref(null)
  const filesInProgress = ref([])
  const stageMessages = ref([])
  const error = ref(null)
  const report = shallowRef(null)

  let sse = null
  let pollTimer = null

  function addMessage(msg) {
    stageMessages.value.push(msg)
    if (stageMessages.value.length > 100) stageMessages.value.shift()
  }

  function submitRawDiff(payload) {
    error.value = null
    return _submit(() => createReview(payload))
  }

  function submitGitHub(payload) {
    error.value = null
    return _submit(() => createReviewFromGitHub(payload))
  }

  function submitByUrl(payload) {
    error.value = null
    return _submit(() => createReviewByUrl(payload))
  }

  async function _submit(apiCall) {
    report.value = null
    resetPipeline()
    filesInProgress.value = []
    stageMessages.value = []

    try {
      const data = await apiCall()
      taskId.value = data.taskId
      eventsUrl.value = data.eventsUrl
      resultUrl.value = data.resultUrl
      appState.value = 'progress'
      connectSSE()
      startPolling()
    } catch (e) {
      error.value = e.message
      throw e
    }
  }

  function resetPipeline() {
    pipelineStages.l1 = 'pending'
    pipelineStages.l2 = 'pending'
    pipelineStages.l3 = 'pending'
    currentStage.value = null
  }

  function connectSSE() {
    if (!eventsUrl.value) return

    sse = useSSE(eventsUrl.value, {
      [EVENT_TYPES.TASK_STARTED](_data) {
        addMessage('Task started')
      },
      [EVENT_TYPES.L1_COMPLETE](_data) {
        pipelineStages.l1 = 'complete'
        currentStage.value = 'l1'
        addMessage('Diff sanitization complete')
      },
      [EVENT_TYPES.L2_FILE_START](data) {
        pipelineStages.l1 = 'complete'
        pipelineStages.l2 = 'active'
        currentStage.value = 'l2'
        const path = data.filePath || data.data?.filePath || 'unknown'
        if (!filesInProgress.value.find(f => f.path === path)) {
          filesInProgress.value.push({ path, status: 'analyzing', chunksDone: 0 })
        }
        addMessage(`Analyzing ${path}`)
      },
      [EVENT_TYPES.L2_FILE_CHUNK](data) {
        const path = data.filePath || data.data?.filePath
        const file = filesInProgress.value.find(f => f.path === path)
        if (file) file.chunksDone = (file.chunksDone || 0) + 1
      },
      [EVENT_TYPES.L2_FILE_COMPLETE](data) {
        const path = data.filePath || data.data?.filePath
        const file = filesInProgress.value.find(f => f.path === path)
        if (file) file.status = 'complete'
      },
      [EVENT_TYPES.L2_COMPLETE](_data) {
        pipelineStages.l2 = 'complete'
        addMessage('All files analyzed')
      },
      [EVENT_TYPES.L3_START](_data) {
        pipelineStages.l3 = 'active'
        currentStage.value = 'l3'
        addMessage('Generating global report...')
      },
      [EVENT_TYPES.L3_COMPLETE](_data) {
        pipelineStages.l3 = 'complete'
        addMessage('Global report ready')
      },
      [EVENT_TYPES.RESULT](_data) {
        addMessage('Analysis complete')
        sse?.close()
        stopPolling()
        fetchResult()
      },
      error(e) {
        const isFatal = e?.data?.recoverable === false
        if (isFatal) {
          error.value = e.message || 'Analysis failed'
          sse?.close()
          stopPolling()
        }
      },
    })

    sse.connect()
  }

  function startPolling() {
    stopPolling()
    let retries = 0
    const MAX_RETRIES = 30
    pollTimer = setInterval(async () => {
      if (!resultUrl.value || report.value || error.value) return
      retries++
      if (retries > MAX_RETRIES) {
        stopPolling()
        sse?.close()
        error.value = 'Analysis timed out'
        return
      }
      try {
        const data = await fetchResultApi(resultUrl.value)
        if (data) {
          stopPolling()
          sse?.close()
          report.value = data
          appState.value = 'result'
        }
      } catch {
        // Polling errors are silent — SSE covers the happy path
      }
    }, 2000)
  }

  function stopPolling() {
    if (pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
    }
  }

  async function fetchResult() {
    if (!resultUrl.value) return
    let retries = 0
    const MAX_RETRIES = 15
    const tryFetch = async () => {
      try {
        const data = await fetchResultApi(resultUrl.value)
        if (data) {
          report.value = data
          appState.value = 'result'
        } else if (retries++ < MAX_RETRIES) {
          setTimeout(tryFetch, 1000)
        } else {
          error.value = 'Result fetch timed out'
        }
      } catch (e) {
        error.value = e.message
      }
    }
    tryFetch()
  }

  function reset() {
    sse?.close()
    sse = null
    stopPolling()
    appState.value = 'input'
    inputMode.value = 'raw'
    taskId.value = null
    eventsUrl.value = null
    resultUrl.value = null
    resetPipeline()
    currentStage.value = null
    filesInProgress.value = []
    stageMessages.value = []
    error.value = null
    report.value = null
  }

  return {
    appState, stageTab, inputMode, taskId, eventsUrl, resultUrl,
    pipelineStages, currentStage, filesInProgress,
    stageMessages, error, report,
    submitRawDiff, submitGitHub, submitByUrl, fetchResult, reset,
  }
}
