import { useEffect, useRef } from 'react'

export interface SseLogEntry {
  at: number // ms desde o início do request
  kind: 'open' | 'event' | 'close' | 'error'
  raw: string
}

interface SseDebugPanelProps {
  entries: SseLogEntry[]
  onClear: () => void
}

function format(entry: SseLogEntry) {
  return `+${(entry.at / 1000).toFixed(3)}s ${entry.kind.toUpperCase()} ${entry.raw}`
}

export default function SseDebugPanel({ entries, onClear }: SseDebugPanelProps) {
  const endRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    endRef.current?.scrollIntoView?.({ block: 'nearest' })
  }, [entries.length])

  return (
    <div className="sse-debug">
      <div className="sse-debug__header">
        <span>SSE · {entries.length} eventos</span>
        <button type="button" className="sse-debug__action" onClick={() => navigator.clipboard?.writeText(entries.map(format).join('\n'))}>
          Copiar
        </button>
        <button type="button" className="sse-debug__action" onClick={onClear}>
          Limpar
        </button>
      </div>
      <div className="sse-debug__log">
        {entries.length === 0 && <span className="sse-debug__empty">Envie uma mensagem para ver o stream.</span>}
        {entries.map((entry, index) => (
          <div
            key={index}
            className={`sse-debug__row sse-debug__row--${entry.kind}${entry.raw.includes('"toolCall"') ? ' sse-debug__row--tool' : ''}`}
          >
            {format(entry)}
          </div>
        ))}
        <div ref={endRef} />
      </div>
    </div>
  )
}
