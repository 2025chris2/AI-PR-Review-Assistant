import { describe, it, expect } from 'vitest'
import { ref } from 'vue'
import { mount } from '@vue/test-utils'
import InputView from '../InputView.vue'

const mockReview = {
  inputMode: ref('raw'),
  appState: ref('input'),
  submitRawDiff: () => {},
  submitGitHub: () => {},
  submitByUrl: () => {},
}

const app = { provide: { review: mockReview } }

describe('InputView', () => {
  it('renders title', () => {
    const wrapper = mount(InputView, { global: app })
    expect(wrapper.text()).toContain('Review a Pull Request')
  })

  it('shows Paste Raw Diff button (InputMethodToggle)', () => {
    const wrapper = mount(InputView, { global: app })
    expect(wrapper.text()).toContain('Paste Raw Diff')
  })

  it('shows Start Review button (RawDiffForm)', () => {
    const wrapper = mount(InputView, { global: app })
    expect(wrapper.text()).toContain('Start Review')
  })

  it('switches to GitHub mode', async () => {
    const wrapper = mount(InputView, { global: app })
    mockReview.inputMode.value = 'github'
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('From GitHub PR')
  })
})
