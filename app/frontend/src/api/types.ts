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

export interface ConversationMessage {
  id: string
  role: MessageRole
  content: string
  createdAt: string
}

export interface ApiErrorBody {
  error: string
  message: string
}
