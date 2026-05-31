const API_BASE = '/api/v1/reviews'

export async function createReview(payload) {
  const res = await fetch(API_BASE, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
  if (!res.ok) {
    const err = await res.json().catch(() => ({}))
    throw new Error(err.message || err.error || `Request failed (${res.status})`)
  }
  return res.json()
}

export async function createReviewFromGitHub(payload) {
  const res = await fetch(`${API_BASE}/github`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
  if (!res.ok) {
    const err = await res.json().catch(() => ({}))
    throw new Error(err.error || `Request failed (${res.status})`)
  }
  return res.json()
}

export async function createReviewByUrl(payload) {
  const res = await fetch(`${API_BASE}/by-url`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
  if (!res.ok) {
    const err = await res.json().catch(() => ({}))
    throw new Error(err.error || `Request failed (${res.status})`)
  }
  return res.json()
}

export async function fetchResult(resultUrl) {
  const res = await fetch(resultUrl)
  if (res.status === 202) return null
  if (!res.ok) {
    throw new Error(`Failed to fetch result (${res.status})`)
  }
  return res.json()
}
