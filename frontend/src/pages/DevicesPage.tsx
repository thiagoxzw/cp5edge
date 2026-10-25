import { Eye, Pencil, Plus, Power, RefreshCw, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useDeleteDevice, useDevices, useDeviceStatus, useProvisionDevice } from '../api/hooks'
import type { Device } from '../api/types'
import { DeviceForm } from '../components/DeviceForm'
import { PageHeader } from '../components/Layout'
import { Badge, Button, Card, EmptyState, ErrorBox, Modal, Spinner } from '../components/ui'
import { ScorePill } from '../components/WineScoreGauge'
import { fmtAgo, fmtDateTime, fmtValue } from '../lib/format'
import { HEALTH } from '../lib/status'

const PROVISIONING: Record<Device['provisioning'], { label: string; cls: string }> = {
  PROVISIONED: { label: 'Provisionado', cls: 'text-emerald-300' },
  PENDING: { label: 'Pendente', cls: 'text-amber-300' },
  FAILED: { label: 'Falhou', cls: 'text-red-300' },
  NOT_REQUIRED: { label: '—', cls: 'text-subtle' },
}

const SENSOR_SHORT = { TEMPERATURE: 'T', HUMIDITY: 'UR', LUMINOSITY: 'Luz' }

export function DevicesPage() {
  const { data, isLoading, error } = useDevices()
  const [editing, setEditing] = useState<Device | 'new' | null>(null)
  const [deleting, setDeleting] = useState<Device | null>(null)
  const status = useDeviceStatus()
  const provision = useProvisionDevice()
  const del = useDeleteDevice()

  return (
    <>
      <PageHeader
        title="Dispositivos"
        subtitle="Cadastro e gerenciamento dos ESP32 provisionados no FIWARE"
        actions={<Button variant="primary" icon={<Plus className="size-4" />} onClick={() => setEditing('new')}>Novo dispositivo</Button>}
      />

      {isLoading ? <Spinner /> : error ? <ErrorBox error={error} /> : (
        <Card className="overflow-hidden">
          {data!.length === 0 ? (
            <EmptyState title="Nenhum dispositivo cadastrado">Cadastre o ESP32 da Adega Premium com o identificador configurado no firmware.</EmptyState>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[920px] text-sm">
                <thead className="border-b border-line text-left text-[11px] uppercase tracking-wider text-subtle">
                  <tr>
                    <th className="px-5 py-3 font-medium">Dispositivo</th>
                    <th className="px-3 py-3 font-medium">Local</th>
                    <th className="px-3 py-3 font-medium">Status</th>
                    <th className="px-3 py-3 font-medium">Leituras</th>
                    <th className="px-3 py-3 font-medium">Score</th>
                    <th className="px-3 py-3 font-medium">FIWARE</th>
                    <th className="px-3 py-3 font-medium">Cadastro</th>
                    <th className="px-5 py-3 text-right font-medium">Ações</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-line">
                  {data!.map((d) => (
                    <tr key={d.id} className="hover:bg-surface-2/60">
                      <td className="px-5 py-3">
                        <Link to={`/devices/${d.id}`} className="font-medium hover:text-wine-300">{d.name}</Link>
                        <p className="font-mono text-[11px] text-subtle">
                          {d.deviceKey} · {d.sensors.map((s) => SENSOR_SHORT[s]).join(' / ')}{d.simulated && ' · virtual'}
                        </p>
                      </td>
                      <td className="px-3 py-3 text-muted">{d.zoneLabel}</td>
                      <td className="px-3 py-3">
                        <Badge tone={HEALTH[d.live.health]} pulse={d.live.health === 'ALERT'} />
                        <p className="mt-1 text-[11px] text-subtle">contato {fmtAgo(d.live.secondsSinceLastSeen)}</p>
                      </td>
                      <td className="tabular px-3 py-3 text-xs text-muted">
                        {fmtValue(d.live.temperature, '°C')} · {fmtValue(d.live.humidity, '%', 0)} · {fmtValue(d.live.luminosity, '%', 0)}
                      </td>
                      <td className="px-3 py-3"><ScorePill score={d.live.score} /></td>
                      <td className="px-3 py-3 text-xs">
                        <span className={PROVISIONING[d.provisioning].cls} title={d.provisioningMessage ?? ''}>{PROVISIONING[d.provisioning].label}</span>
                      </td>
                      <td className="px-3 py-3 text-xs text-subtle">{fmtDateTime(d.createdAt)}</td>
                      <td className="px-5 py-3">
                        <div className="flex justify-end gap-1">
                          <Link to={`/devices/${d.id}`} className="rounded-md p-1.5 text-muted hover:bg-surface-3 hover:text-fg" title="Visualizar"><Eye className="size-4" /></Link>
                          <button className="rounded-md p-1.5 text-muted hover:bg-surface-3 hover:text-fg" title="Editar" onClick={() => setEditing(d)}><Pencil className="size-4" /></button>
                          {(d.provisioning === 'FAILED' || d.provisioning === 'PENDING') && (
                            <button className="rounded-md p-1.5 text-amber-300 hover:bg-surface-3" title="Provisionar novamente" onClick={() => provision.mutate(d.id)}><RefreshCw className="size-4" /></button>
                          )}
                          <button className="rounded-md p-1.5 text-muted hover:bg-surface-3 hover:text-fg" title={d.status === 'ACTIVE' ? 'Desativar' : 'Ativar'}
                            onClick={() => status.mutate({ id: d.id, status: d.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE' })}>
                            <Power className={d.status === 'ACTIVE' ? 'size-4 text-emerald-300' : 'size-4'} />
                          </button>
                          <button className="rounded-md p-1.5 text-muted hover:bg-red-500/15 hover:text-red-300" title="Excluir" onClick={() => setDeleting(d)}><Trash2 className="size-4" /></button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>
      )}
      {(status.error || provision.error) && <div className="mt-3"><ErrorBox error={status.error ?? provision.error} /></div>}

      <Modal open={editing !== null} onClose={() => setEditing(null)} title={editing === 'new' ? 'Novo dispositivo' : 'Editar dispositivo'} wide>
        {editing !== null && <DeviceForm device={editing === 'new' ? undefined : editing} onDone={() => setEditing(null)} />}
      </Modal>

      <Modal open={deleting !== null} onClose={() => setDeleting(null)} title="Excluir dispositivo">
        <p className="text-sm text-muted">
          Excluir <b className="text-fg">{deleting?.name}</b> ({deleting?.deviceKey})? O dispositivo será removido do IoT Agent e do Orion,
          e seus incidentes serão apagados. O histórico no STH-Comet é preservado. Para apenas pausar o monitoramento, use <b className="text-fg">Desativar</b>.
        </p>
        {del.error && <div className="mt-3"><ErrorBox error={del.error} /></div>}
        <div className="mt-5 flex justify-end gap-2">
          <Button variant="ghost" onClick={() => setDeleting(null)}>Cancelar</Button>
          <Button variant="danger" loading={del.isPending} icon={<Trash2 className="size-4" />}
            onClick={() => deleting && del.mutate(deleting.id, { onSuccess: () => setDeleting(null) })}>Excluir</Button>
        </div>
      </Modal>
    </>
  )
}
