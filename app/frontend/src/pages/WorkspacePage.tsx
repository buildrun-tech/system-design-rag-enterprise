import { useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useActiveAuth } from '../auth/useActiveAuth'
import { fetchEventSource } from '@microsoft/fetch-event-source'
import { apiFetch, handleUnauthorized } from '../api/client'
import type { CitedSource, Conversation, ConversationMessage, Source, ToolCall } from '../api/types'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'

interface NotebookDetail {
  sources: Source[]
}
import Navbar from '../components/Navbar'
import ToolTimeline from '../components/ToolTimeline'
import SseDebugPanel, { type SseLogEntry } from '../components/SseDebugPanel'
import { useDevMode } from '../dev/useDevMode'
import Modal from '../components/Modal'
import MessageSources from '../components/MessageSources'
import QuizPanel from '../components/QuizPanel'
import Button from '../components/ui/Button'
import Input from '../components/ui/Input'
import Card from '../components/ui/Card'
import { ApiError } from '../api/client'
import { config } from '../config'
import { IconArrowLeft, IconFile, IconPlus, IconSend, IconSpinner, IconTrash, IconUpload } from '../components/icons'

const PENDING_STATUSES = new Set(['PENDING', 'PROCESSING'])

interface ChatMessage extends ConversationMessage {
  streaming?: boolean
  failed?: boolean
  toolCalls?: ToolCall[]
}

// start insere (ordem de chegada); end/error mesclam no mesmo id mantendo posição, offset, label e input do start
function upsertToolCall(toolCalls: ToolCall[] = [], toolCall: ToolCall, at: number): ToolCall[] {
  const existing = toolCalls.find((item) => item.id === toolCall.id)
  const merged = { ...existing, ...toolCall, at: existing?.at ?? at }
  return existing ? toolCalls.map((item) => (item.id === merged.id ? merged : item)) : [...toolCalls, merged]
}

const API_BASE_URL = config.apiBaseUrl

