import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import CrossFileIssues from '../CrossFileIssues.vue'

describe('CrossFileIssues', () => {
  it('shows empty state when no issues', () => {
    const wrapper = mount(CrossFileIssues, { props: { issues: [] } })
    expect(wrapper.text()).toContain('未检测到跨文件问题')
  })

  it('renders issue type in header', () => {
    const issues = [{ issueType: 'TRANSACTION_MISSING', description: 'test' }]
    const wrapper = mount(CrossFileIssues, { props: { issues } })
    expect(wrapper.text()).toContain('TRANSACTION MISSING')
  })

  it('renders severity in header', () => {
    const issues = [{ issueType: 'BUG', severity: 'HIGH', description: 'test' }]
    const wrapper = mount(CrossFileIssues, { props: { issues } })
    expect(wrapper.text()).toContain('HIGH')
  })

  it('shows details after clicking the header', async () => {
    const issues = [{
      issueType: 'BUG',
      description: 'desc',
      involvedFiles: ['a.java', 'b.java'],
      suggestion: 'Add validation',
    }]
    const wrapper = mount(CrossFileIssues, { props: { issues } })
    // Hidden initially
    expect(wrapper.text()).not.toContain('desc')
    // Click to expand
    await wrapper.find('button').trigger('click')
    expect(wrapper.text()).toContain('desc')
    expect(wrapper.text()).toContain('a.java')
    expect(wrapper.text()).toContain('b.java')
    expect(wrapper.text()).toContain('Add validation')
    // Click again to collapse
    await wrapper.find('button').trigger('click')
    expect(wrapper.text()).not.toContain('desc')
  })

  it('defaults to empty array when no issues prop', () => {
    const wrapper = mount(CrossFileIssues)
    expect(wrapper.text()).toContain('未检测到跨文件问题')
  })
})
