import type { ToolCall } from '../api/types'
import { IconSpinner } from './icons'

interface ToolTimelineProps {
  toolCalls: ToolCall[]
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

function IoBlock({ title, text }: { title: string; text: string }) {
  return (
    <div className="tool-timeline__block">
      <span className="tool-timeline__block-title">{title}</span>
      <pre>{pretty(text)}</pre>
    </div>
  )
}

export default function ToolTimeline({ toolCalls, dev = false }: ToolTimelineProps) {
  return (
    <details className="tool-timeline-wrap" open>
      <summary className="tool-timeline-wrap__summary">Ferramentas ({toolCalls.length})</summary>
      <ol className="tool-timeline">
        {toolCalls.map((toolCall) => (
          <li key={toolCall.id} className={`tool-timeline__item tool-timeline__item--${toolCall.status}`}>
            <span className="tool-timeline__dot">{toolCall.status === 'start' && <IconSpinner size={10} />}</span>
            <span className="tool-timeline__label">{toolCall.label ?? toolCall.name}</span>
            <span className="tool-timeline__meta">
              {toolCall.at !== undefined && `+${(toolCall.at / 1000).toFixed(2)}s`}
              {toolCall.durationMs !== undefined && ` · ${toolCall.durationMs}ms`}
              {toolCall.status === 'error' && ' · erro'}
              {dev && ` · ${toolCall.name} · ${toolCall.id.slice(0, 8)}`}
            </span>
            {(toolCall.input || toolCall.result) && (
              <details className="tool-timeline__io">
                <summary>detalhes</summary>
                {toolCall.input && <IoBlock title="Input" text={toolCall.input} />}
                {toolCall.result && <IoBlock title={toolCall.status === 'error' ? 'Erro' : 'Output'} text={toolCall.result} />}
              </details>
            )}
          </li>
        ))}
      </ol>
    </details>
  )
}
