import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import RawDiffForm from '../RawDiffForm.vue'

describe('RawDiffForm', () => {
  it('shows error on empty submit', async () => {
    const wrapper = mount(RawDiffForm)
    await wrapper.find('form').trigger('submit.prevent')
    expect(wrapper.text()).toContain('Please paste a diff to review')
  })

  it('emits submit with content', async () => {
    const wrapper = mount(RawDiffForm)
    const textarea = wrapper.find('textarea')
    await textarea.setValue('diff content')
    await wrapper.find('form').trigger('submit.prevent')
    expect(wrapper.emitted('submit')).toHaveLength(1)
    expect(wrapper.emitted('submit')[0][0].rawDiff).toBe('diff content')
  })

  it('detects GitHub PR URL', async () => {
    const wrapper = mount(RawDiffForm)
    const textarea = wrapper.find('textarea')
    await textarea.setValue('https://github.com/user/repo/pull/123')
    expect(wrapper.text()).toContain('Detected GitHub PR URL')
  })

  it('shows optional metadata section on toggle', async () => {
    const wrapper = mount(RawDiffForm)
    expect(wrapper.text()).not.toContain('Base Branch')
    await wrapper.find('button[type="button"]').trigger('click')
    expect(wrapper.text()).toContain('Base Branch')
  })

  it('disables submit when loading', () => {
    const wrapper = mount(RawDiffForm, { props: { loading: true } })
    expect(wrapper.find('button[type="submit"]').attributes('disabled')).toBeDefined()
  })

  it('shows spinner when loading', () => {
    const wrapper = mount(RawDiffForm, { props: { loading: true } })
    expect(wrapper.find('svg').exists()).toBe(true)
  })
})
