import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import InputMethodToggle from '../InputMethodToggle.vue'

describe('InputMethodToggle', () => {
  it('renders two mode buttons', () => {
    const wrapper = mount(InputMethodToggle)
    const buttons = wrapper.findAll('button')
    expect(buttons).toHaveLength(2)
    expect(buttons[0].text()).toContain('Paste Raw Diff')
    expect(buttons[1].text()).toContain('From GitHub PR')
  })

  it('highlights the active mode', () => {
    const wrapper = mount(InputMethodToggle, { props: { modelValue: 'github' } })
    const buttons = wrapper.findAll('button')
    expect(buttons[0].classes()).toContain('text-slate-500')
    expect(buttons[1].classes()).toContain('bg-white')
  })

  it('emits update:modelValue on click', async () => {
    const wrapper = mount(InputMethodToggle)
    const buttons = wrapper.findAll('button')
    await buttons[1].trigger('click')
    expect(wrapper.emitted('update:modelValue')).toHaveLength(1)
    expect(wrapper.emitted('update:modelValue')[0]).toEqual(['github'])
  })

  it('defaults to raw mode', () => {
    const wrapper = mount(InputMethodToggle)
    const buttons = wrapper.findAll('button')
    expect(buttons[0].classes()).toContain('bg-white')
  })
})
