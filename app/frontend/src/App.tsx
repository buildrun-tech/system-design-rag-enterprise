import { useEffect, useRef } from 'react'
import { Routes, Route, useNavigate } from 'react-router-dom'
import { useAuth } from 'react-oidc-context'
import { handleUnauthorized, setUnauthorizedHandler } from './api/client'
import { useActiveAuth } from './auth/useActiveAuth'
import LoginPage from './pages/LoginPage'
import NotebooksPage from './pages/NotebooksPage'
import WorkspacePage from './pages/WorkspacePage'
import ProtectedRoute from './components/ProtectedRoute'

function App() {
  const { signOut } = useActiveAuth()
  const { events } = useAuth()
  const navigate = useNavigate()
  const onExpiredRef = useRef<() => void>(() => {})
  onExpiredRef.current = async () => {
    await signOut()
    navigate('/', { replace: true })
  }

  // 401 em apiFetch/SSE e falha de silent renew do OIDC → logout simples
  useEffect(() => {
    setUnauthorizedHandler(() => onExpiredRef.current())
    const removeRenewError = events.addSilentRenewError(handleUnauthorized)
    return () => {
      setUnauthorizedHandler(null)
      removeRenewError()
    }
  }, [events])

  return (
    <Routes>
      <Route path="/" element={<LoginPage />} />
      <Route
        path="/notebooks"
        element={
          <ProtectedRoute>
            <NotebooksPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/notebooks/:notebookId"
        element={
          <ProtectedRoute>
            <WorkspacePage />
          </ProtectedRoute>
        }
      />
    </Routes>
  )
}

export default App
