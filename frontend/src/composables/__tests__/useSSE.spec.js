import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useSSE } from '../useSSE.js'

let EventSourceSpy

beforeEach(() => {
  EventSourceSpy = vi.fn()
  EventSourceSpy.prototype.addEventListener = vi.fn()
  EventSourceSpy.prototype.removeEventListener = vi.fn()
  EventSourceSpy.prototype.close = vi.fn()
  vi.stubGlobal('EventSource', EventSourceSpy)
})

describe('useSSE', () => {
  it('starts disconnected', () => {
    const sse = useSSE('/events', {})
    expect(sse.isConnected.value).toBe(false)
  })

  it('creates EventSource with URL on connect', () => {
    const sse = useSSE('/events', {})
    sse.connect()
    expect(EventSourceSpy).toHaveBeenCalledWith('/events')
  })

  it('does not create duplicate EventSource on second connect', () => {
    const sse = useSSE('/events', {})
    sse.connect()
    sse.connect()
    expect(EventSourceSpy).toHaveBeenCalledTimes(1)
  })

  it('registers addEventListener for each handler', () => {
    const handler = () => {}
    const sse = useSSE('/events', { l1: handler, l2: handler })
    sse.connect()
    const es = EventSourceSpy.mock.results[0]?.value
    expect(es.addEventListener).toHaveBeenCalledTimes(2)
    expect(es.addEventListener).toHaveBeenCalledWith('l1', expect.any(Function))
    expect(es.addEventListener).toHaveBeenCalledWith('l2', expect.any(Function))
  })

  it('closes EventSource on close()', () => {
    const sse = useSSE('/events', {})
    sse.connect()
    const es = EventSourceSpy.mock.results[0]?.value
    sse.close()
    expect(es.close).toHaveBeenCalled()
    expect(sse.isConnected.value).toBe(false)
  })

  it('removes event listeners on close()', () => {
    const handler = () => {}
    const sse = useSSE('/events', { myEvent: handler })
    sse.connect()
    const es = EventSourceSpy.mock.results[0]?.value
    sse.close()
    expect(es.removeEventListener).toHaveBeenCalled()
    expect(es.close).toHaveBeenCalled()
  })

  it('calls error handler when EventSource errors', () => {
    const errorHandler = vi.fn()
    const sse = useSSE('/events', { error: errorHandler })
    sse.connect()
    const es = EventSourceSpy.mock.results[0]?.value
    // Trigger onerror
    es.onerror?.()
    expect(errorHandler).toHaveBeenCalledWith(
      expect.objectContaining({ data: { recoverable: false } }),
    )
  })
})
