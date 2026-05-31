const API_BASE = '/api/v1/reviews'
const REQUEST_TIMEOUT = 30_000

async function postJson(url, payload) {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), REQUEST_TIMEOUT)
  try {
    const res = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
      signal: controller.signal,
    })
    if (!res.ok) {
      const err = await res.json().catch(() => ({}))
      throw new Error(err.message || err.error || `Request failed (${res.status})`)
    }
    return res.json().catch(() => ({}))
  } finally {
    clearTimeout(timer)
  }
}

export function createReview(payload) {
  return postJson(API_BASE, payload)
}

export function createReviewFromGitHub(payload) {
  return postJson(`${API_BASE}/github`, payload)
}

export function createReviewByUrl(payload) {
  return postJson(`${API_BASE}/by-url`, payload)
}

export async function fetchResult(resultUrl) {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), REQUEST_TIMEOUT)
  try {
    const res = await fetch(resultUrl, { signal: controller.signal })
    if (res.status === 202) return null
    if (!res.ok) {
      const err = await res.json().catch(() => ({}))
      throw new Error(err.message || err.error || `Failed to fetch result (${res.status})`)
    }
    return res.json().catch(() => null)
  } finally {
    clearTimeout(timer)
  }
}
