import clsx from 'clsx'
import { BellOff, ChevronLeft, ChevronRight, ShieldCheck } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useAcknowledgeIncident, useDevices, useIncidents } from '../api/hooks'
import type { Incident } from '../api/types'
import { incidentIcon, useIncidentDuration } from '../components/IncidentCard'
import { PageHeader } from '../components/Layout'
import { Badge, Button, Card, EmptyState, ErrorBox, inputClass, Segmented, Spinner } from '../components/ui'
import { fmtDateTime, fmtDuration, fmtValue } from '../lib/format'
import { INCIDENT_STATUS, LEVEL } from '../lib/status'

function Row({ i }: { i: Incident }) {
  const duration = useIncidentDuration(i)
  const ack = useAcknowledgeIncident()
  const Icon = incidentIcon(i)
  const unit = i.unit ?? ''
  return (
    <tr className={clsx('hover:bg-surface-2/60', i.status !== 'RESOLVED' && 'bg-red-500/[0.03]')}>
      <td className="px-5 py-3">
        <div className="flex items-center gap-2.5">
          <Icon className="size-4 shrink-0 text-muted" />
          <div>
            <p className="font-medium">{i.typeLabel}</p>
            <p className="text-[11px] text-subtle">#{i.id}{i.demo && ' · demo'}</p>
          </div>
        </div>
      </td>
      <td className="px-3 py-3">
        <Link to={`/devices/${i.deviceId}`} className="hover:text-wine-300">{i.deviceName}</Link>
        <p className="text-[11px] text-subtle">{i.zoneLabel}</p>
      </td>
      <td className="tabular px-3 py-3 text-xs">
        {i.type === 'DEVICE_OFFLINE' ? <span className="text-subtle">—</span> : (
          <>
            <p>detectado <b>{fmtValue(i.detectedValue, unit)}</b> · pico <b>{fmtValue(i.peakValue, unit)}</b></p>
            <p className="text-subtle">limite {fmtValue(i.limitValue, unit)}</p>
          </>
        )}
      </td>
      <td className="tabular px-3 py-3 text-xs">
        <p>{fmtDateTime(i.startedAt)}</p>
        <p className="text-subtle">{i.normalizedAt ? `→ ${fmtDateTime(i.normalizedAt)}` : 'em andamento'}</p>
      </td>
      <td className="tabular px-3 py-3 font-medium">{fmtDuration(duration)}</td>
      <td className="px-3 py-3"><Badge tone={LEVEL[i.severity]} /></td>
      <td className="px-3 py-3"><Badge tone={INCIDENT_STATUS[i.status]} pulse={i.status === 'ACTIVE'} /></td>
      <td className="px-5 py-3 text-right">
        {i.status === 'ACTIVE' && i.type !== 'DEVICE_OFFLINE' && (
          <Button size="sm" variant="ghost" icon={<BellOff className="size-3.5" />} loading={ack.isPending} onClick={() => ack.mutate(i.id)}>Silenciar</Button>
        )}
      </td>
    </tr>
  )
}

/** Central de incidentes: histórico completo com filtros. */
export function IncidentsPage() {
  const [status, setStatus] = useState<'open' | 'RESOLVED' | ''>('open')
  const [severity, setSeverity] = useState('')
  const [deviceId, setDeviceId] = useState<number | undefined>()
  const [page, setPage] = useState(0)
  const { data: devices } = useDevices()
  const { data, isLoading, error } = useIncidents({ status, severity, deviceId, page })

  const reset = <T,>(fn: (v: T) => void) => (v: T) => { fn(v); setPage(0) }

  return (
    <>
      <PageHeader title="Central de incidentes"
        subtitle="Uma ocorrência por anomalia — encerrada automaticamente quando o ambiente normaliza" />

      <Card className="mb-4 flex flex-wrap items-center gap-3 p-3">
        <Segmented value={status} onChange={reset(setStatus)}
          options={[{ value: 'open', label: 'Abertos' }, { value: 'RESOLVED', label: 'Resolvidos' }, { value: '', label: 'Todos' }]} />
        <select className={clsx(inputClass, 'sm:w-56')} value={severity} onChange={(e) => reset(setSeverity)(e.target.value)}>
          <option value="">Todas as severidades</option>
          <option value="ATTENTION">🟡 Atenção</option>
          <option value="CRITICAL">🟠 Crítico</option>
          <option value="EMERGENCY">🔴 Emergência</option>
        </select>
        <select className={clsx(inputClass, 'sm:w-56')} value={deviceId ?? ''} onChange={(e) => reset(setDeviceId)(e.target.value ? Number(e.target.value) : undefined)}>
          <option value="">Todos os dispositivos</option>
          {devices?.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}
        </select>
        {data && <span className="ml-auto text-xs text-muted">{data.totalElements} incidentes</span>}
      </Card>

      {isLoading ? <Spinner /> : error ? <ErrorBox error={error} /> : (
        <Card className="overflow-hidden">
          {data!.content.length === 0 ? (
            <EmptyState icon={<ShieldCheck className="size-7 text-emerald-400" />} title={status === 'open' ? 'Nenhum incidente aberto' : 'Nenhum incidente encontrado'} />
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[980px] text-sm">
                <thead className="border-b border-line text-left text-[11px] uppercase tracking-wider text-subtle">
                  <tr>
                    <th className="px-5 py-3 font-medium">Tipo</th>
                    <th className="px-3 py-3 font-medium">Dispositivo / local</th>
                    <th className="px-3 py-3 font-medium">Valores</th>
                    <th className="px-3 py-3 font-medium">Início / normalização</th>
                    <th className="px-3 py-3 font-medium">Duração</th>
                    <th className="px-3 py-3 font-medium">Severidade</th>
                    <th className="px-3 py-3 font-medium">Status</th>
                    <th className="px-5 py-3" />
                  </tr>
                </thead>
                <tbody className="divide-y divide-line">{data!.content.map((i) => <Row key={i.id} i={i} />)}</tbody>
              </table>
            </div>
          )}
          {data && data.totalPages > 1 && (
            <div className="flex items-center justify-between border-t border-line px-5 py-3 text-xs text-muted">
              <span>Página {data.page + 1} de {data.totalPages}</span>
              <div className="flex gap-1">
                <Button size="sm" variant="ghost" disabled={page === 0} onClick={() => setPage(page - 1)} icon={<ChevronLeft className="size-4" />} />
                <Button size="sm" variant="ghost" disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)} icon={<ChevronRight className="size-4" />} />
              </div>
            </div>
          )}
        </Card>
      )}
    </>
  )
}
