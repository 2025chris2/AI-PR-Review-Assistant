import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ProgressView from '../ProgressView.vue'

describe('ProgressView', () => {
  it('renders PipelineStages', () => {
    const wrapper = mount(ProgressView)
    expect(wrapper.findComponent({ name: 'PipelineStages' }).exists()).toBe(true)
  })

  it('renders ProgressSpinner', () => {
    const wrapper = mount(ProgressView)
    expect(wrapper.findComponent({ name: 'ProgressSpinner' }).exists()).toBe(true)
  })

  it('shows error state', () => {
    const wrapper = mount(ProgressView, {
      props: { error: 'Something went wrong' },
    })
    expect(wrapper.text()).toContain('Something went wrong')
    expect(wrapper.findComponent({ name: 'ProgressSpinner' }).exists()).toBe(false)
  })

  it('emits reset on error button click', async () => {
    const wrapper = mount(ProgressView, {
      props: { error: 'Error' },
    })
    await wrapper.find('button').trigger('click')
    expect(wrapper.emitted('reset')).toHaveLength(1)
  })

  it('renders FileProgressList when files provided', () => {
    const files = [{ path: 'a.java', status: 'analyzing' }]
    const wrapper = mount(ProgressView, { props: { files } })
    expect(wrapper.findComponent({ name: 'FileProgressList' }).exists()).toBe(true)
  })
})
