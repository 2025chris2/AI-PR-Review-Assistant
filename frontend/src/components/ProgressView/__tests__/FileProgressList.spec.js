import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import FileProgressList from '../FileProgressList.vue'

describe('FileProgressList', () => {
  it('renders nothing when files empty', () => {
    const wrapper = mount(FileProgressList, { props: { files: [] } })
    expect(wrapper.find('h3').exists()).toBe(false)
  })

  it('renders file paths', () => {
    const files = [{ path: 'src/App.vue', status: 'analyzing' }]
    const wrapper = mount(FileProgressList, { props: { files } })
    expect(wrapper.text()).toContain('src/App.vue')
  })

  it('shows spinner for analyzing files', () => {
    const files = [{ path: 'a.java', status: 'analyzing' }]
    const wrapper = mount(FileProgressList, { props: { files } })
    expect(wrapper.find('.animate-spin').exists()).toBe(true)
  })

  it('shows check icon for completed files', () => {
    const files = [{ path: 'a.java', status: 'complete' }]
    const wrapper = mount(FileProgressList, { props: { files } })
    expect(wrapper.find('svg.text-emerald-500').exists()).toBe(true)
  })

  it('renders multiple files', () => {
    const files = [
      { path: 'a.java', status: 'analyzing' },
      { path: 'b.java', status: 'complete' },
    ]
    const wrapper = mount(FileProgressList, { props: { files } })
    const items = wrapper.findAll('.rounded-md')
    expect(items).toHaveLength(2)
  })

  it('shows chunk count for analyzing files', () => {
    const files = [{ path: 'a.java', status: 'analyzing', chunksDone: 3 }]
    const wrapper = mount(FileProgressList, { props: { files } })
    expect(wrapper.text()).toContain('3 chunks')
  })

  it('hides chunk count when chunksDone is 0', () => {
    const files = [{ path: 'a.java', status: 'analyzing', chunksDone: 0 }]
    const wrapper = mount(FileProgressList, { props: { files } })
    expect(wrapper.text()).not.toContain('chunks')
  })

  it('hides chunk count for completed files', () => {
    const files = [{ path: 'a.java', status: 'complete', chunksDone: 5 }]
    const wrapper = mount(FileProgressList, { props: { files } })
    expect(wrapper.text()).not.toContain('chunks')
  })
})
