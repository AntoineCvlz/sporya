// Chemin relatif — même valeur en local (proxy Vite, voir vite.config.ts) et
// en prod (routage Ingress, voir infrastructure/kubernetes/ingress/). Pas de
// configuration d'URL de base ni de CORS à gérer.
const AUTH_BASE = '/api/v1/auth'

export class ApiError extends Error {
  status: number

  constructor(message: string, status: number) {
    super(message)
    this.status = status
  }
}

async function parseErrorMessage(response: Response): Promise<string> {
  try {
    const body = (await response.json()) as { message?: string }
    return body.message ?? response.statusText
  } catch {
    return response.statusText
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${AUTH_BASE}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...init?.headers,
    },
  })
  if (!response.ok) {
    throw new ApiError(await parseErrorMessage(response), response.status)
  }
  return (await response.json()) as T
}

export interface UserResponse {
  id: string
  email: string
}

export interface AuthResponse {
  accessToken: string
  tokenType: string
  expiresInSeconds: number
}

export function register(email: string, password: string): Promise<UserResponse> {
  return request<UserResponse>('/register', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  })
}

export function login(email: string, password: string): Promise<AuthResponse> {
  return request<AuthResponse>('/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  })
}

export function me(accessToken: string): Promise<UserResponse> {
  return request<UserResponse>('/me', {
    headers: { Authorization: `Bearer ${accessToken}` },
  })
}
