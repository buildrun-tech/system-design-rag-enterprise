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

export type QuizStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'

export interface QuizQuestion {
  question: string
  options: string[]
  correctIndex: number
  explanation: string
}

export interface QuizCreated {
  quizId: string
  status: QuizStatus
}

export interface Quiz {
  quizId: string
  status: QuizStatus
  questions?: QuizQuestion[]
  errorMessage?: string
}
