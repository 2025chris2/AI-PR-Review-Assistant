import { ref } from 'vue'

export function useSSE(url, handlers = {}) {
  const isConnected = ref(false)
  let eventSource = null
  let cleanupFns = []

  function connect() {
    if (eventSource) return

    eventSource = new EventSource(url)

    eventSource.onopen = () => {
      isConnected.value = true
    }

    for (const [eventName, handler] of Object.entries(handlers)) {
      const fn = (e) => {
        try {
          handler(JSON.parse(e.data))
        } catch {
          handler(e.data)
        }
      }
      eventSource.addEventListener(eventName, fn)
      cleanupFns.push(() => eventSource.removeEventListener(eventName, fn))
    }

    eventSource.onerror = () => {
      isConnected.value = false
      if (handlers['error']) {
        handlers['error']({ data: { recoverable: false }, message: 'SSE connection lost' })
      }
    }
  }

  function close() {
    if (!eventSource) return
    cleanupFns.forEach(fn => fn())
    cleanupFns = []
    eventSource.onmessage = null
    eventSource.onerror = null
    eventSource.close()
    eventSource = null
    isConnected.value = false
  }

  return { isConnected, connect, close }
}
