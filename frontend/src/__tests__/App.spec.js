import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import App from '../App.vue'

beforeEach(() => {
  vi.stubGlobal('EventSource', vi.fn(() => ({
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    close: vi.fn(),
  })))
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('App', () => {
  it('renders AppHeader', () => {
    const wrapper = mount(App)
    expect(wrapper.findComponent({ name: 'AppHeader' }).exists()).toBe(true)
  })

  it('shows InputView by default', () => {
    const wrapper = mount(App)
    expect(wrapper.findComponent({ name: 'InputView' }).exists()).toBe(true)
  })

  it('provides review inject', () => {
    const wrapper = mount(App)
    const review = wrapper.vm.review
    expect(review).toBeDefined()
    expect(review.appState.value).toBe('input')
  })
})
