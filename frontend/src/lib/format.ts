const dateTime = new Intl.DateTimeFormat('pt-BR', {
  day: '2-digit',
  month: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
})
const time = new Intl.DateTimeFormat('pt-BR', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
const shortDate = new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })

export function fmtValue(v: number | null | undefined, unit = '', decimals = 1): string {
  if (v === null || v === undefined || Number.isNaN(v)) return '—'
  return `${v.toLocaleString('pt-BR', { minimumFractionDigits: decimals, maximumFractionDigits: decimals })}${unit}`
}

/** 272 -> "04:32"; 3725 -> "1:02:05" */
export function fmtDuration(totalSeconds: number | null | undefined): string {
  if (totalSeconds === null || totalSeconds === undefined) return '—'
  const s = Math.max(0, Math.floor(totalSeconds))
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  const sec = s % 60
  const pad = (n: number) => String(n).padStart(2, '0')
  return h > 0 ? `${h}:${pad(m)}:${pad(sec)}` : `${pad(m)}:${pad(sec)}`
}

export function fmtAgo(seconds: number | null | undefined): string {
  if (seconds === null || seconds === undefined) return 'nunca'
  if (seconds < 5) return 'agora'
  if (seconds < 60) return `há ${seconds}s`
  if (seconds < 3600) return `há ${Math.floor(seconds / 60)} min`
  if (seconds < 86400) return `há ${Math.floor(seconds / 3600)} h`
  return `há ${Math.floor(seconds / 86400)} d`
}

export function secondsSince(iso: string | null | undefined): number | null {
  if (!iso) return null
  return Math.max(0, Math.floor((Date.now() - new Date(iso).getTime()) / 1000))
}

export const fmtDateTime = (iso: string | null | undefined) => (iso ? dateTime.format(new Date(iso)) : '—')
export const fmtTime = (iso: string | null | undefined) => (iso ? time.format(new Date(iso)) : '—')
export const fmtShortDate = (iso: string | number) => shortDate.format(new Date(iso))
