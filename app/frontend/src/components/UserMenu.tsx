import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useActiveAuth } from '../auth/useActiveAuth'
import { IconLogout } from './icons'

export default function UserMenu() {
  const auth = useActiveAuth()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (ref.current && !ref.current.contains(event.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  async function handleLogout() {
    await auth.signOut()
    navigate('/', { replace: true })
  }

  return (
    <div className="profile-menu" ref={ref}>
      <button className="profile-avatar" onClick={() => setOpen((current) => !current)} title="Perfil">
        U
      </button>
      {open && (
        <div className="profile-dropdown">
          <button className="profile-dropdown-item" onClick={handleLogout}>
            <IconLogout size={16} />
            Sair
          </button>
        </div>
      )}
    </div>
  )
}
