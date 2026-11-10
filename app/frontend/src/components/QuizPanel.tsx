import { useEffect, useRef, useState } from 'react'
import { ApiError, apiFetch } from '../api/client'
import type { Quiz, QuizCreated, QuizQuestion } from '../api/types'
import Button from './ui/Button'
import { IconSpinner } from './icons'

const POLL_INTERVAL_MS = 2000

type Phase = 'generating' | 'ready' | 'failed'

interface QuizPanelProps {
  conversationId: string
  token: string
}

// Montado só enquanto o modal está aberto: gera ao abrir, fechar descarta quiz e polling.
export default function QuizPanel({ conversationId, token }: QuizPanelProps) {
  const [phase, setPhase] = useState<Phase>('generating')
  const [quizId, setQuizId] = useState<string | null>(null)
  const [questions, setQuestions] = useState<QuizQuestion[]>([])
  const [answers, setAnswers] = useState<Record<number, number>>({})
  const [current, setCurrent] = useState(0)
  const [finished, setFinished] = useState(false)
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

  const started = useRef(false)
  useEffect(() => {
    if (started.current) return // StrictMode monta 2x em dev; evita 2 quizzes
    started.current = true
    handleGenerate()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function handleGenerate() {
    setError(null)
    setAnswers({})
    setCurrent(0)
    setFinished(false)
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
      {phase === 'generating' && (
        <p className="empty-state">
          <IconSpinner size={16} /> Gerando quiz com as fontes ativas...
        </p>
      )}
      {phase === 'failed' && (
        <>
          <p role="alert">{error}</p>
          <Button onClick={handleGenerate}>Tentar de novo</Button>
        </>
      )}

      {phase === 'ready' && (() => {
        const total = questions.length
        const progress = finished ? 100 : (answered / total) * 100
        const question = questions[current]
        const chosen = answers[current]
        const isAnswered = chosen !== undefined
        return (
          <>
            <div
              className="quiz-progress"
              role="progressbar"
              aria-valuemin={0}
              aria-valuemax={total}
              aria-valuenow={finished ? total : answered}
            >
              <div className="quiz-progress__bar" style={{ width: `${progress}%` }} />
            </div>

            {finished ? (
              <div className="quiz-result">
                <p className="quiz-question__title">
                  Resultado: {score} de {total} ({Math.round((score / total) * 100)}%)
                </p>
                <div className="toolbar">
                  <Button variant="secondary" onClick={() => { setFinished(false); setCurrent(0) }}>
                    Revisar respostas
                  </Button>
                  <Button onClick={handleGenerate}>Gerar novo quiz</Button>
                </div>
              </div>
            ) : (
              <>
                <div className="quiz-question">
                  <p className="quiz-question__title">
                    {current + 1}/{total}. {question.question}
                  </p>
                  <div className="quiz-options" role="group" aria-label={`Pergunta ${current + 1}`}>
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
                          onClick={() => setAnswers((prev) => ({ ...prev, [current]: optionIndex }))}
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
                <div className="toolbar quiz-nav">
                  <Button variant="secondary" disabled={current === 0} onClick={() => setCurrent(current - 1)}>
                    Anterior
                  </Button>
                  {current < total - 1 ? (
                    <Button variant="secondary" onClick={() => setCurrent(current + 1)}>
                      Próxima
                    </Button>
                  ) : (
                    <Button disabled={answered < total} onClick={() => setFinished(true)}>
                      Ver resultado
                    </Button>
                  )}
                </div>
              </>
            )}
          </>
        )
      })()}
    </div>
  )
}
