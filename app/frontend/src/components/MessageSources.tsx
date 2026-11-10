import { useState } from 'react'
import type { CitedSource } from '../api/types'
import { IconFile } from './icons'

// Clique (não hover) abre o card inline: sem popup flutuante, nada some ao mover o mouse, funciona no touch.
export default function MessageSources({ sources }: { sources: CitedSource[] }) {
  const [openId, setOpenId] = useState<string | null>(null)
  const open = sources.find((source) => source.sourceId === openId)

  return (
    <div className="message-sources">
      <span className="message-sources__label">Fontes · {sources.length}</span>
      <div className="message-sources__chips">
        {sources.map((source) => (
          <button
            key={source.sourceId}
            type="button"
            className={`source-chip${source.sourceId === openId ? ' source-chip--active' : ''}`}
            aria-expanded={source.sourceId === openId}
            title={source.name}
            onClick={() => setOpenId(source.sourceId === openId ? null : source.sourceId)}
          >
            <IconFile size={12} />
            <span className="source-chip__name">{source.name}</span>
          </button>
        ))}
      </div>
      {open && (
        <div className="source-card">
          <div className="source-card__title">{open.name}</div>
          {open.chunks.map((chunk) => (
            <blockquote key={chunk.chunkIndex} className="source-card__snippet">
              {chunk.snippet}
            </blockquote>
          ))}
        </div>
      )}
    </div>
  )
}
