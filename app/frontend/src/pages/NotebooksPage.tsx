import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from 'react-oidc-context'
import { apiFetch, ApiError } from '../api/client'
import type { Notebook } from '../api/types'
import UserMenu from '../components/UserMenu'

export default function NotebooksPage() {
  const auth = useAuth()
  const token = auth.user?.access_token ?? ''
  const navigate = useNavigate()

  const [notebooks, setNotebooks] = useState<Notebook[]>([])
  const [name, setName] = useState('')
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    apiFetch<Notebook[]>('/api/v1/notebooks', token).then(setNotebooks)
  }, [token])

  async function handleCreate(event: React.FormEvent) {
    event.preventDefault()
    setError(null)
    try {
      const created = await apiFetch<Notebook>('/api/v1/notebooks', token, {
        method: 'POST',
        body: JSON.stringify({ name }),
      })
      setNotebooks((current) => [created, ...current])
      setName('')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Falha ao criar notebook')
    }
  }

  return (
    <main style={{ padding: '2rem' }}>
      <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h1>Notebooks</h1>
        <UserMenu />
      </header>

      <form onSubmit={handleCreate} style={{ margin: '1rem 0', display: 'flex', gap: '0.5rem' }}>
        <input
          value={name}
          onChange={(event) => setName(event.target.value)}
          placeholder="Nome do notebook"
          required
        />
        <button type="submit">criar</button>
      </form>
      {error && <p role="alert">{error}</p>}

      <ul style={{ listStyle: 'none', padding: 0, display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
        {notebooks.map((notebook) => (
          <li key={notebook.id} style={{ border: '1px solid #ccc', borderRadius: '0.5rem', padding: '1rem', display: 'flex', justifyContent: 'space-between' }}>
            <span>{notebook.name}</span>
            <button onClick={() => navigate(`/notebooks/${notebook.id}`)}>abrir</button>
          </li>
        ))}
      </ul>
    </main>
  )
}
