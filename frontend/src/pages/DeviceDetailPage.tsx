import clsx from 'clsx'
import { ArrowLeft, Bell, BellOff, Cpu, Droplets, Lightbulb, LightbulbOff, Radio, Sun, Thermometer, Unplug, Plug } from 'lucide-react'
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useDevice, useDeviceEvents, useDeviceIntelligence, useIncidents, useSendCommand, useSimulateConnection } from '../api/hooks'
import type { Period, VariableKey } from '../api/types'
import { EventFeed } from '../components/EventFeed'
import { HistoryChart } from '../components/HistoryChart'
import { IncidentCard } from '../components/IncidentCard'
import { IntelligencePanel } from '../components/IntelligencePanel'
import { Badge, Button, Card, CardHeader, EmptyState, ErrorBox, Segmented, Spinner } from '../components/ui'
import { WineScoreGauge } from '../components/WineScoreGauge'
import { fmtAgo, fmtDateTime, fmtDuration, fmtValue } from '../lib/format'
import { CODE_LABEL, HEALTH, LEVEL, VARIABLES } from '../lib/status'

export const PERIODS: { value: Period; label: string }[] = [
  { value: '1h', label: '1h' },
  { value: '6h', label: '6h' },
  { value: '24h', label: '24h' },
  { value: '7d', label: '7 dias' },
  { value: '30d', label: '30 dias' },
]

