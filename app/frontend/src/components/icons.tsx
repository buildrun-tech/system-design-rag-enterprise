type IconProps = { size?: number }

const base = (size = 18) => ({
  width: size,
  height: size,
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 2,
  strokeLinecap: 'round' as const,
  strokeLinejoin: 'round' as const,
})

export function IconBook({ size }: IconProps) {
  return (
    <svg {...base(size)}>
      <path d="M4 4.5A2.5 2.5 0 0 1 6.5 2H20v18H6.5A2.5 2.5 0 0 0 4 22.5v-18Z" />
      <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" />
    </svg>
  )
}

export function IconPlus({ size }: IconProps) {
  return (
    <svg {...base(size)}>
      <path d="M12 5v14M5 12h14" />
    </svg>
  )
}

export function IconLogout({ size }: IconProps) {
  return (
    <svg {...base(size)}>
      <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
      <path d="M16 17l5-5-5-5" />
      <path d="M21 12H9" />
    </svg>
  )
}

export function IconSun({ size }: IconProps) {
  return (
    <svg {...base(size)}>
      <circle cx="12" cy="12" r="4" />
      <path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41" />
    </svg>
  )
}

export function IconMoon({ size }: IconProps) {
  return (
    <svg {...base(size)}>
      <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79Z" />
    </svg>
  )
}

export function IconFile({ size }: IconProps) {
  return (
    <svg {...base(size)}>
      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z" />
      <path d="M14 2v6h6" />
    </svg>
  )
}

export function IconSend({ size }: IconProps) {
  return (
    <svg {...base(size)}>
      <path d="M22 2 11 13" />
      <path d="M22 2 15 22l-4-9-9-4Z" />
    </svg>
  )
}

export function IconArrowRight({ size }: IconProps) {
  return (
    <svg {...base(size)}>
      <path d="M5 12h14M13 6l6 6-6 6" />
    </svg>
  )
}

export function IconUpload({ size }: IconProps) {
  return (
    <svg {...base(size)}>
      <path d="M12 16V4M6 10l6-6 6 6" />
      <path d="M4 16v3a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-3" />
    </svg>
  )
}

export function IconTrash({ size }: IconProps) {
  return (
    <svg {...base(size)}>
      <path d="M4 7h16M9 7V4h6v3M6 7l1 13a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2l1-13" />
    </svg>
  )
}

export function IconSpinner({ size = 16 }: IconProps) {
  return (
    <svg {...base(size)} className="icon-spinner">
      <path d="M21 12a9 9 0 1 1-9-9" />
    </svg>
  )
}

export function IconGoogle({ size = 18 }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24">
      <path fill="#4285F4" d="M23.04 12.27c0-.82-.07-1.42-.22-2.05H12.24v3.72h6.19c-.12 1.02-.8 2.56-2.3 3.6l-.02.14 3.35 2.56.23.02c2.13-1.94 3.35-4.8 3.35-8Z" />
      <path fill="#34A853" d="M12.24 23c3.03 0 5.57-.98 7.43-2.68l-3.54-2.72c-.95.65-2.22 1.1-3.89 1.1-2.97 0-5.48-1.94-6.38-4.62l-.13.01-3.48 2.65-.05.13C4.6 20.6 8.13 23 12.24 23Z" />
      <path fill="#FBBC05" d="M5.86 14.09a6.6 6.6 0 0 1-.36-2.13c0-.74.13-1.46.35-2.13l-.01-.14-3.53-2.7-.11.06A10.9 10.9 0 0 0 1 12c0 1.77.44 3.44 1.2 4.91l3.66-2.82Z" />
      <path fill="#EA4335" d="M12.24 5.11c2.1 0 3.53.9 4.34 1.66l3.17-3.05C17.8 1.98 15.27 1 12.24 1 8.13 1 4.6 3.4 2.86 6.9l3.65 2.83c.91-2.68 3.42-4.62 5.73-4.62Z" />
    </svg>
  )
}

export function IconGithub({ size = 18 }: IconProps) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="currentColor">
      <path d="M12 1.5a10.5 10.5 0 0 0-3.32 20.47c.52.1.72-.23.72-.5v-1.95c-2.93.64-3.55-1.24-3.55-1.24-.48-1.22-1.17-1.55-1.17-1.55-.96-.65.07-.64.07-.64 1.06.07 1.62 1.09 1.62 1.09.94 1.61 2.46 1.15 3.06.88.1-.68.37-1.15.67-1.42-2.34-.27-4.8-1.17-4.8-5.22 0-1.15.41-2.09 1.08-2.83-.1-.27-.47-1.35.1-2.82 0 0 .89-.28 2.9 1.08a10 10 0 0 1 5.28 0c2.01-1.36 2.9-1.08 2.9-1.08.57 1.47.2 2.55.1 2.82.68.74 1.08 1.68 1.08 2.83 0 4.06-2.47 4.94-4.82 5.2.38.33.72.97.72 1.96v2.9c0 .28.19.61.73.5A10.5 10.5 0 0 0 12 1.5Z" />
    </svg>
  )
}
