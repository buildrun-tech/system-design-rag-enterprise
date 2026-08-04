import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuth } from 'react-oidc-context'

export default function ProtectedRoute({ children }: { children: ReactNode }) {
  const auth = useAuth()

  if (auth.isLoading) {
    return <p>Carregando...</p>
  }

  if (!auth.isAuthenticated) {
    return <Navigate to="/" replace />
  }

  return <>{children}</>
}
