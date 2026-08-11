import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useActiveAuth } from '../auth/useActiveAuth'
import { apiFetch, ApiError } from '../api/client'
import type { Notebook, Page } from '../api/types'
import UserMenu from '../components/UserMenu'
import Button from '../components/ui/Button'
import Input from '../components/ui/Input'
import Card from '../components/ui/Card'

export default function NotebooksPage() {
  const { token } = useActiveAuth()
  const navigate = useNavigate()

  const [notebooks, setNotebooks] = useState<Notebook[]>([])
  const [name, setName] = useState('')
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    apiFetch<Page<Notebook>>('/api/v1/notebooks', token).then((page) => setNotebooks(page.content))
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
    <main style={{ padding: 'var(--space-4)' }}>
      <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h1>Notebooks</h1>
        <UserMenu />
      </header>

      <form onSubmit={handleCreate} style={{ margin: 'var(--space-2) 0', display: 'flex', gap: 'var(--space-1)' }}>
        <Input
          value={name}
          onChange={(event) => setName(event.target.value)}
          placeholder="Nome do notebook"
          required
          style={{ flex: 1 }}
        />
        <Button type="submit">criar</Button>
      </form>
      {error && <p role="alert">{error}</p>}

      <ul style={{ listStyle: 'none', padding: 0, display: 'flex', flexDirection: 'column', gap: 'var(--space-1)' }}>
        {notebooks.map((notebook) => (
          <li key={notebook.id}>
            <Card style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span>{notebook.name}</span>
              <Button variant="secondary" onClick={() => navigate(`/notebooks/${notebook.id}`)}>abrir</Button>
            </Card>
          </li>
        ))}
      </ul>
    </main>
  )
}
