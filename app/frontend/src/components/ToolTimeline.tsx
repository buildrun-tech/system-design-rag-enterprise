import { useEffect, useState } from 'react'
import type { ToolCall } from '../api/types'
import { IconSpinner } from './icons'

interface ToolTimelineProps {
  toolCalls: ToolCall[]
  streaming?: boolean
  dev?: boolean
}

// JSON vira indentado; qualquer outra coisa (texto, ISO date) fica como veio
function pretty(text: string): string {
  try {
    return JSON.stringify(JSON.parse(text), null, 2)
  } catch {
    return text
  }
}

// maior unidade primeiro: 850ms · 23.4s · 2m 05s · 1h 03m
export function formatDuration(ms: number): string {
  if (ms < 1000) return `${Math.round(ms)}ms`
  const totalSeconds = ms / 1000
  if (totalSeconds < 60) return `${totalSeconds.toFixed(1)}s`
  const minutes = Math.floor(totalSeconds / 60)
  if (minutes < 60) return `${minutes}m ${String(Math.floor(totalSeconds % 60)).padStart(2, '0')}s`
  return `${Math.floor(minutes / 60)}h ${String(minutes % 60).padStart(2, '0')}m`
}

function IoBlock({ title, text }: { title: string; text: string }) {
  return (
    <div className="tool-step__block">
      <span className="tool-step__block-title">{title}</span>
      <pre>{pretty(text)}</pre>
    </div>
  )
}

function StepIcon({ status }: { status: ToolCall['status'] }) {
  if (status === 'start') return <IconSpinner size={12} />
  return <span className={`tool-step__icon tool-step__icon--${status}`}>{status === 'error' ? '✕' : '✓'}</span>
}

// Estilo ChatGPT/Claude: cabeçalho de uma linha ("Pesquisando..." com shimmer enquanto roda,
// "N etapas concluídas" depois), aberto durante o stream e recolhido quando termina.
export default function ToolTimeline({ toolCalls, streaming = false, dev = false }: ToolTimelineProps) {
  const running = toolCalls.find((item) => item.status === 'start')
  const active = streaming && !!running
  const [open, setOpen] = useState(active)
  useEffect(() => setOpen(active), [active])

  const totalMs = toolCalls.reduce((sum, item) => sum + (item.durationMs ?? 0), 0)
  const title = running
    ? `${running.label ?? running.name}…`
    : `${toolCalls.length} ${toolCalls.length === 1 ? 'etapa concluída' : 'etapas concluídas'}${totalMs ? ` · ${formatDuration(totalMs)}` : ''}`

  return (
    <div className="tool-trace">
      <button type="button" className="tool-trace__header" aria-expanded={open} onClick={() => setOpen(!open)}>
        <span className={`tool-trace__title${running ? ' shimmer' : ''}`}>{title}</span>
        <span className={`tool-trace__chevron${open ? ' tool-trace__chevron--open' : ''}`}>›</span>
      </button>
      {open && (
        <ol className="tool-trace__steps">
          {toolCalls.map((toolCall) => (
            <li key={toolCall.id} className="tool-step">
              <StepIcon status={toolCall.status} />
              <div className="tool-step__body">
                <div className="tool-step__row">
                  <span className="tool-step__label">
                    {toolCall.status === 'start' ? `${toolCall.label ?? toolCall.name}…` : (toolCall.summary ?? toolCall.label ?? toolCall.name)}
                  </span>
                  <span className="tool-step__meta">
                    {toolCall.durationMs !== undefined && formatDuration(toolCall.durationMs)}
                    {toolCall.status === 'error' && ' · erro'}
                    {dev && ` · ${toolCall.name} · ${toolCall.id.slice(0, 8)}`}
                  </span>
                </div>
                {dev && (toolCall.input || toolCall.result) && (
                  <details className="tool-step__io">
                    <summary>detalhes</summary>
                    {toolCall.input && <IoBlock title="Input" text={toolCall.input} />}
                    {toolCall.result && <IoBlock title={toolCall.status === 'error' ? 'Erro' : 'Output'} text={toolCall.result} />}
                  </details>
                )}
              </div>
            </li>
          ))}
        </ol>
      )}
    </div>
  )
}
