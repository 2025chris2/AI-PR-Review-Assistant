import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import GitHubForm from '../GitHubForm.vue'

describe('GitHubForm', () => {
  it('shows error on empty submit', async () => {
    const wrapper = mount(GitHubForm)
    await wrapper.find('form').trigger('submit.prevent')
    expect(wrapper.text()).toContain('All fields are required')
  })

  it('emits submit with form data', async () => {
    const wrapper = mount(GitHubForm)
    await wrapper.find('input[placeholder*="facebook"]').setValue('facebook')
    await wrapper.find('input[placeholder*="react"]').setValue('react')
    await wrapper.find('input[type="number"]').setValue(123)
    await wrapper.find('input[placeholder*="ghp"]').setValue('ghp_test')
    await wrapper.find('form').trigger('submit.prevent')
    expect(wrapper.emitted('submit')).toHaveLength(1)
    const data = wrapper.emitted('submit')[0][0]
    expect(data.owner).toBe('facebook')
    expect(data.repo).toBe('react')
    expect(data.prNumber).toBe(123)
    expect(data.token).toBe('ghp_test')
  })

  it('disables submit when loading', () => {
    const wrapper = mount(GitHubForm, { props: { loading: true } })
    expect(wrapper.find('button[type="submit"]').attributes('disabled')).toBeDefined()
  })

  it('toggles token visibility', async () => {
    const wrapper = mount(GitHubForm)
    expect(wrapper.find('input[type="password"]').exists()).toBe(true)
    await wrapper.find('button[type="button"]').trigger('click')
    expect(wrapper.find('input[type="text"]').exists()).toBe(true)
  })
})
