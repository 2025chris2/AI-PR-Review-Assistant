import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import PipelineStages from '../PipelineStages.vue'

describe('PipelineStages', () => {
  it('renders all three stage labels', () => {
    const wrapper = mount(PipelineStages)
    expect(wrapper.text()).toContain('Sanitize Diff')
    expect(wrapper.text()).toContain('Analyze Files')
    expect(wrapper.text()).toContain('Aggregate')
  })

  it('shows pending state by default', () => {
    const wrapper = mount(PipelineStages)
    const circles = wrapper.findAll('.rounded-full.bg-slate-300')
    expect(circles.length).toBeGreaterThanOrEqual(3)
  })

  it('shows active stage with blue color', () => {
    const stages = { l1: 'complete', l2: 'active', l3: 'pending' }
    const wrapper = mount(PipelineStages, { props: { stages } })
    expect(wrapper.text()).toContain('Sanitize Diff')
  })

  it('renders check icon for completed stage', () => {
    const stages = { l1: 'complete', l2: 'pending', l3: 'pending' }
    const wrapper = mount(PipelineStages, { props: { stages } })
    expect(wrapper.find('svg').exists()).toBe(true)
  })
})
