import type { InputHTMLAttributes } from 'react'
import './ui.css'

export default function Input({ className, ...props }: InputHTMLAttributes<HTMLInputElement>) {
  return <input className={`ui-input ${className ?? ''}`} {...props} />
}
