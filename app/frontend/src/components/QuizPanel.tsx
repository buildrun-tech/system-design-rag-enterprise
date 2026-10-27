import { useEffect, useState } from 'react'
import { ApiError, apiFetch } from '../api/client'
import type { Quiz, QuizCreated, QuizQuestion } from '../api/types'
import Button from './ui/Button'
import { IconSpinner } from './icons'

const POLL_INTERVAL_MS = 2000

type Phase = 'idle' | 'generating' | 'ready' | 'failed'

interface QuizPanelProps {
  conversationId: string
  token: string
}

// Montado com key={conversationId}: trocar de conversa descarta quiz e polling.
export default function QuizPanel({ conversationId, token }: QuizPanelProps) {
  const [phase, setPhase] = useState<Phase>('idle')
  const [quizId, setQuizId] = useState<string | null>(null)
  const [questions, setQuestions] = useState<QuizQuestion[]>([])
  const [answers, setAnswers] = useState<Record<number, number>>({})
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (phase !== 'generating' || !quizId) return
    const interval = setInterval(async () => {
      try {
        const quiz = await apiFetch<Quiz>(`/api/v1/conversations/${conversationId}/quizzes/${quizId}`, token)
        if (quiz.status === 'COMPLETED' && quiz.questions) {
          setQuestions(quiz.questions)
          setPhase('ready')
        } else if (quiz.status === 'FAILED') {
          setError(quiz.errorMessage ?? 'Falha ao gerar o quiz')
          setPhase('failed')
        }
      } catch (err) {
        setError(err instanceof ApiError ? err.message : 'Falha ao consultar o quiz')
        setPhase('failed')
      }
    }, POLL_INTERVAL_MS)
    return () => clearInterval(interval)
  }, [phase, quizId, conversationId, token])

  async function handleGenerate() {
    setError(null)
    setAnswers({})
    setQuestions([])
    setPhase('generating')
    try {
      const created = await apiFetch<QuizCreated>(`/api/v1/conversations/${conversationId}/quizzes`, token, {
        method: 'POST',
      })
      setQuizId(created.quizId)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Falha ao gerar o quiz')
      setPhase('failed')
    }
  }

  const answered = Object.keys(answers).length
  const score = questions.filter((question, index) => answers[index] === question.correctIndex).length

  return (
    <div className="quiz-panel">
      <div className="section-header">
        <h2 className="section-title">Quiz</h2>
      </div>

      {phase !== 'ready' && (
        <Button onClick={handleGenerate} disabled={phase === 'generating'}>
          {phase === 'generating' ? (
            <>
              <IconSpinner size={16} /> Gerando quiz...
            </>
          ) : (
            'Gerar quiz'
          )}
        </Button>
      )}
      {phase === 'idle' && <p className="empty-state">Gere um quiz com base nas fontes ativas da conversa.</p>}
      {phase === 'failed' && <p role="alert">{error}</p>}

      {phase === 'ready' && (
        <>
          {questions.map((question, index) => {
            const chosen = answers[index]
            const isAnswered = chosen !== undefined
            return (
              <div className="quiz-question" key={index}>
                <p className="quiz-question__title">
                  {index + 1}. {question.question}
                </p>
                <div className="quiz-options" role="group" aria-label={`Pergunta ${index + 1}`}>
                  {question.options.map((option, optionIndex) => {
                    const state = !isAnswered
                      ? ''
                      : optionIndex === question.correctIndex
                        ? ' quiz-option--correct'
                        : optionIndex === chosen
                          ? ' quiz-option--wrong'
                          : ''
                    return (
                      <button
                        key={optionIndex}
                        className={`quiz-option${state}`}
                        disabled={isAnswered}
                        onClick={() => setAnswers((current) => ({ ...current, [index]: optionIndex }))}
                      >
                        {option}
                      </button>
                    )
                  })}
                </div>
                {isAnswered && (
                  <p className="quiz-explanation">
                    <strong>{chosen === question.correctIndex ? 'Correto. ' : 'Incorreto. '}</strong>
                    {question.explanation}
                  </p>
                )}
              </div>
            )
          })}
          {answered === questions.length && (
            <div className="quiz-result">
              <p>
                Pontuação: {score} de {questions.length}
              </p>
              <Button onClick={handleGenerate}>Gerar novo quiz</Button>
            </div>
          )}
        </>
      )}
    </div>
  )
}
