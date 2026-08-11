import { useCallback, useState } from 'react'
import * as directAuth from './directAuth'
import type { DirectAuthSession } from './directAuth'

export function useDirectAuth() {
  const [session, setSession] = useState<DirectAuthSession | null>(() => directAuth.getStoredSession())
  const [error, setError] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(false)

  const signIn = useCallback(async (email: string, password: string) => {
    setIsLoading(true)
    setError(null)
    try {
      const newSession = await directAuth.signIn(email, password)
      setSession(newSession)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Falha no login')
      throw err
    } finally {
      setIsLoading(false)
    }
  }, [])

  const signUp = useCallback(async (email: string, password: string) => {
    setIsLoading(true)
    setError(null)
    try {
      await directAuth.signUp(email, password)
      const newSession = await directAuth.signIn(email, password)
      setSession(newSession)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Falha no cadastro')
      throw err
    } finally {
      setIsLoading(false)
    }
  }, [])

  const signOut = useCallback(() => {
    directAuth.signOut()
    setSession(null)
  }, [])

  return {
    session,
    isAuthenticated: session !== null,
    isLoading,
    error,
    signIn,
    signUp,
    signOut,
  }
}
