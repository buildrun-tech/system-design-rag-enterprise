import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useActiveAuth } from '../auth/useActiveAuth'
import { apiFetch, ApiError } from '../api/client'
import type { Notebook, Page } from '../api/types'
import Navbar from '../components/Navbar'
import Modal from '../components/Modal'
import Button from '../components/ui/Button'
import Input from '../components/ui/Input'
import Card from '../components/ui/Card'
import { IconArrowRight, IconBook, IconPlus } from '../components/icons'

export default function NotebooksPage() {
  const { token } = useActiveAuth()
  const navigate = useNavigate()

  const [notebooks, setNotebooks] = useState<Notebook[]>([])
  const [name, setName] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [modalOpen, setModalOpen] = useState(false)

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
      setModalOpen(false)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Falha ao criar notebook')
    }
  }

  return (
    <div className="page">
      <Navbar title="NotebookLM" />

      <div className="page-body">
        <section className="section">
          <div className="section-header">
            <h2 className="section-title">Seus notebooks</h2>
            <Button onClick={() => setModalOpen(true)}>
              <IconPlus size={16} />
              novo notebook
            </Button>
          </div>
          {notebooks.length === 0 ? (
            <p className="empty-state">Nenhum notebook ainda — crie o primeiro.</p>
          ) : (
            <div className="grid-cards">
              {notebooks.map((notebook) => (
                <Card key={notebook.id} className="notebook-card">
                  <span className="notebook-card-name">
                    <IconBook size={16} />
                    {notebook.name}
                  </span>
                  <Button variant="secondary" onClick={() => navigate(`/notebooks/${notebook.id}`)}>
                    abrir
                    <IconArrowRight size={16} />
                  </Button>
                </Card>
              ))}
            </div>
          )}
        </section>
      </div>

      <Modal open={modalOpen} onClose={() => setModalOpen(false)} title="Novo notebook">
        <form onSubmit={handleCreate} className="stack">
          <Input
            value={name}
            onChange={(event) => setName(event.target.value)}
            placeholder="Nome do notebook"
            autoFocus
            required
          />
          {error && <p role="alert">{error}</p>}
          <Button type="submit" className="full-width">
            <IconPlus size={16} />
            criar
          </Button>
        </form>
      </Modal>
    </div>
  )
}
