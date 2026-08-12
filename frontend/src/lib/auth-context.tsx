import { createContext, useContext, useState, type ReactNode } from 'react'

const TOKEN_STORAGE_KEY = 'sporya.accessToken'

interface AuthContextValue {
  accessToken: string | null
  setAccessToken: (token: string | null) => void
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [accessToken, setAccessTokenState] = useState<string | null>(() =>
    localStorage.getItem(TOKEN_STORAGE_KEY),
  )

  function setAccessToken(token: string | null) {
    if (token) {
      localStorage.setItem(TOKEN_STORAGE_KEY, token)
    } else {
      localStorage.removeItem(TOKEN_STORAGE_KEY)
    }
    setAccessTokenState(token)
  }

  return (
    <AuthContext.Provider value={{ accessToken, setAccessToken }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}
