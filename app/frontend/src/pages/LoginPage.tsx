import { useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from 'react-oidc-context'

export default function LoginPage() {
  const auth = useAuth()
  const navigate = useNavigate()

  useEffect(() => {
    if (auth.isAuthenticated) {
      navigate('/notebooks', { replace: true })
    }
  }, [auth.isAuthenticated, navigate])

  if (auth.isLoading) {
    return <p>Carregando...</p>
  }

  return (
    <main style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '1rem', marginTop: '4rem' }}>
      <h1>NotebookLM</h1>
      <button onClick={() => auth.signinRedirect({ extraQueryParams: { identity_provider: 'Google' } })}>
        Login Google
      </button>
      <button onClick={() => auth.signinRedirect({ extraQueryParams: { identity_provider: 'GitHub' } })}>
        Login Github
      </button>
      {auth.error && <p role="alert">{auth.error.message}</p>}
    </main>
  )
}
