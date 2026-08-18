import { useEffect, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import { useActiveAuth } from '../auth/useActiveAuth'
import { fetchEventSource } from '@microsoft/fetch-event-source'
import { apiFetch } from '../api/client'
import type { Conversation, ConversationMessage, Source } from '../api/types'

interface NotebookDetail {
  sources: Source[]
}
import Navbar from '../components/Navbar'
import Button from '../components/ui/Button'
import Input from '../components/ui/Input'
import Card from '../components/ui/Card'
import { ApiError } from '../api/client'
import { IconFile, IconSend, IconSpinner, IconTrash, IconUpload } from '../components/icons'

const PENDING_STATUSES = new Set(['PENDING', 'PROCESSING'])

interface ChatMessage extends ConversationMessage {
  streaming?: boolean
  failed?: boolean
}

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL

export default function WorkspacePage() {
  const { notebookId } = useParams<{ notebookId: string }>()
  const { token } = useActiveAuth()

  const [sources, setSources] = useState<Source[]>([])
  const [sourceError, setSourceError] = useState<string | null>(null)
  const [uploading, setUploading] = useState(false)
  const [conversation, setConversation] = useState<Conversation | null>(null)
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [input, setInput] = useState('')
  const abortRef = useRef<AbortController | null>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (!notebookId || !token) return
    apiFetch<NotebookDetail>(`/api/v1/notebooks/${notebookId}`, token).then((detail) => setSources(detail.sources))
  }, [notebookId, token])

  useEffect(() => {
    if (!notebookId || !token) return
    if (!sources.some((source) => PENDING_STATUSES.has(source.status))) return

    const interval = setInterval(() => {
      apiFetch<Source[]>(`/api/v1/notebooks/${notebookId}/sources`, token).then(setSources)
    }, 3000)
    return () => clearInterval(interval)
  }, [notebookId, token, sources])

  async function handleFileSelected(event: React.ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file || !notebookId) return

    setSourceError(null)
    setUploading(true)
    try {
      const formData = new FormData()
      formData.append('file', file)
      const created = await apiFetch<Source>(`/api/v1/notebooks/${notebookId}/sources`, token, {
        method: 'POST',
        body: formData,
      })
      setSources((current) => [created, ...current])
    } catch (err) {
      setSourceError(err instanceof ApiError ? err.message : 'Falha ao enviar arquivo')
    } finally {
      setUploading(false)
    }
  }

  async function handleDeleteSource(sourceId: string) {
    if (!notebookId) return
    const index = sources.findIndex((source) => source.id === sourceId)
    if (index === -1) return
    const removed = sources[index]

    setSourceError(null)
    setSources((current) => current.filter((source) => source.id !== sourceId))
    try {
      await apiFetch<void>(`/api/v1/notebooks/${notebookId}/sources/${sourceId}`, token, { method: 'DELETE' })
    } catch (err) {
      setSources((current) => [...current.slice(0, index), removed, ...current.slice(index)])
      setSourceError(err instanceof ApiError ? err.message : 'Falha ao deletar source')
    }
  }

  useEffect(() => {
    if (!notebookId || !token) return
    ;(async () => {
      const conversations = await apiFetch<Conversation[]>(`/api/v1/notebooks/${notebookId}/conversations`, token)
      const active = conversations[0] ?? (await apiFetch<Conversation>(`/api/v1/notebooks/${notebookId}/conversations`, token, {
        method: 'POST',
        body: JSON.stringify({}),
      }))
      setConversation(active)
      const history = await apiFetch<ConversationMessage[]>(`/api/v1/conversations/${active.id}/messages`, token)
      setMessages(history)
    })()
  }, [notebookId, token])

  useEffect(() => () => abortRef.current?.abort(), [])

  async function handleSend(event: React.FormEvent) {
    event.preventDefault()
    if (!conversation || !input.trim()) return

    const userMessage: ChatMessage = {
      id: crypto.randomUUID(),
      role: 'user',
      content: input,
      createdAt: new Date().toISOString(),
    }
    const assistantMessage: ChatMessage = {
      id: crypto.randomUUID(),
      role: 'assistant',
      content: '',
      createdAt: new Date().toISOString(),
      streaming: true,
    }
    setMessages((current) => [...current, userMessage, assistantMessage])
    setInput('')

    const controller = new AbortController()
    abortRef.current = controller

    await fetchEventSource(`${API_BASE_URL}/api/v1/conversations/${conversation.id}/messages`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ content: userMessage.content }),
      signal: controller.signal,
      async onopen(response) {
        if (!response.ok) {
          throw new Error(`stream failed: ${response.status}`)
        }
      },
      onmessage(event) {
        const data = JSON.parse(event.data) as { token?: string; done?: boolean; messageId?: string; error?: string }
        setMessages((current) =>
          current.map((message) => {
            if (message.id !== assistantMessage.id) return message
            if (data.error) return { ...message, streaming: false, failed: true }
            if (data.token) return { ...message, content: message.content + data.token }
            if (data.done) return { ...message, id: data.messageId ?? message.id, streaming: false }
            return message
          }),
        )
      },
      onerror(err) {
        setMessages((current) =>
          current.map((message) =>
            message.id === assistantMessage.id ? { ...message, streaming: false, failed: true } : message,
          ),
        )
        throw err
      },
    })
  }

  return (
    <div className="page">
      <Navbar title="NotebookLM" />

      <div className="page-body workspace-grid">
        <Card className="section sources-panel">
          <div className="section-header">
            <h2 className="section-title">Sources</h2>
            <button className="icon-button" title="Adicionar arquivo" onClick={() => fileInputRef.current?.click()} disabled={uploading}>
              {uploading ? <IconSpinner size={16} /> : <IconUpload size={16} />}
            </button>
            <input ref={fileInputRef} type="file" hidden onChange={handleFileSelected} />
          </div>
          {sourceError && <p role="alert">{sourceError}</p>}
          {sources.length === 0 ? (
            <p className="empty-state">Nenhuma fonte ainda.</p>
          ) : (
            sources.map((source) => (
              <div className="source-item" key={source.id}>
                {PENDING_STATUSES.has(source.status) ? <IconSpinner size={16} /> : <IconFile size={16} />}
                <span className="source-name">{source.name}</span>
                <span className={`status-badge${source.status === 'FAILED' ? ' status-badge--failed' : ''}`}>
                  {source.status}
                </span>
                <div className="source-actions">
                  <button className="icon-button" title="Remover source" onClick={() => handleDeleteSource(source.id)}>
                    <IconTrash size={16} />
                  </button>
                </div>
              </div>
            ))
          )}
        </Card>

        <Card className="chat-panel">
          <h2 className="section-title">Chat</h2>
          <div className="chat-messages">
            {messages.map((message) => (
              <div key={message.id} className={`message-bubble message-bubble--${message.role}`}>
                <span className="message-role">{message.role === 'user' ? 'Você' : 'Assistente'}</span>
                {message.content}
                {message.streaming ? '…' : ''}
                {message.failed ? ' (falha no envio, tente novamente)' : ''}
              </div>
            ))}
          </div>
          <form onSubmit={handleSend} className="toolbar">
            <Input
              className="flex-1"
              value={input}
              onChange={(event) => setInput(event.target.value)}
              placeholder="Ola chat, etc"
            />
            <Button type="submit">
              <IconSend size={16} />
            </Button>
          </form>
        </Card>
      </div>
    </div>
  )
}
