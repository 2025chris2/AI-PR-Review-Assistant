import { describe, it, expect } from 'vitest'
import { STAGES, EVENT_TYPES, INITIAL_STAGES } from '../pipeline.js'

describe('STAGES', () => {
  it('has three stages with required fields', () => {
    const ids = Object.keys(STAGES)
    expect(ids).toEqual(['l1', 'l2', 'l3'])
    for (const id of ids) {
      expect(STAGES[id]).toHaveProperty('id')
      expect(STAGES[id]).toHaveProperty('label')
      expect(STAGES[id]).toHaveProperty('desc')
      expect(STAGES[id]).toHaveProperty('event')
    }
  })

  it('each event matches dot format', () => {
    for (const stage of Object.values(STAGES)) {
      expect(stage.event).toMatch(/^\w+\.\w+$/)
    }
  })
})

describe('EVENT_TYPES', () => {
  it('has all expected event keys', () => {
    expect(EVENT_TYPES.TASK_STARTED).toBe('task.started')
    expect(EVENT_TYPES.L1_COMPLETE).toBe('l1.complete')
    expect(EVENT_TYPES.L2_FILE_START).toBe('l2.file.start')
    expect(EVENT_TYPES.L2_FILE_CHUNK).toBe('l2.file.chunk')
    expect(EVENT_TYPES.L2_FILE_COMPLETE).toBe('l2.file.complete')
    expect(EVENT_TYPES.L2_COMPLETE).toBe('l2.complete')
    expect(EVENT_TYPES.L3_START).toBe('l3.start')
    expect(EVENT_TYPES.L3_COMPLETE).toBe('l3.complete')
    expect(EVENT_TYPES.RESULT).toBe('result')
    expect(EVENT_TYPES.ERROR).toBe('error')
  })
})

describe('INITIAL_STAGES', () => {
  it('all stages start as pending', () => {
    expect(INITIAL_STAGES).toEqual({ l1: 'pending', l2: 'pending', l3: 'pending' })
  })
})
