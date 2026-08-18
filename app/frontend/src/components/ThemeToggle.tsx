import { useTheme } from '../theme/useTheme'
import { IconSun, IconMoon } from './icons'

export default function ThemeToggle() {
  const { theme, toggleTheme } = useTheme()

  return (
    <button
      className="icon-button"
      onClick={toggleTheme}
      title={theme === 'dark' ? 'Ativar modo claro' : 'Ativar modo escuro'}
    >
      {theme === 'dark' ? <IconSun /> : <IconMoon />}
    </button>
  )
}
