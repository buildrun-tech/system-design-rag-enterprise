import { useState } from 'react'

const KEY = 'devMode'

// localStorage pode lançar (modo privado, site data bloqueado): cai para false
function read(): boolean {
  try {
    return localStorage.getItem(KEY) === '1'
  } catch {
    return false
  }
}

export function useDevMode(): [boolean, () => void] {
  const [devMode, setDevMode] = useState(read)

  function toggle() {
    setDevMode((current) => {
      const next = !current
      try {
        localStorage.setItem(KEY, next ? '1' : '0')
      } catch {
        // sem persistência: vale só para esta sessão
      }
      return next
    })
  }

  return [devMode, toggle]
}
