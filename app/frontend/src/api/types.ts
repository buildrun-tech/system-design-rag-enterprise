export interface Page<T> {
  content: T[]
}

export type SourceType = 'FILE' | 'URL'
export type SourceStatus = 'PENDING' | 'PROCESSING' | 'READY' | 'FAILED'
export type MessageRole = 'user' | 'assistant'

export interface Notebook {
  id: string
  name: string
  description: string | null
  createdAt: string
  updatedAt: string
}

export interface Source {
  id: string
  name: string
  type: SourceType
  status: SourceStatus
  errorMessage: string | null
  createdAt: string
}

export interface Conversation {
  id: string
  notebookId: string
  activeSourceIds?: string[]
  createdAt: string
  preview?: string
}

export interface CitedChunk {
  chunkIndex: number
  snippet: string
}

export interface CitedSource {
  sourceId: string
  name: string
  chunks: CitedChunk[]
}

export interface ConversationMessage {
  id: string
  role: MessageRole
  content: string
  sources?: CitedSource[]
  createdAt: string
}

export interface ApiErrorBody {
  error: string
  message: string
}
