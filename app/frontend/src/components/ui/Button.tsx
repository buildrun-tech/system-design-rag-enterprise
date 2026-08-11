import type { ButtonHTMLAttributes } from 'react'
import './ui.css'

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: 'primary' | 'secondary'
}

export default function Button({ variant = 'primary', className, ...props }: ButtonProps) {
  return <button className={`ui-button ui-button--${variant} ${className ?? ''}`} {...props} />
}