export default function WorkspacePage() {
  const { notebookId } = useParams<{ notebookId: string }>()
  const { token } = useActiveAuth()
  const navigate = useNavigate()

  const [sources, setSources] = useState<Source[]>([])
  const [sourceError, setSourceError] = useState<string | null>(null)
  const [uploading, setUploading] = useState(false)
  const [quizStep, setQuizStep] = useState<'closed' | 'confirm' | 'open'>('closed')
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false)
  const actionsRef = useRef<HTMLDetailsElement>(null)
  const chatRef = useRef<HTMLDivElement>(null)
  const [devMode, toggleDevMode] = useDevMode()
  const [sseLog, setSseLog] = useState<SseLogEntry[]>([])
  const [conversations, setConversations] = useState<Conversation[]>([])
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
      const list = await apiFetch<Conversation[]>(`/api/v1/notebooks/${notebookId}/conversations`, token)
      const active = list[0] ?? (await apiFetch<Conversation>(`/api/v1/notebooks/${notebookId}/conversations`, token, {
        method: 'POST',
        body: JSON.stringify({}),
      }))
      setConversations(list[0] ? list : [active])
      setConversation(active)
      const history = await apiFetch<ConversationMessage[]>(`/api/v1/conversations/${active.id}/messages`, token)
      setMessages(history)
    })()
  }, [notebookId, token])

  useEffect(() => () => abortRef.current?.abort(), [])

  // acompanha o stream só se o usuário já está perto do fim (não briga com scroll manual)
  useEffect(() => {
    const el = chatRef.current
    if (el && el.scrollHeight - el.scrollTop - el.clientHeight < 120) el.scrollTop = el.scrollHeight
  }, [messages])

  async function handleSwitchConversation(target: Conversation) {
    if (!token || target.id === conversation?.id) return
    abortRef.current?.abort()
    setConversation(target)
    const history = await apiFetch<ConversationMessage[]>(`/api/v1/conversations/${target.id}/messages`, token)
    setMessages(history)
  }

  async function handleNewChat() {
    if (!notebookId || !token) return
    abortRef.current?.abort()
    const created = await apiFetch<Conversation>(`/api/v1/notebooks/${notebookId}/conversations`, token, {
      method: 'POST',
      body: JSON.stringify({}),
    })
    setConversations((current) => [created, ...current])
    setConversation(created)
    setMessages([])
  }

  async function handleDeleteConversation() {
    if (!conversation || !token || !notebookId) return
    const deletedId = conversation.id
    abortRef.current?.abort()
    setDeleteConfirmOpen(false)
    await apiFetch<void>(`/api/v1/notebooks/${notebookId}/conversations/${deletedId}`, token, { method: 'DELETE' })
    const remaining = conversations.filter((item) => item.id !== deletedId)
    if (remaining.length === 0) {
      await handleNewChat()
      setConversations((current) => current.filter((item) => item.id !== deletedId))
    } else {
      setConversations(remaining)
      await handleSwitchConversation(remaining[0])
    }
  }

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

    const streamStart = performance.now()
    const elapsed = () => performance.now() - streamStart
    const log = (kind: SseLogEntry['kind'], raw: string) =>
      setSseLog((current) => [...current, { at: elapsed(), kind, raw }].slice(-300))
    setSseLog([])

    await fetchEventSource(`${API_BASE_URL}/api/v1/conversations/${conversation.id}/messages`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ content: userMessage.content }),
      signal: controller.signal,
      openWhenHidden: true,
      async onopen(response) {
        log('open', `HTTP ${response.status} ${response.headers.get('content-type') ?? ''}`)
        if (response.status === 401) {
          handleUnauthorized()
        }
        if (!response.ok) {
          throw new Error(`stream failed: ${response.status}`)
        }
      },
      onmessage(event) {
        log('event', event.data)
        const data = JSON.parse(event.data) as { token?: string; done?: boolean; messageId?: string; sources?: CitedSource[]; error?: string; toolCall?: ToolCall }
        setMessages((current) =>
          current.map((message) => {
            if (message.id !== assistantMessage.id) return message
            if (data.error) return { ...message, streaming: false, failed: true }
            if (data.toolCall) return { ...message, toolCalls: upsertToolCall(message.toolCalls, data.toolCall, elapsed()) }
            if (data.token) return { ...message, content: message.content + data.token }
            if (data.done) return { ...message, id: data.messageId ?? message.id, sources: data.sources ?? [], streaming: false }
            return message
          }),
        )
      },
      onclose() {
        log('close', '')
      },
      onerror(err) {
        log('error', String(err))
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
      <Navbar title="NotebookLM">
        <button className="icon-button" title="Voltar" onClick={() => navigate('/notebooks')}>
          <IconArrowLeft size={16} />
        </button>
        <button
          className={`icon-button icon-button--text${devMode ? ' icon-button--active' : ''}`}
          title="Dev mode: inspecionar eventos SSE"
          aria-pressed={devMode}
          onClick={toggleDevMode}
        >
          DEV
        </button>
      </Navbar>

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
          <div className="section-header">
            <h2 className="section-title">Chat</h2>
            <div className="toolbar">
              <select
                className="conversation-select"
                value={conversation?.id ?? ''}
                onChange={(event) => {
                  const target = conversations.find((item) => item.id === event.target.value)
                  if (target) handleSwitchConversation(target)
                }}
              >
                {conversations.map((item, index) => (
                  <option key={item.id} value={item.id}>
                    {item.preview || `Conversa ${conversations.length - index}`}
                  </option>
                ))}
              </select>
              <details className="actions-menu" ref={actionsRef}>
                <summary className="ui-button ui-button--secondary">Ações ▾</summary>
                <div className="actions-menu-list">
                  <button
                    disabled={!conversation}
                    onClick={() => {
                      actionsRef.current?.removeAttribute('open')
                      setQuizStep('confirm')
                    }}
                  >
                    Quiz
                  </button>
                  <button
                    disabled={!conversation}
                    onClick={() => {
                      actionsRef.current?.removeAttribute('open')
                      setDeleteConfirmOpen(true)
                    }}
                  >
                    Excluir conversa
                  </button>
                </div>
              </details>
              <button className="icon-button" title="Novo chat" onClick={handleNewChat}>
                <IconPlus size={16} />
              </button>
            </div>
          </div>
          <div className="chat-messages" ref={chatRef}>
            {messages.map((message) => (
              <div key={message.id} className={`message-bubble message-bubble--${message.role}`}>
                <span className="message-role">{message.role === 'user' ? 'Você' : 'Assistente'}</span>
                {message.toolCalls && message.toolCalls.length > 0 && (
                  <ToolTimeline toolCalls={message.toolCalls} streaming={message.streaming} dev={devMode} />
                )}
                {message.streaming && message.content === '' ? (
                  !message.toolCalls?.some((item) => item.status === 'start') && (
                    <span className="shimmer">{message.toolCalls?.length ? 'Escrevendo resposta…' : 'Pensando…'}</span>
                  )
                ) : (
                  <ReactMarkdown remarkPlugins={[remarkGfm]}>{message.content}</ReactMarkdown>
                )}
                {message.failed ? ' (falha no envio, tente novamente)' : ''}
                {message.sources && message.sources.length > 0 && <MessageSources sources={message.sources} />}
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

      {devMode && (
        <aside className="debug-drawer">
          <SseDebugPanel entries={sseLog} onClear={() => setSseLog([])} onClose={toggleDevMode} />
        </aside>
      )}

      <Modal open={quizStep === 'confirm'} onClose={() => setQuizStep('closed')} title="Gerar quiz">
        <p>Gerar um quiz com base nesta conversa?</p>
        <div className="toolbar">
          <Button onClick={() => setQuizStep('closed')}>Cancelar</Button>
          <Button onClick={() => setQuizStep('open')}>Gerar</Button>
        </div>
      </Modal>

      <Modal open={quizStep === 'open'} onClose={() => setQuizStep('closed')} title="Quiz">
        {conversation && token && <QuizPanel conversationId={conversation.id} token={token} />}
      </Modal>

      <Modal open={deleteConfirmOpen} onClose={() => setDeleteConfirmOpen(false)} title="Excluir conversa">
        <p>Excluir esta conversa e todas as mensagens? Não dá para desfazer.</p>
        <div className="toolbar">
          <Button onClick={() => setDeleteConfirmOpen(false)}>Cancelar</Button>
          <Button onClick={handleDeleteConversation}>Excluir</Button>
        </div>
      </Modal>
    </div>
  )
}
