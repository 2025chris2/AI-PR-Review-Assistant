import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ResultView from '../ResultView.vue'

const sampleReport = {
  overallSummary: 'Fix login bug',
  globalRiskLevel: 'HIGH',
  globalRiskReason: 'Security critical',
  prUrl: 'https://github.com/user/repo/pull/1',
  crossFileIssues: [{ issueType: 'BUG', description: 'test' }],
  architectureSuggestions: ['Add validation'],
  analysisTimeMs: 1200,
  fileReports: [
    {
      filePath: 'A.java',
      status: 'MODIFIED',
      riskLevel: 'LOW',
      overallSummary: 'minor fix',
      risks: [{ type: 'STYLE', line: 5, description: 'format' }],
      suggestions: [{ priority: 3, description: 'rename var' }],
    },
    {
      filePath: 'B.java',
      status: 'ADDED',
      riskLevel: 'HIGH',
      risks: [{ type: 'SECURITY', line: 10, description: 'sql injection' }],
    },
  ],
}

describe('ResultView', () => {
  it('renders ResultSummary with report data', () => {
    const wrapper = mount(ResultView, { props: { report: sampleReport } })
    expect(wrapper.findComponent({ name: 'ResultSummary' }).exists()).toBe(true)
    expect(wrapper.text()).toContain('Fix login bug')
  })

  it('renders file sidebar', () => {
    const wrapper = mount(ResultView, { props: { report: sampleReport } })
    expect(wrapper.text()).toContain('A.java')
    expect(wrapper.text()).toContain('B.java')
  })

  it('shows error banner when report has error', () => {
    const wrapper = mount(ResultView, {
      props: { report: { error: 'Analysis failed' } },
    })
    expect(wrapper.text()).toContain('Analysis failed')
  })

  it('shows empty file list state', () => {
    const wrapper = mount(ResultView, {
      props: { report: { overallSummary: 'ok' } },
    })
    expect(wrapper.text()).toContain('No files analyzed')
  })

  it('switches between tabs', async () => {
    const wrapper = mount(ResultView, { props: { report: sampleReport } })
    const buttons = wrapper.findAll('.flex.gap-1 button')
    expect(buttons.length).toBeGreaterThanOrEqual(3)
    await buttons[1].trigger('click')
    // After clicking "cross" tab, should see CrossFileIssues
    expect(wrapper.findComponent({ name: 'CrossFileIssues' }).exists()).toBe(true)
  })

  it('renders nothing when report is null', () => {
    const wrapper = mount(ResultView, { props: { report: null } })
    expect(wrapper.find('.bg-white').exists()).toBe(false)
  })

  it('sorts files by risk level HIGH first', () => {
    const wrapper = mount(ResultView, { props: { report: sampleReport } })
    const fileButtons = wrapper.findAll('.w-full.flex.items-center.gap-2')
    // B.java (HIGH) should come before A.java (LOW)
    expect(fileButtons[0].text()).toContain('B.java')
    expect(fileButtons[1].text()).toContain('A.java')
  })
})
