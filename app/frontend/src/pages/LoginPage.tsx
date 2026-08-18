import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from 'react-oidc-context'
import { useDirectAuth } from '../auth/useDirectAuth'
import Button from '../components/ui/Button'
import Card from '../components/ui/Card'
import Input from '../components/ui/Input'
import ThemeToggle from '../components/ThemeToggle'
import { IconGithub, IconGoogle } from '../components/icons'

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
    <main className="center-page">
      <div className="theme-toggle-corner">
        <ThemeToggle />
      </div>
      <Card className="stack login-card">
        <h1 className="login-title">NotebookLM</h1>
        <Button
          variant="secondary"
          className="full-width"
          onClick={() => auth.signinRedirect({ extraQueryParams: { identity_provider: 'Google' } })}
        >
          <IconGoogle />
          Login Google
        </Button>
        <Button
          variant="secondary"
          className="full-width"
          onClick={() => auth.signinRedirect({ extraQueryParams: { identity_provider: 'GitHub' } })}
        >
          <IconGithub />
          Login Github
        </Button>
        {auth.error && <p role="alert">{auth.error.message}</p>}

        {DIRECT_SIGNUP_ENABLED && (
          <>
            <p className="text-muted">ou entre com email e senha (dev local)</p>
            <form onSubmit={handleDirectLogin} className="stack full-width">
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
              <div className="row">
                <Button type="submit" className="flex-1" disabled={direct.isLoading}>
                  Entrar
                </Button>
                <Button type="button" variant="secondary" className="flex-1" disabled={direct.isLoading} onClick={handleDirectSignUp}>
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
