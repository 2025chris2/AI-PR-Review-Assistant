import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ProgressSpinner from '../ProgressSpinner.vue'

describe('ProgressSpinner', () => {
  it('renders spinning svg', () => {
    const wrapper = mount(ProgressSpinner)
    expect(wrapper.find('.animate-spin').exists()).toBe(true)
  })

  it('renders default status text', () => {
    const wrapper = mount(ProgressSpinner)
    expect(wrapper.text()).toContain('Starting analysis...')
  })

  it('renders custom status text', () => {
    const wrapper = mount(ProgressSpinner, {
      props: { statusText: 'Analyzing files...' },
    })
    expect(wrapper.text()).toContain('Analyzing files...')
  })
})
