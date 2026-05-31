import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import App from '../App.vue'

describe('App', () => {
  it('renders AppHeader', () => {
    const wrapper = mount(App)
    expect(wrapper.findComponent({ name: 'AppHeader' }).exists()).toBe(true)
  })

  it('shows InputView by default (stageTab=1)', () => {
    const wrapper = mount(App)
    expect(wrapper.findComponent({ name: 'InputView' }).exists()).toBe(true)
  })

  it('switches to ResultView when tab is 3', async () => {
    const wrapper = mount(App)
    const header = wrapper.findComponent({ name: 'AppHeader' })
    header.vm.$emit('tab-change', 3)
    await wrapper.vm.$nextTick()
    expect(wrapper.findComponent({ name: 'ResultView' }).exists()).toBe(true)
  })

  it('switches to InputView on new-review', async () => {
    const wrapper = mount(App)
    const header = wrapper.findComponent({ name: 'AppHeader' })
    header.vm.$emit('tab-change', 3)
    await wrapper.vm.$nextTick()
    const result = wrapper.findComponent({ name: 'ResultView' })
    result.vm.$emit('new-review')
    await wrapper.vm.$nextTick()
    expect(wrapper.findComponent({ name: 'InputView' }).exists()).toBe(true)
  })
})