export function DeviceDetailPage() {
  const id = Number(useParams().id)
  const { data: d, isLoading, error } = useDevice(id)
  const events = useDeviceEvents(id)
  const incidents = useIncidents({ deviceId: id, page: 0 })
  const intelligence = useDeviceIntelligence(id)
  const command = useSendCommand()
  const connection = useSimulateConnection()
  const [variable, setVariable] = useState<VariableKey>('temperature')
  const [period, setPeriod] = useState<Period>('1h')

  if (isLoading) return <Spinner />
  if (error || !d) return <ErrorBox error={error ?? 'Dispositivo não encontrado'} />
  const l = d.live
  const send = (cmd: string, param?: string) => command.mutate({ id, command: cmd, param })
  const offline = l.health === 'OFFLINE'

  const readings = [
    { key: 'temperature' as const, icon: Thermometer, value: l.temperature, code: 'temp' },
    { key: 'humidity' as const, icon: Droplets, value: l.humidity, code: 'hum' },
    { key: 'luminosity' as const, icon: Sun, value: l.luminosity, code: 'lux' },
  ]

  return (
    <>
      <Link to="/" className="mb-4 inline-flex items-center gap-1 text-xs text-muted hover:text-fg"><ArrowLeft className="size-3.5" /> Visão geral</Link>

      <div className="mb-6 flex flex-wrap items-start justify-between gap-4">
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="text-2xl font-semibold tracking-tight">{d.name}</h1>
            <Badge tone={HEALTH[l.health]} pulse={l.health === 'ALERT'} />
            {l.environmentLevel !== 'NORMAL' && !offline && <Badge tone={LEVEL[l.environmentLevel]} />}
          </div>
          <p className="mt-1 text-sm text-muted">
            {d.zoneLabel} · <span className="font-mono text-xs">{d.deviceKey}</span> · <span className="font-mono text-xs text-subtle">{d.entityId}</span>
          </p>
          {d.description && <p className="mt-1 text-xs text-subtle">{d.description}</p>}
        </div>
        <div className="text-right text-xs text-muted">
          <p>Último contato: <b className="text-fg">{fmtDateTime(l.lastSeenAt)}</b> ({fmtAgo(l.secondsSinceLastSeen)})</p>
          {offline && l.secondsSinceLastSeen !== null && <p className="mt-0.5 text-zinc-300">Tempo offline: <b>{fmtDuration(l.secondsSinceLastSeen)}</b></p>}
          {l.uptime !== null && <p className="mt-0.5 text-subtle">uptime do firmware {fmtDuration(l.uptime)}</p>}
        </div>
      </div>

      <div className="grid gap-4 lg:grid-cols-12">
        {/* Leituras atuais */}
        <div className="grid grid-cols-3 gap-3 lg:col-span-8">
          {readings.map((r) => {
            const meta = VARIABLES[r.key]
            const warn = l.pending.includes(r.code) || l.buzzerAlerts.includes(r.code)
            return (
              <Card key={r.key} className={clsx('p-4', warn && 'border-red-500/50', offline && 'opacity-50')}>
                <div className="flex items-center gap-1.5 text-xs text-muted"><r.icon className="size-3.5" style={{ color: meta.color }} /> {meta.label}</div>
                <p className={clsx('tabular mt-2 text-3xl font-semibold tracking-tight', warn && 'text-red-300')}>
                  {fmtValue(r.value, '', r.key === 'temperature' ? 1 : 0)}<span className="ml-0.5 text-base text-muted">{meta.unit}</span>
                </p>
                {l.pending.includes(r.code) && <p className="mt-1 text-[11px] text-amber-300">fora da faixa · aguardando tempo para disparo</p>}
              </Card>
            )
          })}

          {/* Gráfico */}
          <Card className="col-span-3">
            <CardHeader
              title="Histórico"
              subtitle="Fonte real indicada no rodapé (STH-Comet :8666 no modo FIWARE)"
              action={
                <div className="flex flex-wrap justify-end gap-2">
                  <Segmented value={variable} onChange={setVariable} options={(Object.keys(VARIABLES) as VariableKey[]).map((k) => ({ value: k, label: VARIABLES[k].label }))} />
                  <Segmented value={period} onChange={setPeriod} options={PERIODS} />
                </div>
              }
            />
            <div className="p-4"><HistoryChart deviceId={id} variable={variable} period={period} height={260} /></div>
          </Card>
        </div>

        {/* Score + controle */}
        <div className="space-y-4 lg:col-span-4">
          <Card className="p-5">
            <div className="flex items-center gap-5">
              <WineScoreGauge score={l.score} label={l.scoreLabel} size={116} />
              <div>
                <p className="text-xs font-medium uppercase tracking-wider text-subtle">Wine Score</p>
                <p className="mt-1 text-xs text-muted">100 menos as penalidades abaixo.</p>
              </div>
            </div>
            {l.scoreComponents.length > 0 ? (
              <ul className="mt-4 space-y-2.5">
                {l.scoreComponents.map((c) => (
                  <li key={c.key}>
                    <div className="flex justify-between text-xs">
                      <span>{c.label}</span>
                      <span className={clsx('tabular font-medium', c.penalty > 0.5 ? 'text-red-300' : 'text-muted')}>−{c.penalty.toFixed(1)}</span>
                    </div>
                    <div className="mt-1 h-1 overflow-hidden rounded-full bg-surface-3">
                      <div className="h-full rounded-full bg-wine-400 transition-all" style={{ width: `${Math.min(100, (c.penalty / c.maxPenalty) * 100)}%` }} />
                    </div>
                    <p className="mt-0.5 text-[11px] text-subtle">{c.detail}</p>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="mt-4 text-xs text-subtle">Score indisponível enquanto o dispositivo estiver offline.</p>
            )}
          </Card>

          <Card>
            <CardHeader title="Atuadores e comandos" subtitle="Backend → Orion → IoT Agent → MQTT → ESP32" icon={<Radio className="size-4" />} />
            <div className="space-y-4 p-5">
              <div className="grid grid-cols-2 gap-2 text-xs">
                <div className="rounded-lg bg-surface-2 p-3 ring-1 ring-line">
                  <p className="text-subtle">LED azul</p>
                  <p className={clsx('mt-1 flex items-center gap-1.5 font-medium', l.ledActive ? 'text-sky-300' : 'text-muted')}>
                    {l.ledActive ? <Lightbulb className="size-4 animate-pulse-soft" /> : <LightbulbOff className="size-4" />}
                    {l.ledActive === null ? 'não reportado' : l.ledActive ? 'piscando' : 'desligado'}
                  </p>
                </div>
                <div className="rounded-lg bg-surface-2 p-3 ring-1 ring-line">
                  <p className="text-subtle">Buzzer</p>
                  <p className={clsx('mt-1 flex items-center gap-1.5 font-medium', l.buzzerAlerts.length ? 'text-red-300' : 'text-muted')}>
                    {l.buzzerAlerts.length ? <Bell className="size-4 animate-pulse-soft" /> : <BellOff className="size-4" />}
                    {l.buzzerAlerts.length ? l.buzzerAlerts.map((c) => CODE_LABEL[c] ?? c).join(', ') : 'silencioso'}
                  </p>
                </div>
              </div>
              {l.demoMode && l.demoMode !== 'off' && (
                <p className="rounded-lg bg-wine-800/40 px-3 py-2 text-xs text-wine-200">Modo demo ativo no firmware: <span className="font-mono">{l.demoMode}</span></p>
              )}
              <div>
                <p className="mb-2 text-[11px] uppercase tracking-wider text-subtle">Teste manual (o backend ressincroniza em ~20s)</p>
                <div className="grid grid-cols-2 gap-2">
                  <Button size="sm" onClick={() => send('ledOn')} disabled={offline}>LED on</Button>
                  <Button size="sm" onClick={() => send('ledOff')} disabled={offline}>LED off</Button>
                  <Button size="sm" onClick={() => send('alertTemp')} disabled={offline}>Alerta temp.</Button>
                  <Button size="sm" onClick={() => send('alertHum')} disabled={offline}>Alerta umid.</Button>
                  <Button size="sm" onClick={() => send('alertLux')} disabled={offline}>Alerta luz</Button>
                  <Button size="sm" variant="danger" onClick={() => send('alertStop', 'all')} disabled={offline}>Parar alertas</Button>
                </div>
                {command.error && <div className="mt-2"><ErrorBox error={command.error} /></div>}
              </div>
              {d.simulated && (
                <div className="border-t border-line pt-4">
                  <p className="mb-2 text-[11px] uppercase tracking-wider text-subtle">Dispositivo virtual · heartbeat</p>
                  <div className="flex gap-2">
                    <Button size="sm" icon={<Unplug className="size-3.5" />} onClick={() => connection.mutate({ id, paused: true })}>Simular queda</Button>
                    <Button size="sm" icon={<Plug className="size-3.5" />} onClick={() => connection.mutate({ id, paused: false })}>Restabelecer</Button>
                  </div>
                </div>
              )}
            </div>
          </Card>
        </div>

        {/* WineGuard Intelligence */}
        <IntelligencePanel data={intelligence.data} className="lg:col-span-12" />

        {/* Incidentes + eventos */}
        <Card className="lg:col-span-8">
          <CardHeader title="Incidentes do dispositivo" />
          <div className="space-y-2 p-3">
            {incidents.data?.content.length ? incidents.data.content.slice(0, 6).map((i) => <IncidentCard key={i.id} incident={i} />)
              : <EmptyState icon={<Cpu className="size-6" />} title="Nenhum incidente registrado" />}
          </div>
        </Card>
        <Card className="lg:col-span-4">
          <CardHeader title="Eventos" />
          <div className="max-h-[420px] overflow-y-auto"><EventFeed events={events.data ?? []} compact /></div>
        </Card>
      </div>
    </>
  )
}
