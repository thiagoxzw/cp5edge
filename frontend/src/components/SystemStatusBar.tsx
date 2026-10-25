import clsx from 'clsx'
import { Activity, Server } from 'lucide-react'
import { useState } from 'react'
import { useRetryProvisioning, useSystemStatus } from '../api/hooks'
import { fmtTime } from '../lib/format'
import { Button, Modal } from './ui'

/** Barra superior: saúde de Orion / IoT Agent / STH-Comet e do ciclo de monitoramento. */
export function SystemStatusBar() {
  const { data, error } = useSystemStatus()
  const [open, setOpen] = useState(false)
  const retry = useRetryProvisioning()

  if (error) {
    return (
      <span className="inline-flex items-center gap-2 rounded-full bg-red-500/10 px-3 py-1 text-xs text-red-300 ring-1 ring-red-500/30">
        <span className="size-1.5 rounded-full bg-red-500" /> Backend indisponível
      </span>
    )
  }
  if (!data) return null

  const fiware = data.mode === 'fiware'
  const allOk = data.telemetryAvailable && data.components.every((c) => c.ok) && (!fiware || data.provisioningReady)

  return (
    <>
      <div className="flex min-w-0 flex-1 items-center gap-2 overflow-x-auto">
        <button
          onClick={() => setOpen(true)}
          className={clsx(
            'inline-flex shrink-0 items-center gap-2 rounded-full px-3 py-1 text-xs ring-1 transition-colors',
            allOk ? 'bg-emerald-400/10 text-emerald-300 ring-emerald-400/25' : 'bg-amber-400/10 text-amber-300 ring-amber-400/30',
          )}
        >
          <Server className="size-3.5" />
          {fiware ? (allOk ? 'FIWARE conectado' : 'FIWARE com problemas') : 'Modo standalone (sem FIWARE)'}
        </button>
        {fiware &&
          data.components.map((c) => (
            <span key={c.name} className="hidden shrink-0 items-center gap-1.5 text-[11px] text-muted md:inline-flex">
              <span className={clsx('size-1.5 rounded-full', c.ok ? 'bg-emerald-400' : 'bg-red-500')} />
              {c.name}
            </span>
          ))}
        <span className="ml-auto hidden shrink-0 items-center gap-1.5 text-[11px] text-subtle sm:inline-flex">
          <Activity className="size-3" /> ciclo {fmtTime(data.lastCycleAt)}
        </span>
      </div>

      <Modal open={open} onClose={() => setOpen(false)} title="Saúde da plataforma">
        <dl className="space-y-3 text-sm">
          <Row label="Modo" value={data.mode} />
          {fiware && <Row label="fiware-service / servicepath" value={`${data.fiwareService} ${data.fiwareServicePath}`} />}
          {fiware && <Row label="Tipo de entidade" value={data.entityType} />}
          {data.components.map((c) => (
            <div key={c.name} className="rounded-lg border border-line bg-surface-2 p-3">
              <div className="flex items-center justify-between">
                <span className="font-medium">{c.name}</span>
                <span className={c.ok ? 'text-emerald-300' : 'text-red-300'}>{c.ok ? `ok ${c.version ?? ''}` : 'falha'}</span>
              </div>
              <p className="mt-1 font-mono text-[11px] text-subtle">{c.url}</p>
              {c.detail && <p className="mt-1 text-xs text-red-300">{c.detail}</p>}
            </div>
          ))}
          <Row label="Leitura de telemetria" value={data.telemetryAvailable ? 'ok' : `falha: ${data.telemetryError}`} ok={data.telemetryAvailable} />
          {fiware && <Row label="Provisionamento (service group + subscription STH)" value={data.provisioningReady ? 'ok' : data.provisioningError ?? 'pendente'} ok={data.provisioningReady} />}
          <Row label="Dispositivos virtuais" value={data.simulatorReady ? 'publicando' : 'aguardando MQTT'} ok={data.simulatorReady} />
        </dl>
        {fiware && (
          <div className="mt-5 flex justify-end">
            <Button variant="primary" loading={retry.isPending} onClick={() => retry.mutate()}>Reprovisionar FIWARE</Button>
          </div>
        )}
      </Modal>
    </>
  )
}

function Row({ label, value, ok }: { label: string; value: string; ok?: boolean }) {
  return (
    <div className="flex items-start justify-between gap-4">
      <dt className="text-muted">{label}</dt>
      <dd className={clsx('text-right font-medium', ok === false && 'text-red-300', ok === true && 'text-emerald-300')}>{value}</dd>
    </div>
  )
}
