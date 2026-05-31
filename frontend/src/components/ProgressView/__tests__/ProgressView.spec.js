import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ProgressView from '../ProgressView.vue'

const mockReview = {
  pipelineStages: { l1: 'pending', l2: 'pending', l3: 'pending' },
  currentStage: { value: null },
  appState: { value: 'progress' },
  taskId: { value: 'abc' },
  error: { value: '' },
  filesInProgress: { value: [] },
  reset: () => {},
}

const app = { provide: { review: mockReview } }

describe('ProgressView', () => {
  it('renders PipelineStages', () => {
    const wrapper = mount(ProgressView, { global: app })
    expect(wrapper.findComponent({ name: 'PipelineStages' }).exists()).toBe(true)
  })

  it('renders spinner when in progress', () => {
    const wrapper = mount(ProgressView, { global: app })
    expect(wrapper.findComponent({ name: 'ProgressSpinner' }).exists()).toBe(true)
  })

  it('shows error state', () => {
    mockReview.error.value = 'Something went wrong'
    const wrapper = mount(ProgressView, { global: app })
    expect(wrapper.text()).toContain('Something went wrong')
    mockReview.error.value = ''
  })

  it('hides spinner when error', () => {
    mockReview.error.value = 'Error'
    const wrapper = mount(ProgressView, { global: app })
    expect(wrapper.findComponent({ name: 'ProgressSpinner' }).exists()).toBe(false)
    mockReview.error.value = ''
  })
})
