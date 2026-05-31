import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { useReview } from '../useReview.js'

let EventSourceSpy

beforeEach(() => {
  EventSourceSpy = vi.fn()
  EventSourceSpy.prototype.addEventListener = vi.fn()
  EventSourceSpy.prototype.removeEventListener = vi.fn()
  EventSourceSpy.prototype.close = vi.fn()
  vi.stubGlobal('EventSource', EventSourceSpy)
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('useReview', () => {
  it('starts with input state and stageTab=1', () => {
    const review = useReview()
    expect(review.appState.value).toBe('input')
    expect(review.stageTab.value).toBe(1)
  })

  it('submitRawDiff calls API and transitions to progress', async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 202,
      json: () => Promise.resolve({ taskId: 'abc', eventsUrl: '/e/abc', resultUrl: '/r/abc' }),
    })
    const review = useReview()
    await review.submitRawDiff({ rawDiff: 'test' })
    expect(review.taskId.value).toBe('abc')
    expect(review.appState.value).toBe('progress')
  })

  it('creates EventSource after submit', async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 202,
      json: () => Promise.resolve({ taskId: 'abc', eventsUrl: '/e/abc', resultUrl: '/r/abc' }),
    })
    const review = useReview()
    await review.submitRawDiff({ rawDiff: 'test' })
    expect(EventSourceSpy).toHaveBeenCalledWith('/e/abc')
  })

  it('resets state on reset()', () => {
    const review = useReview()
    review.appState.value = 'result'
    review.taskId.value = 'abc'
    review.reset()
    expect(review.appState.value).toBe('input')
    expect(review.stageTab.value).toBe(1)
    expect(review.taskId.value).toBeNull()
    expect(review.error.value).toBeNull()
    expect(review.report.value).toBeNull()
  })

  it('pipeline stages complete when appState becomes result', async () => {
    const review = useReview()
    review.appState.value = 'result'
    await vi.waitFor(() => {
      expect(review.pipelineStages.l1).toBe('complete')
      expect(review.pipelineStages.l2).toBe('complete')
      expect(review.pipelineStages.l3).toBe('complete')
    })
  })

  it('stageTab follows appState changes', async () => {
    const review = useReview()
    review.appState.value = 'progress'
    await vi.waitFor(() => expect(review.stageTab.value).toBe(2))
    review.appState.value = 'result'
    await vi.waitFor(() => expect(review.stageTab.value).toBe(3))
    review.appState.value = 'input'
    await vi.waitFor(() => expect(review.stageTab.value).toBe(1))
  })

  it('sets error on API failure', async () => {
    global.fetch = vi.fn().mockRejectedValue(new Error('Network error'))
    const review = useReview()
    await expect(review.submitRawDiff({ rawDiff: 'test' })).rejects.toThrow('Network error')
    expect(review.error.value).toBe('Network error')
  })

  it('submitByUrl posts to by-url endpoint', async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 202,
      json: () => Promise.resolve({ taskId: 'u', eventsUrl: '/e/u', resultUrl: '/r/u' }),
    })
    const review = useReview()
    await review.submitByUrl({ url: 'https://github.com/u/r/pull/1' })
    expect(review.taskId.value).toBe('u')
    expect(review.appState.value).toBe('progress')
  })

  it('submitGitHub posts to github endpoint', async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 202,
      json: () => Promise.resolve({ taskId: 'g', eventsUrl: '/e/g', resultUrl: '/r/g' }),
    })
    const review = useReview()
    await review.submitGitHub({ owner: 'u', repo: 'r', prNumber: 1, token: 't' })
    expect(review.taskId.value).toBe('g')
    expect(review.appState.value).toBe('progress')
  })
})
