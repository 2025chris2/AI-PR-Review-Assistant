import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ResultSummary from '../ResultSummary.vue'

describe('ResultSummary', () => {
  it('renders overall summary', () => {
    const wrapper = mount(ResultSummary, {
      props: { overallSummary: 'Fix login bug' },
    })
    expect(wrapper.text()).toContain('Fix login bug')
  })

  it('shows default text when no summary', () => {
    const wrapper = mount(ResultSummary)
    expect(wrapper.text()).toContain('Review Complete')
  })

  it('renders PR URL link', () => {
    const wrapper = mount(ResultSummary, {
      props: { prUrl: 'https://github.com/user/repo/pull/1' },
    })
    expect(wrapper.find('a').attributes('href')).toBe('https://github.com/user/repo/pull/1')
    expect(wrapper.find('a').attributes('rel')).toContain('noopener')
  })

  it('renders risk reason', () => {
    const wrapper = mount(ResultSummary, {
      props: { globalRiskReason: 'Security concerns' },
    })
    expect(wrapper.text()).toContain('Security concerns')
  })

  it('renders RiskBadge when riskLevel is provided', () => {
    const wrapper = mount(ResultSummary, {
      props: { riskLevel: 'HIGH' },
    })
    expect(wrapper.findComponent({ name: 'RiskBadge' }).exists()).toBe(true)
  })

  it('emits new-review on button click', async () => {
    const wrapper = mount(ResultSummary)
    await wrapper.find('button').trigger('click')
    expect(wrapper.emitted('new-review')).toHaveLength(1)
  })
})
