/**
 * Cliente HTTP do WineGuard. Base configurável por VITE_API_URL (padrão: /api, servido via proxy
 * do Vite em dev e via nginx em produção).
 */
const BASE = (import.meta.env.VITE_API_URL as string | undefined)?.replace(/\/$/, '') ?? '/api'

/** Erro no formato RFC 7807 devolvido pelo backend. */
export class ApiError extends Error {
  status: number
  title?: string
  errors?: Record<string, string>

  constructor(status: number, message: string, title?: string, errors?: Record<string, string>) {
    super(message)
    this.status = status
    this.title = title
    this.errors = errors
  }
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  let res: Response
  try {
    res = await fetch(BASE + path, {
      method,
      headers: body !== undefined ? { 'Content-Type': 'application/json' } : undefined,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    })
  } catch {
    throw new ApiError(0, 'Backend indisponível. Verifique se o WineGuard backend está em execução.')
  }
  if (res.status === 204) return undefined as T
  const text = await res.text()
  const data = text ? JSON.parse(text) : undefined
  if (!res.ok) {
    throw new ApiError(res.status, data?.detail ?? `Erro ${res.status}`, data?.title, data?.errors)
  }
  return data as T
}

export const api = {
  get: <T>(path: string) => request<T>('GET', path),
  post: <T>(path: string, body?: unknown) => request<T>('POST', path, body ?? {}),
  put: <T>(path: string, body: unknown) => request<T>('PUT', path, body),
  patch: <T>(path: string, body: unknown) => request<T>('PATCH', path, body),
  del: <T>(path: string) => request<T>('DELETE', path),
}
