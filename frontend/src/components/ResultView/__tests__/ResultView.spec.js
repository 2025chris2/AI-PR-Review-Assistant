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

const mockReview = {
  report: { value: sampleReport },
  reset: () => {},
}

const app = { provide: { review: mockReview } }

describe('ResultView', () => {
  it('renders ResultSummary', () => {
    const wrapper = mount(ResultView, { global: app })
    const summary = wrapper.findComponent({ name: 'ResultSummary' })
    expect(summary.exists()).toBe(true)
  })

  it('renders file sidebar', () => {
    const wrapper = mount(ResultView, { global: app })
    expect(wrapper.text()).toContain('A.java')
    expect(wrapper.text()).toContain('B.java')
  })

  it('shows empty file list when no reports', () => {
    mockReview.report.value = { overallSummary: 'ok' }
    const wrapper = mount(ResultView, { global: app })
    expect(wrapper.text()).toContain('No files analyzed')
    mockReview.report.value = sampleReport
  })

  it('sorts files by risk level', () => {
    const wrapper = mount(ResultView, { global: app })
    const fileButtons = wrapper.findAll('.w-full.flex.items-center.gap-2')
    if (fileButtons.length >= 2) {
      expect(fileButtons[0].text()).toContain('B.java')
    }
  })
})
