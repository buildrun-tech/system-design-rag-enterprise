import { useEffect, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import { useAuth } from 'react-oidc-context'
import { fetchEventSource } from '@microsoft/fetch-event-source'
import { apiFetch } from '../api/client'
import type { Conversation, ConversationMessage, Source } from '../api/types'
import UserMenu from '../components/UserMenu'

interface ChatMessage extends ConversationMessage {
  streaming?: boolean
  failed?: boolean
}

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL

export default function WorkspacePage() {
  const { notebookId } = useParams<{ notebookId: string }>()
  const auth = useAuth()
  const token = auth.user?.access_token ?? ''

  const [sources, setSources] = useState<Source[]>([])
  const [conversation, setConversation] = useState<Conversation | null>(null)
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [input, setInput] = useState('')
  const abortRef = useRef<AbortController | null>(null)

  useEffect(() => {
    if (!notebookId) return
    apiFetch<Source[]>(`/api/v1/notebooks/${notebookId}/sources`, token).then(setSources)
  }, [notebookId, token])

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
    <main style={{ padding: '2rem', display: 'grid', gridTemplateColumns: '1fr 2fr', gap: '1rem', height: '90vh' }}>
      <header style={{ gridColumn: '1 / -1', display: 'flex', justifyContent: 'flex-end' }}>
        <UserMenu />
      </header>

      <section style={{ border: '1px solid #ccc', borderRadius: '0.5rem', padding: '1rem', overflowY: 'auto' }}>
        <h2>sources</h2>
        <ul style={{ listStyle: 'none', padding: 0 }}>
          {sources.map((source) => (
            <li key={source.id}>
              {source.name} — {source.status}
              {source.status === 'FAILED' && source.errorMessage ? ` (${source.errorMessage})` : ''}
            </li>
          ))}
        </ul>
      </section>

      <section style={{ border: '1px solid #ccc', borderRadius: '0.5rem', padding: '1rem', display: 'flex', flexDirection: 'column' }}>
        <h2>chat</h2>
        <div style={{ flex: 1, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
          {messages.map((message) => (
            <p key={message.id}>
              <strong>{message.role === 'user' ? 'Você' : 'Assistente'}:</strong> {message.content}
              {message.streaming ? '…' : ''}
              {message.failed ? ' (falha no envio, tente novamente)' : ''}
            </p>
          ))}
        </div>
        <form onSubmit={handleSend} style={{ display: 'flex', gap: '0.5rem' }}>
          <input
            value={input}
            onChange={(event) => setInput(event.target.value)}
            placeholder="Ola chat, etc"
            style={{ flex: 1 }}
          />
          <button type="submit">{'>'}</button>
        </form>
      </section>
    </main>
  )
}
