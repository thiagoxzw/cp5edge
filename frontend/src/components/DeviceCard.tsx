import clsx from 'clsx'
import { Bell, Cpu, Droplets, Lightbulb, MapPin, Sun, Thermometer } from 'lucide-react'
import { Link } from 'react-router-dom'
import type { Device } from '../api/types'
import { fmtAgo, fmtValue } from '../lib/format'
import { HEALTH, LEVEL } from '../lib/status'
import { Badge } from './ui'
import { ScorePill } from './WineScoreGauge'

function Metric({ icon, label, value, warn }: { icon: React.ReactNode; label: string; value: string; warn?: boolean }) {
  return (
    <div className={clsx('rounded-lg bg-surface-2 px-2.5 py-2 ring-1 ring-inset', warn ? 'ring-red-500/40' : 'ring-line')}>
      <div className="flex items-center gap-1 text-[10px] uppercase tracking-wider text-subtle">{icon}{label}</div>
      <div className={clsx('tabular mt-0.5 text-base font-semibold', warn && 'text-red-300')}>{value}</div>
    </div>
  )
}

export function DeviceCard({ device }: { device: Device }) {
  const l = device.live
  const health = HEALTH[l.health]
  const offline = l.health === 'OFFLINE' || l.health === 'INACTIVE'
  const pending = new Set(l.pending)
  const alerting = new Set(l.buzzerAlerts)
  const warn = (code: string) => pending.has(code) || alerting.has(code)
  return (
    <Link
      to={`/devices/${device.id}`}
      className={clsx(
        'group block animate-fade-in rounded-2xl border bg-surface/80 p-4 transition-colors hover:border-wine-500/60',
        l.health === 'ALERT' ? 'border-red-500/40' : 'border-line',
      )}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="truncate text-sm font-semibold">{device.name}</p>
          <p className="mt-0.5 flex items-center gap-1 text-xs text-muted">
            <MapPin className="size-3" /> {device.zoneLabel}
            <span className="text-subtle">· {device.deviceKey}</span>
            {device.simulated && <span className="rounded bg-surface-3 px-1 text-[10px] text-subtle">virtual</span>}
          </p>
        </div>
        <Badge tone={health} pulse={l.health === 'ALERT'} />
      </div>

      <div className={clsx('mt-4 grid grid-cols-3 gap-2', offline && 'opacity-50')}>
        <Metric icon={<Thermometer className="size-3" />} label="Temp." value={fmtValue(l.temperature, '°')} warn={warn('temp')} />
        <Metric icon={<Droplets className="size-3" />} label="Umid." value={fmtValue(l.humidity, '%', 0)} warn={warn('hum')} />
        <Metric icon={<Sun className="size-3" />} label="Luz" value={fmtValue(l.luminosity, '%', 0)} warn={warn('lux')} />
      </div>

      <div className="mt-4 flex items-center justify-between">
        <div className="flex items-center gap-3 text-xs text-muted">
          <span className="text-subtle">Wine Score</span>
          <ScorePill score={l.score} />
        </div>
        <div className="flex items-center gap-2 text-xs">
          {l.ledActive && <Lightbulb className="size-4 animate-pulse-soft text-sky-400" aria-label="LED azul piscando" />}
          {l.buzzerAlerts.length > 0 && <Bell className="size-4 animate-pulse-soft text-red-400" aria-label="Buzzer ativo" />}
          {l.environmentLevel !== 'NORMAL' && !offline && <Badge tone={LEVEL[l.environmentLevel]} />}
          {offline && (
            <span className="inline-flex items-center gap-1 text-subtle">
              <Cpu className="size-3" /> {fmtAgo(l.secondsSinceLastSeen)}
            </span>
          )}
        </div>
      </div>
    </Link>
  )
}
