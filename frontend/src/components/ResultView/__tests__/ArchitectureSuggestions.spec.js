import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ArchitectureSuggestions from '../ArchitectureSuggestions.vue'

describe('ArchitectureSuggestions', () => {
  it('shows empty state when no suggestions', () => {
    const wrapper = mount(ArchitectureSuggestions, { props: { suggestions: [] } })
    expect(wrapper.text()).toContain('暂无架构建议')
  })

  it('renders suggestion list', () => {
    const suggestions = ['Add caching layer', 'Use DTO pattern']
    const wrapper = mount(ArchitectureSuggestions, { props: { suggestions } })
    expect(wrapper.text()).toContain('Add caching layer')
    expect(wrapper.text()).toContain('Use DTO pattern')
  })

  it('renders multiple suggestions', () => {
    const suggestions = ['A', 'B', 'C']
    const wrapper = mount(ArchitectureSuggestions, { props: { suggestions } })
    const items = wrapper.findAll('li')
    expect(items).toHaveLength(3)
  })

  it('defaults to empty array', () => {
    const wrapper = mount(ArchitectureSuggestions)
    expect(wrapper.text()).toContain('暂无架构建议')
  })
})
