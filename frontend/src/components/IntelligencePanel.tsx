import clsx from 'clsx'
import { Sparkles } from 'lucide-react'
import type { Intelligence, RiskDataStatus } from '../api/types'
import { fmtAgo } from '../lib/format'
import { RISK, riskLevelOf } from '../lib/status'
import { Badge, Card, CardHeader } from './ui'

const DATA_NOTE: Record<RiskDataStatus, string | null> = {
  OK: null,
  STALE: 'Dados desatualizados: o risco não é avaliado com leitura antiga.',
  NO_DATA: 'Sem leituras deste dispositivo.',
}

/** WineGuard Intelligence de um dispositivo: índice, fatores com explicação e recomendações. */
export function IntelligencePanel({ data, className }: { data?: Intelligence; className?: string }) {
  if (!data) return null
  const r = data.report
  const tone = RISK[r.level]
  return (
    <Card className={className}>
      <CardHeader
        title="WineGuard Intelligence"
        subtitle="Risco de conservação explicável (0–100), a partir dos limites configurados"
        icon={<Sparkles className="size-4" />}
        action={<Badge tone={tone} />}
      />
      <div className="grid gap-5 p-4 md:grid-cols-12">
        <div className="md:col-span-3">
          <div className={clsx('text-4xl font-semibold tabular-nums', tone.text)}>
            {r.index ?? '—'}
            <span className="ml-1 text-sm font-normal text-muted">/100</span>
          </div>
          <p className="mt-2 text-sm">{r.summary}</p>
          {DATA_NOTE[r.dataStatus] && <p className="mt-2 text-xs text-amber-300">{DATA_NOTE[r.dataStatus]}</p>}
          {r.lastReadingAt && <p className="mt-1 text-[11px] text-subtle">Última leitura {fmtAgo(Math.round((Date.now() - new Date(r.lastReadingAt).getTime()) / 1000))}</p>}
        </div>

        <div className="space-y-3 md:col-span-5">
          <h4 className="text-xs font-semibold">Fatores de risco</h4>
          {r.factors.map((f) => (
            <div key={f.variable}>
              <div className="flex items-baseline justify-between text-xs">
                <span>{f.label}</span>
                <span className="tabular-nums text-muted">{f.status === 'SEM_LEITURA' ? 'sem leitura' : f.risk}</span>
              </div>
              <div className="mt-1 h-1.5 rounded-full bg-white/5">
                <div className={clsx('h-1.5 rounded-full', RISK[riskLevelOf(f.risk)].dot)} style={{ width: `${f.risk}%` }} />
              </div>
              <p className="mt-1 text-[11px] text-muted">{f.explanation}</p>
            </div>
          ))}
        </div>

        <div className="md:col-span-4">
          <h4 className="text-xs font-semibold">Recomendações</h4>
          {r.recommendations.length ? (
            <ul className="mt-2 list-disc space-y-1.5 pl-4 text-xs">
              {r.recommendations.map((x) => <li key={x}>{x}</li>)}
            </ul>
          ) : (
            <p className="mt-2 text-xs text-muted">Nenhuma ação necessária.</p>
          )}
          <p className="mt-3 text-[11px] text-subtle">{r.disclaimer}</p>
        </div>
      </div>
    </Card>
  )
}
