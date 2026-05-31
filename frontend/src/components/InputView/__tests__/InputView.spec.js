import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import InputView from '../InputView.vue'

describe('InputView', () => {
  it('renders title and description', () => {
    const wrapper = mount(InputView)
    expect(wrapper.text()).toContain('Review a Pull Request')
    expect(wrapper.text()).toContain('AI-powered analysis')
  })

  it('shows RawDiffForm by default', () => {
    const wrapper = mount(InputView)
    expect(wrapper.findComponent({ name: 'RawDiffForm' }).exists()).toBe(true)
  })

  it('switches to GitHubForm on mode change', async () => {
    const wrapper = mount(InputView)
    const toggle = wrapper.findComponent({ name: 'InputMethodToggle' })
    expect(wrapper.findComponent({ name: 'GitHubForm' }).exists()).toBe(false)
    toggle.vm.$emit('update:modelValue', 'github')
    await wrapper.vm.$nextTick()
    expect(wrapper.findComponent({ name: 'RawDiffForm' }).exists()).toBe(false)
    expect(wrapper.findComponent({ name: 'GitHubForm' }).exists()).toBe(true)
  })
})
