import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from 'react-oidc-context'
import { useDirectAuth } from '../auth/useDirectAuth'
import Button from '../components/ui/Button'
import Card from '../components/ui/Card'
import Input from '../components/ui/Input'

const DIRECT_SIGNUP_ENABLED = import.meta.env.VITE_ENABLE_DIRECT_SIGNUP === 'true'

export default function LoginPage() {
  const auth = useAuth()
  const direct = useDirectAuth()
  const navigate = useNavigate()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')

  useEffect(() => {
    if (auth.isAuthenticated || direct.isAuthenticated) {
      navigate('/notebooks', { replace: true })
    }
  }, [auth.isAuthenticated, direct.isAuthenticated, navigate])

  if (auth.isLoading) {
    return <p>Carregando...</p>
  }

  async function handleDirectLogin(event: React.FormEvent) {
    event.preventDefault()
    await direct.signIn(email, password).catch(() => {})
  }

  async function handleDirectSignUp() {
    await direct.signUp(email, password).catch(() => {})
  }

  return (
    <main style={{ display: 'flex', justifyContent: 'center', marginTop: '4rem' }}>
      <Card style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 'var(--space-2)', width: '320px' }}>
        <h1 style={{ fontSize: '32px', margin: 0 }}>NotebookLM</h1>
        <Button style={{ width: '100%' }} onClick={() => auth.signinRedirect({ extraQueryParams: { identity_provider: 'Google' } })}>
          Login Google
        </Button>
        <Button style={{ width: '100%' }} onClick={() => auth.signinRedirect({ extraQueryParams: { identity_provider: 'GitHub' } })}>
          Login Github
        </Button>
        {auth.error && <p role="alert">{auth.error.message}</p>}

        {DIRECT_SIGNUP_ENABLED && (
          <>
            <p style={{ margin: 0, color: 'var(--text)' }}>ou entre com email e senha (dev local)</p>
            <form onSubmit={handleDirectLogin} style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-1)', width: '100%' }}>
              <Input
                type="email"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                placeholder="email"
                required
              />
              <Input
                type="password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                placeholder="senha"
                required
              />
              <div style={{ display: 'flex', gap: 'var(--space-1)' }}>
                <Button type="submit" style={{ flex: 1 }} disabled={direct.isLoading}>
                  Entrar
                </Button>
                <Button type="button" variant="secondary" style={{ flex: 1 }} disabled={direct.isLoading} onClick={handleDirectSignUp}>
                  Cadastrar
                </Button>
              </div>
            </form>
            {direct.error && <p role="alert">{direct.error}</p>}
          </>
        )}
      </Card>
    </main>
  )
}
