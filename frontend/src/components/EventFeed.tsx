import clsx from 'clsx'
import { AlertTriangle, CheckCircle2, Info, Siren } from 'lucide-react'
import type { EventItem } from '../api/types'
import { fmtTime } from '../lib/format'

const ICON = {
  INFO: { icon: Info, cls: 'text-sky-300 bg-sky-400/10' },
  SUCCESS: { icon: CheckCircle2, cls: 'text-emerald-300 bg-emerald-400/10' },
  WARNING: { icon: AlertTriangle, cls: 'text-amber-300 bg-amber-400/10' },
  CRITICAL: { icon: Siren, cls: 'text-red-300 bg-red-500/10' },
}

export function EventFeed({ events, compact }: { events: EventItem[]; compact?: boolean }) {
  if (events.length === 0) return <p className="px-5 py-8 text-center text-xs text-muted">Nenhum evento registrado ainda.</p>
  return (
    <ol className="divide-y divide-line">
      {events.map((e) => {
        const { icon: Icon, cls } = ICON[e.level]
        return (
          <li key={e.id} className={clsx('flex gap-3 px-5', compact ? 'py-2.5' : 'py-3')}>
            <span className={clsx('mt-0.5 flex size-6 shrink-0 items-center justify-center rounded-full', cls)}>
              <Icon className="size-3.5" />
            </span>
            <div className="min-w-0 flex-1">
              <p className="text-xs leading-relaxed text-fg/90">
                {e.deviceName && <span className="font-medium text-fg">{e.deviceName} · </span>}
                {e.message}
              </p>
              <p className="tabular mt-0.5 text-[11px] text-subtle">{fmtTime(e.timestamp)}</p>
            </div>
          </li>
        )
      })}
    </ol>
  )
}
