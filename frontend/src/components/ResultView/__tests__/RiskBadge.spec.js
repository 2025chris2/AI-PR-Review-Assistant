import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import RiskBadge from '../RiskBadge.vue'

describe('RiskBadge', () => {
  it('renders level text', () => {
    const wrapper = mount(RiskBadge, { props: { level: 'HIGH' } })
    expect(wrapper.text()).toContain('HIGH')
  })

  it('applies HIGH colors to root span', () => {
    const wrapper = mount(RiskBadge, { props: { level: 'HIGH' } })
    expect(wrapper.classes()).toContain('bg-red-100')
    expect(wrapper.classes()).toContain('text-red-700')
  })

  it('applies MEDIUM colors to root span', () => {
    const wrapper = mount(RiskBadge, { props: { level: 'MEDIUM' } })
    expect(wrapper.classes()).toContain('bg-amber-100')
    expect(wrapper.classes()).toContain('text-amber-700')
  })

  it('applies LOW colors to root span', () => {
    const wrapper = mount(RiskBadge, { props: { level: 'LOW' } })
    expect(wrapper.classes()).toContain('bg-emerald-100')
    expect(wrapper.classes()).toContain('text-emerald-700')
  })

  it('renders nothing when level is empty', () => {
    const wrapper = mount(RiskBadge, { props: { level: '' } })
    expect(wrapper.find('span').exists()).toBe(false)
  })

  it('applies sm size', () => {
    const wrapper = mount(RiskBadge, { props: { level: 'LOW', size: 'sm' } })
    expect(wrapper.classes()).toContain('text-[10px]')
  })

  it('applies md size by default', () => {
    const wrapper = mount(RiskBadge, { props: { level: 'LOW' } })
    expect(wrapper.classes()).toContain('text-xs')
  })

  it('applies lg size', () => {
    const wrapper = mount(RiskBadge, { props: { level: 'LOW', size: 'lg' } })
    expect(wrapper.classes()).toContain('text-sm')
  })

  it('falls back to LOW for unknown level', () => {
    const wrapper = mount(RiskBadge, { props: { level: 'CRITICAL' } })
    expect(wrapper.classes()).toContain('bg-emerald-100')
  })
})
