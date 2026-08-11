import { useNavigate } from 'react-router-dom'
import { useActiveAuth } from '../auth/useActiveAuth'
import Button from './ui/Button'

export default function UserMenu() {
  const auth = useActiveAuth()
  const navigate = useNavigate()

  async function handleLogout() {
    await auth.signOut()
    navigate('/', { replace: true })
  }

  return (
    <Button
      variant="secondary"
      onClick={handleLogout}
      title="Logout"
      style={{ borderRadius: '50%', width: '2rem', height: '2rem', padding: 0 }}
    >
      p
    </Button>
  )
}
