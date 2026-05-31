import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import AppHeader from '../AppHeader.vue'

const mockReview = {
  stageTab: { value: 1 },
  taskId: { value: '' },
  appState: { value: 'input' },
}

const app = {
  provide: { review: mockReview },
}

describe('AppHeader', () => {
  it('renders title and subtitle', () => {
    const wrapper = mount(AppHeader, { global: app })
    expect(wrapper.text()).toContain('PR Review Assistant')
    expect(wrapper.text()).toContain('AI-powered code review')
  })

  it('renders three step buttons', () => {
    const wrapper = mount(AppHeader, { global: app })
    const buttons = wrapper.findAll('header button')
    expect(buttons).toHaveLength(3)
  })

  it('highlights active tab', () => {
    mockReview.stageTab.value = 2
    const wrapper = mount(AppHeader, { global: app })
    const buttons = wrapper.findAll('header button')
    expect(buttons[1].classes()).toContain('bg-white')
    mockReview.stageTab.value = 1
  })

  it('sets stageTab on button click', async () => {
    const wrapper = mount(AppHeader, { global: app })
    const buttons = wrapper.findAll('header button')
    await buttons[2].trigger('click')
    expect(mockReview.stageTab.value).toBe(3)
  })

  it('hides task info when no taskId', () => {
    const wrapper = mount(AppHeader, { global: app })
    expect(wrapper.text()).not.toContain('#')
  })

  it('shows task info when taskId provided', () => {
    mockReview.taskId.value = 'abc123'
    mockReview.appState.value = 'progress'
    const wrapper = mount(AppHeader, { global: app })
    expect(wrapper.text()).toContain('#abc123')
    mockReview.taskId.value = ''
    mockReview.appState.value = 'input'
  })
})
