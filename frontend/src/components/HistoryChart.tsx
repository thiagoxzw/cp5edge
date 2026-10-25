import { Database } from 'lucide-react'
import { Area, AreaChart, CartesianGrid, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { useHistory } from '../api/hooks'
import type { Period, VariableKey } from '../api/types'
import { fmtShortDate, fmtValue } from '../lib/format'
import { VARIABLES } from '../lib/status'
import { EmptyState, ErrorBox, Spinner } from './ui'

const tickFormatter = (period: Period) => (t: number) => {
  const d = new Date(t)
  if (period === '1h' || period === '6h' || period === '24h') {
    return d.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })
  }
  return d.toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' })
}

/** Gráfico histórico (STH-Comet :8666) com as linhas dos limites configurados. */
export function HistoryChart({ deviceId, variable, period, height = 300 }: { deviceId: number | undefined; variable: VariableKey; period: Period; height?: number }) {
  const { data, isLoading, error } = useHistory(deviceId, variable, period)
  const meta = VARIABLES[variable]

  if (deviceId === undefined) return <EmptyState title="Selecione um dispositivo" />
  if (isLoading) return <Spinner label="Consultando histórico…" />
  if (error) return <div className="p-4"><ErrorBox error={error} /></div>
  if (!data || data.points.length === 0) {
    return (
      <EmptyState icon={<Database className="size-6" />} title="Sem dados no período">
        Nenhuma medição registrada para {meta.label.toLowerCase()} neste intervalo. Fonte: {data?.source ?? '—'}
      </EmptyState>
    )
  }

  const points = data.points.map((p) => ({ t: new Date(p.t).getTime(), v: p.v }))
  const values = points.map((p) => p.v)
  const lo = Math.min(...values, data.limits.min ?? Infinity)
  const hi = Math.max(...values, data.limits.max ?? -Infinity)
  const pad = Math.max(1, (hi - lo) * 0.15)
  const gradientId = `grad-${variable}`

  return (
    <div>
      <div style={{ height }}>
        <ResponsiveContainer width="100%" height="100%">
          <AreaChart data={points} margin={{ top: 12, right: 16, bottom: 0, left: -8 }}>
            <defs>
              <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor={meta.color} stopOpacity={0.35} />
                <stop offset="100%" stopColor={meta.color} stopOpacity={0} />
              </linearGradient>
            </defs>
            <CartesianGrid vertical={false} strokeDasharray="3 3" />
            <XAxis dataKey="t" type="number" scale="time" domain={['dataMin', 'dataMax']} tickFormatter={tickFormatter(period)}
              tickLine={false} axisLine={false} minTickGap={40} />
            <YAxis domain={[Math.floor(lo - pad), Math.ceil(hi + pad)]} tickLine={false} axisLine={false} width={44}
              tickFormatter={(v: number) => `${v}${meta.unit === '%' ? '%' : '°'}`} />
            <Tooltip
              cursor={{ stroke: 'var(--color-line-strong)' }}
              contentStyle={{ background: 'var(--color-surface-2)', border: '1px solid var(--color-line-strong)', borderRadius: 10, fontSize: 12 }}
              labelStyle={{ color: 'var(--color-muted)' }}
              labelFormatter={(t) => fmtShortDate(Number(t))}
              formatter={(v) => [fmtValue(Number(v), meta.unit), meta.label]}
            />
            {data.limits.max !== null && (
              <ReferenceLine y={data.limits.max} stroke="#ef4444" strokeDasharray="6 4" strokeOpacity={0.8}
                label={{ value: `máx ${data.limits.max}${meta.unit}`, fill: '#fca5a5', fontSize: 11, position: 'insideTopRight' }} />
            )}
            {data.limits.min !== null && (
              <ReferenceLine y={data.limits.min} stroke="#38bdf8" strokeDasharray="6 4" strokeOpacity={0.8}
                label={{ value: `mín ${data.limits.min}${meta.unit}`, fill: '#7dd3fc', fontSize: 11, position: 'insideBottomRight' }} />
            )}
            <Area type="monotone" dataKey="v" stroke={meta.color} strokeWidth={2} fill={`url(#${gradientId})`}
              dot={false} activeDot={{ r: 4 }} isAnimationActive={false} />
          </AreaChart>
        </ResponsiveContainer>
      </div>
      <div className="tabular mt-3 flex flex-wrap items-center gap-x-5 gap-y-1 border-t border-line px-1 pt-3 text-xs text-muted">
        <span>Mín <b className="text-fg">{fmtValue(data.stats.min, meta.unit)}</b></span>
        <span>Média <b className="text-fg">{fmtValue(data.stats.avg, meta.unit)}</b></span>
        <span>Máx <b className="text-fg">{fmtValue(data.stats.max, meta.unit)}</b></span>
        <span>{data.stats.count} pontos</span>
        <span className="ml-auto inline-flex items-center gap-1 text-subtle"><Database className="size-3" /> {data.source} · {data.resolution}</span>
      </div>
    </div>
  )
}
