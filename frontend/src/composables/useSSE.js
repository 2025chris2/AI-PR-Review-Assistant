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

    eventSource.onmessage = (e) => {
      if (handlers['message']) {
        try {
          handlers['message'](JSON.parse(e.data))
        } catch {
          handlers['message'](e.data)
        }
      }
    }

    eventSource.onerror = () => {
      isConnected.value = false
      if (handlers['error']) {
        handlers['error'](new Error('SSE connection lost'))
      }
    }
  }

  function close() {
    if (!eventSource) return
    cleanupFns.forEach(fn => fn())
    cleanupFns = []
    eventSource.close()
    eventSource = null
    isConnected.value = false
  }

  return { isConnected, connect, close }
}
