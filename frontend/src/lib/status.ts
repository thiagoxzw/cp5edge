import type { AlertLevel, HealthStatus, IncidentStatus, RiskLevel, ZoneMapStatus } from '../api/types'

/** Paleta semântica: cor só significa estado. */
export interface Tone {
  label: string
  dot: string
  text: string
  chip: string
}

const TONES = {
  green: { dot: 'bg-emerald-400', text: 'text-emerald-300', chip: 'bg-emerald-400/10 text-emerald-300 ring-emerald-400/25' },
  amber: { dot: 'bg-amber-400', text: 'text-amber-300', chip: 'bg-amber-400/10 text-amber-300 ring-amber-400/25' },
  orange: { dot: 'bg-orange-500', text: 'text-orange-300', chip: 'bg-orange-500/10 text-orange-300 ring-orange-500/30' },
  red: { dot: 'bg-red-500', text: 'text-red-300', chip: 'bg-red-500/12 text-red-300 ring-red-500/30' },
  gray: { dot: 'bg-zinc-500', text: 'text-zinc-400', chip: 'bg-zinc-500/10 text-zinc-400 ring-zinc-500/25' },
}

export const HEALTH: Record<HealthStatus, Tone> = {
  ONLINE: { label: 'Online', ...TONES.green },
  UNSTABLE: { label: 'Instável', ...TONES.amber },
  ALERT: { label: 'Alerta', ...TONES.red },
  OFFLINE: { label: 'Offline', ...TONES.gray },
  INACTIVE: { label: 'Desativado', ...TONES.gray },
}

export const LEVEL: Record<AlertLevel, Tone> = {
  NORMAL: { label: 'Normal', ...TONES.green },
  ATTENTION: { label: 'Atenção', ...TONES.amber },
  CRITICAL: { label: 'Crítico', ...TONES.orange },
  EMERGENCY: { label: 'Emergência', ...TONES.red },
}

export const INCIDENT_STATUS: Record<IncidentStatus, Tone> = {
  ACTIVE: { label: 'Ativo', ...TONES.red },
  ACKNOWLEDGED: { label: 'Reconhecido', ...TONES.amber },
  RESOLVED: { label: 'Resolvido', ...TONES.green },
}

/** Cores das áreas no mapa da vinheria. */
export const ZONE_STATUS: Record<ZoneMapStatus, { label: string; fill: string; stroke: string; text: string }> = {
  normal: { label: 'Normal', fill: 'rgb(52 211 153 / 0.10)', stroke: 'rgb(52 211 153 / 0.55)', text: 'text-emerald-300' },
  attention: { label: 'Atenção', fill: 'rgb(251 191 36 / 0.12)', stroke: 'rgb(251 191 36 / 0.65)', text: 'text-amber-300' },
  critical: { label: 'Crítico', fill: 'rgb(239 68 68 / 0.16)', stroke: 'rgb(239 68 68 / 0.75)', text: 'text-red-300' },
  offline: { label: 'Offline', fill: 'rgb(113 113 122 / 0.12)', stroke: 'rgb(113 113 122 / 0.55)', text: 'text-zinc-400' },
  empty: { label: 'Sem sensor', fill: 'rgb(113 113 122 / 0.04)', stroke: 'rgb(113 113 122 / 0.25)', text: 'text-zinc-500' },
}

/** Cor do Wine Score conforme a classificação (90/75/50). */
export function scoreTone(score: number | null | undefined): { stroke: string; text: string } {
  if (score === null || score === undefined) return { stroke: '#52525b', text: 'text-zinc-400' }
  if (score >= 90) return { stroke: '#34d399', text: 'text-emerald-300' }
  if (score >= 75) return { stroke: '#a3e635', text: 'text-lime-300' }
  if (score >= 50) return { stroke: '#fbbf24', text: 'text-amber-300' }
  return { stroke: '#ef4444', text: 'text-red-300' }
}

export function scoreLabel(score: number | null | undefined): string {
  if (score === null || score === undefined) return 'Sem dados'
  if (score >= 90) return 'Excelente'
  if (score >= 75) return 'Bom'
  if (score >= 50) return 'Atenção'
  return 'Crítico'
}

export const VARIABLES = {
  temperature: { label: 'Temperatura', unit: '°C', code: 'temp', color: '#e8a3b8' },
  humidity: { label: 'Umidade', unit: '%', code: 'hum', color: '#7dd3fc' },
  luminosity: { label: 'Luminosidade', unit: '%', code: 'lux', color: '#d6b36a' },
} as const

export const CODE_LABEL: Record<string, string> = { temp: 'Temperatura', hum: 'Umidade', lux: 'Luminosidade' }

/** WineGuard Intelligence: faixas do índice de risco (0–24 baixo · 25–49 moderado · 50–74 alto · 75–100 crítico). */
export const RISK: Record<RiskLevel, Tone> = {
  LOW: { label: 'Risco baixo', ...TONES.green },
  MODERATE: { label: 'Risco moderado', ...TONES.amber },
  HIGH: { label: 'Risco alto', ...TONES.orange },
  CRITICAL: { label: 'Risco crítico', ...TONES.red },
  INDETERMINATE: { label: 'Indeterminado', ...TONES.gray },
}

export const riskLevelOf = (risk: number): RiskLevel =>
  risk >= 75 ? 'CRITICAL' : risk >= 50 ? 'HIGH' : risk >= 25 ? 'MODERATE' : 'LOW'
