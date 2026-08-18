import type { ReactNode } from 'react'
import { IconBook } from './icons'
import ThemeToggle from './ThemeToggle'
import UserMenu from './UserMenu'

export default function Navbar({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <header className="navbar">
      <div className="navbar-brand">
        <IconBook />
        <span>{title}</span>
      </div>
      <div className="navbar-actions">
        {children}
        <ThemeToggle />
        <UserMenu />
      </div>
    </header>
  )
}
