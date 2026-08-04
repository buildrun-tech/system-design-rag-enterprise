import { useNavigate } from 'react-router-dom'
import { useAuth } from 'react-oidc-context'

export default function UserMenu() {
  const auth = useAuth()
  const navigate = useNavigate()

  async function handleLogout() {
    await auth.removeUser()
    navigate('/', { replace: true })
  }

  return (
    <button
      onClick={handleLogout}
      title="Logout"
      style={{ borderRadius: '50%', width: '2rem', height: '2rem' }}
    >
      p
    </button>
  )
}
