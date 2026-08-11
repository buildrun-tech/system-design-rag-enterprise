import type { HTMLAttributes } from 'react'
import './ui.css'

export default function Card({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div className={`ui-card ${className ?? ''}`} {...props} />
}
