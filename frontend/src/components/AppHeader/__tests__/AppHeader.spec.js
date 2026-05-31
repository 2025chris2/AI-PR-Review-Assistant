import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import AppHeader from '../AppHeader.vue'

describe('AppHeader', () => {
  it('renders title and subtitle', () => {
    const wrapper = mount(AppHeader)
    expect(wrapper.text()).toContain('PR Review Assistant')
    expect(wrapper.text()).toContain('AI-powered code review')
  })

  it('renders three step buttons', () => {
    const wrapper = mount(AppHeader)
    const buttons = wrapper.findAll('header button')
    expect(buttons).toHaveLength(3)
  })

  it('highlights active tab', () => {
    const wrapper = mount(AppHeader, { props: { stageTab: 2 } })
    const buttons = wrapper.findAll('header button')
    expect(buttons[1].classes()).toContain('bg-white')
    expect(buttons[1].classes()).toContain('shadow-sm')
  })

  it('emits tab-change on click', async () => {
    const wrapper = mount(AppHeader)
    const buttons = wrapper.findAll('header button')
    await buttons[1].trigger('click')
    expect(wrapper.emitted('tab-change')).toHaveLength(1)
    expect(wrapper.emitted('tab-change')[0]).toEqual([2])
  })

  it('hides task info when no taskId', () => {
    const wrapper = mount(AppHeader)
    expect(wrapper.text()).not.toContain('#')
  })

  it('shows task info when taskId provided', () => {
    const wrapper = mount(AppHeader, {
      props: { taskId: 'abc123', appState: 'progress' },
    })
    expect(wrapper.text()).toContain('#abc123')
  })

  it('shows pulse dot when progress', () => {
    const wrapper = mount(AppHeader, {
      props: { taskId: 'x', appState: 'progress' },
    })
    const dot = wrapper.find('.animate-pulse')
    expect(dot.exists()).toBe(true)
  })

  it('shows green dot when result', () => {
    const wrapper = mount(AppHeader, {
      props: { taskId: 'x', appState: 'result' },
    })
    const dot = wrapper.find('.bg-emerald-500')
    expect(dot.exists()).toBe(true)
  })
})
