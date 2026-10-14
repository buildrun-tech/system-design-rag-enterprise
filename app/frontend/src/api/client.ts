import { config } from '../config'
import type { ApiErrorBody } from './types'

const API_BASE_URL = config.apiBaseUrl

export class ApiError extends Error {
  status: number
  code: string

  constructor(status: number, body: ApiErrorBody) {
    super(body.message)
    this.status = status
    this.code = body.error
  }
}

let unauthorizedHandler: (() => void) | null = null

export function setUnauthorizedHandler(handler: (() => void) | null) {
  unauthorizedHandler = handler
}

// Spring devolve 401 com corpo vazio — só o status é confiável
export function handleUnauthorized() {
  unauthorizedHandler?.()
}

export async function apiFetch<T>(path: string, token: string, init: RequestInit = {}): Promise<T> {
  const isFormData = init.body instanceof FormData
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: {
      Authorization: `Bearer ${token}`,
      ...(isFormData ? {} : { 'Content-Type': 'application/json' }),
      ...init.headers,
    },
  })

  if (response.status === 401) {
    handleUnauthorized()
  }

  if (!response.ok) {
    const body: ApiErrorBody = await response.json().catch(() => ({
      error: 'UNKNOWN_ERROR',
      message: response.statusText,
    }))
    throw new ApiError(response.status, body)
  }

  if (response.status === 204) {
    return undefined as T
  }

  return response.json()
}
