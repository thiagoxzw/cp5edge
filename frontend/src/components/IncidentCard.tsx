import clsx from 'clsx'
import { BellOff, Droplets, Sun, Thermometer, WifiOff } from 'lucide-react'
import { Link } from 'react-router-dom'
import { useAcknowledgeIncident } from '../api/hooks'
import type { Incident } from '../api/types'
import { fmtDateTime, fmtDuration, fmtValue } from '../lib/format'
import { INCIDENT_STATUS, LEVEL } from '../lib/status'
import { useNow } from '../lib/useNow'
import { Badge, Button } from './ui'

const BAR: Record<string, string> = {
  ATTENTION: 'bg-amber-400',
  CRITICAL: 'bg-orange-500',
  EMERGENCY: 'bg-red-500',
  NORMAL: 'bg-emerald-400',
}

export function incidentIcon(i: Incident) {
  if (i.type === 'DEVICE_OFFLINE') return WifiOff
  if (i.variable === 'HUMIDITY') return Droplets
  if (i.variable === 'LUMINOSITY') return Sun
  return Thermometer
}

/** Duração ao vivo para incidentes abertos. */
export function useIncidentDuration(i: Incident) {
  const now = useNow(1000)
  if (i.normalizedAt) return i.durationSeconds
  return Math.max(0, Math.floor((now - new Date(i.startedAt).getTime()) / 1000))
}

/** Cartão no formato "🔴 TEMPERATURA CRÍTICA · Adega Premium · 25.8°C · Limite 18°C · 04:32 · ATIVO". */
export function IncidentCard({ incident: i }: { incident: Incident }) {
  const ack = useAcknowledgeIncident()
  const duration = useIncidentDuration(i)
  const Icon = incidentIcon(i)
  const level = i.status === 'RESOLVED' ? i.severity : i.currentSeverity
  const title = i.type === 'DEVICE_OFFLINE' ? 'DISPOSITIVO OFFLINE' : `${i.typeLabel.toUpperCase()} · ${LEVEL[level].label.toUpperCase()}`
  return (
    <div className="relative flex animate-fade-in gap-3 overflow-hidden rounded-xl border border-line bg-surface-2/70 p-3.5 pl-4">
      <span className={clsx('absolute inset-y-0 left-0 w-1', BAR[level], i.status !== 'RESOLVED' && 'animate-pulse-soft')} />
      <span className="mt-0.5 flex size-8 shrink-0 items-center justify-center rounded-lg bg-surface-3">
        <Icon className="size-4 text-fg/80" />
      </span>
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <p className="text-xs font-semibold tracking-wide">{title}</p>
          <Badge tone={INCIDENT_STATUS[i.status]} />
          {i.demo && <span className="rounded bg-wine-800/60 px-1.5 text-[10px] text-wine-200">demo</span>}
        </div>
        <p className="mt-0.5 truncate text-xs text-muted">
          <Link to={`/devices/${i.deviceId}`} className="hover:text-fg">{i.zoneLabel ?? i.deviceName}</Link> · {i.deviceName}
        </p>
        <dl className="tabular mt-2 grid grid-cols-2 gap-x-4 gap-y-1 text-xs sm:grid-cols-4">
          {i.type !== 'DEVICE_OFFLINE' && (
            <>
              <div><dt className="text-subtle">Atual</dt><dd className="font-medium">{fmtValue(i.lastValue, i.unit ?? '')}</dd></div>
              <div><dt className="text-subtle">Limite</dt><dd className="font-medium">{fmtValue(i.limitValue, i.unit ?? '')}</dd></div>
            </>
          )}
          <div><dt className="text-subtle">Duração</dt><dd className="font-medium">{fmtDuration(duration)}</dd></div>
          <div><dt className="text-subtle">Início</dt><dd className="font-medium">{fmtDateTime(i.startedAt)}</dd></div>
        </dl>
      </div>
      {i.status === 'ACTIVE' && i.type !== 'DEVICE_OFFLINE' && (
        <Button size="sm" variant="ghost" icon={<BellOff className="size-3.5" />} loading={ack.isPending}
          onClick={() => ack.mutate(i.id)} title="Reconhecer: silencia o buzzer (LED continua até normalizar)">
          <span className="hidden sm:inline">Silenciar</span>
        </Button>
      )}
    </div>
  )
}
