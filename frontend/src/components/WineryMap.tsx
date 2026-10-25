import clsx from 'clsx'
import { Droplets, Sun, Thermometer, WifiOff } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import type { Zone, ZoneStatus } from '../api/types'
import { fmtValue } from '../lib/format'
import { ZONE_STATUS } from '../lib/status'
import { ScorePill } from './WineScoreGauge'

/** Disposição das áreas na planta (grid de 12 colunas). */
const LAYOUT: Record<Zone, { span: string; decor: 'racks' | 'boxes' | 'table' }> = {
  PREMIUM_CELLAR: { span: 'md:col-span-6', decor: 'racks' },
  RESERVE_CELLAR: { span: 'md:col-span-6', decor: 'racks' },
  STOCK: { span: 'md:col-span-5', decor: 'boxes' },
  TASTING_ROOM: { span: 'md:col-span-7', decor: 'table' },
}

const DECOR: Record<string, string> = {
  racks: 'repeating-linear-gradient(90deg, rgb(255 255 255 / 0.035) 0 2px, transparent 2px 26px)',
  boxes: 'repeating-linear-gradient(45deg, rgb(255 255 255 / 0.03) 0 10px, transparent 10px 20px)',
  table: 'radial-gradient(circle at 78% 60%, rgb(255 255 255 / 0.05) 0 38px, transparent 39px)',
}

/** Mapa da vinheria: cada área muda de cor conforme o estado do(s) seu(s) dispositivo(s). Clique abre o dispositivo. */
export function WineryMap({ zones }: { zones: ZoneStatus[] }) {
  const navigate = useNavigate()
  return (
    <div className="rounded-xl border border-line-strong bg-surface-2/60 p-2">
      <div className="grid grid-cols-1 gap-2 md:grid-cols-12">
        {zones.map((z) => {
          const style = ZONE_STATUS[z.status]
          const d = z.devices[0]
          const layout = LAYOUT[z.zone]
          return (
            <button
              key={z.zone}
              type="button"
              disabled={!d}
              onClick={() => d && navigate(`/devices/${d.id}`)}
              className={clsx(
                'group relative min-h-[132px] overflow-hidden rounded-lg border-2 p-3.5 text-left transition-all',
                'hover:brightness-125 focus-visible:outline-2 focus-visible:outline-wine-400 disabled:cursor-default',
                layout.span,
                z.status === 'critical' && 'shadow-[0_0_24px_-6px_rgb(239_68_68/0.6)]',
                z.status === 'offline' && 'grayscale',
              )}
              style={{ borderColor: style.stroke, backgroundColor: style.fill, backgroundImage: DECOR[layout.decor] }}
              aria-label={`${z.label}: ${style.label}`}
            >
              <div className="flex items-start justify-between gap-2">
                <div>
                  <p className="text-[11px] font-semibold uppercase tracking-[0.12em] text-fg/90">{z.label}</p>
                  <p className={clsx('mt-0.5 flex items-center gap-1.5 text-xs font-medium', style.text)}>
                    <span
                      className={clsx('size-2 rounded-full', z.status === 'critical' && 'animate-pulse-soft')}
                      style={{ background: style.stroke }}
                    />
                    {style.label}
                    {z.devices.length > 1 && <span className="text-subtle">· {z.devices.length} sensores</span>}
                  </p>
                </div>
                {d && <ScorePill score={d.score} />}
              </div>

              {d ? (
                z.status === 'offline' ? (
                  <div className="mt-5 flex items-center gap-2 text-xs text-zinc-400">
                    <WifiOff className="size-4" /> {d.name} sem comunicação
                  </div>
                ) : (
                  <div className="mt-5 flex flex-wrap items-center gap-x-4 gap-y-1 text-sm">
                    <span className="tabular inline-flex items-center gap-1"><Thermometer className="size-3.5 text-wine-300" />{fmtValue(d.temperature, '°C')}</span>
                    <span className="tabular inline-flex items-center gap-1"><Droplets className="size-3.5 text-sky-300" />{fmtValue(d.humidity, '%', 0)}</span>
                    <span className="tabular inline-flex items-center gap-1"><Sun className="size-3.5 text-gold-400" />{fmtValue(d.luminosity, '%', 0)}</span>
                  </div>
                )
              ) : (
                <p className="mt-5 text-xs text-subtle">Nenhum dispositivo nesta área</p>
              )}
              {d && <p className="mt-2 truncate text-[11px] text-muted">{d.name}</p>}
            </button>
          )
        })}
      </div>
    </div>
  )
}
