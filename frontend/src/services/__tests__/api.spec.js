import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { createReview, createReviewFromGitHub, createReviewByUrl, fetchResult } from '../api.js'

const mockFetch = vi.fn()
global.fetch = mockFetch

beforeEach(() => mockFetch.mockReset())
afterEach(() => mockFetch.mockReset())

function mockResponse(status, body) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: () => Promise.resolve(body),
  }
}

describe('createReview', () => {
  it('POSTs to /api/v1/reviews', async () => {
    mockFetch.mockResolvedValue(mockResponse(202, { taskId: 'abc' }))
    const result = await createReview({ rawDiff: 'test' })
    expect(mockFetch).toHaveBeenCalledWith(
      '/api/v1/reviews',
      expect.objectContaining({ method: 'POST' }),
    )
    expect(result.taskId).toBe('abc')
  })
})

describe('createReviewFromGitHub', () => {
  it('POSTs to /api/v1/reviews/github', async () => {
    mockFetch.mockResolvedValue(mockResponse(202, { taskId: 'abc' }))
    const result = await createReviewFromGitHub({ owner: 'user', repo: 'r', prNumber: 1, token: 't' })
    expect(mockFetch).toHaveBeenCalledWith(
      '/api/v1/reviews/github',
      expect.any(Object),
    )
    expect(result.taskId).toBe('abc')
  })
})

describe('createReviewByUrl', () => {
  it('POSTs to /api/v1/reviews/by-url', async () => {
    mockFetch.mockResolvedValue(mockResponse(202, { taskId: 'abc' }))
    const result = await createReviewByUrl({ url: 'https://github.com/u/r/pull/1' })
    expect(mockFetch).toHaveBeenCalledWith(
      '/api/v1/reviews/by-url',
      expect.any(Object),
    )
    expect(result.taskId).toBe('abc')
  })
})

describe('fetchResult', () => {
  it('returns null for 202', async () => {
    mockFetch.mockResolvedValue(mockResponse(202, {}))
    const result = await fetchResult('/some/url')
    expect(result).toBeNull()
  })

  it('returns data for 200', async () => {
    mockFetch.mockResolvedValue(mockResponse(200, { status: 'ok' }))
    const result = await fetchResult('/some/url')
    expect(result).toEqual({ status: 'ok' })
  })

  it('throws on error status', async () => {
    mockFetch.mockResolvedValue({
      ok: false,
      status: 500,
      json: () => Promise.resolve({ error: 'server error' }),
    })
    await expect(fetchResult('/bad')).rejects.toThrow()
  })

  it('throws with server error message', async () => {
    mockFetch.mockResolvedValue({
      ok: false,
      status: 400,
      json: () => Promise.resolve({ message: 'Invalid diff' }),
    })
    await expect(fetchResult('/bad')).rejects.toThrow('Invalid diff')
  })
})

describe('empty response body handling', () => {
  it('createReview handles empty 2xx body gracefully', async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.reject(new SyntaxError('Unexpected end of JSON input')),
    })
    const result = await createReview({ rawDiff: 'test' })
    expect(result).toEqual({})
  })

  it('fetchResult handles empty 200 body gracefully', async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      status: 200,
      json: () => Promise.reject(new SyntaxError('Unexpected end of JSON input')),
    })
    const result = await fetchResult('/some/url')
    expect(result).toBeNull()
  })
})

describe('request timeout', () => {
  it('POST functions include AbortController signal', async () => {
    mockFetch.mockResolvedValue(mockResponse(202, { taskId: 't' }))
    await createReview({ rawDiff: 'x' })
    const call = mockFetch.mock.calls[0]
    expect(call[1]).toHaveProperty('signal')
  })
})
