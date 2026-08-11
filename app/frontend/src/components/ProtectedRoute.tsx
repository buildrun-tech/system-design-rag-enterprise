import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { useActiveAuth } from '../auth/useActiveAuth'

export default function ProtectedRoute({ children }: { children: ReactNode }) {
  const auth = useActiveAuth()

  if (auth.isLoading) {
    return <p>Carregando...</p>
  }

  if (!auth.isAuthenticated) {
    return <Navigate to="/" replace />
  }

  return <>{children}</>
}
