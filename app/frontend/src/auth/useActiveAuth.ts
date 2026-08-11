import { useAuth } from 'react-oidc-context'
import { useDirectAuth } from './useDirectAuth'

// Ponto único de leitura de sessão: sessão OIDC (Hosted UI) tem prioridade,
// sessão direta (email/senha, dev local) é o fallback.
export function useActiveAuth() {
  const oidc = useAuth()
  const direct = useDirectAuth()

  const isAuthenticated = oidc.isAuthenticated || direct.isAuthenticated
  const isLoading = oidc.isLoading || direct.isLoading
  const token = oidc.user?.access_token ?? direct.session?.accessToken ?? ''

  async function signOut() {
    if (oidc.isAuthenticated) {
      await oidc.removeUser()
    }
    if (direct.isAuthenticated) {
      direct.signOut()
    }
  }

  return { isAuthenticated, isLoading, token, signOut, direct }
}
