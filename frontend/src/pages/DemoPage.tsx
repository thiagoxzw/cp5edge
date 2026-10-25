import clsx from 'clsx'
import { Check, Droplets, Eraser, Loader2, Play, RotateCcw, Square, Sun, Thermometer } from 'lucide-react'
import { useState } from 'react'
import { useDemoActions, useDemoStatus, useDevices } from '../api/hooks'
import type { DemoScenario } from '../api/types'
import { PageHeader } from '../components/Layout'
import { Button, Card, CardHeader, ErrorBox, Field, inputClass, Spinner } from '../components/ui'
import { WineScoreGauge } from '../components/WineScoreGauge'
import { fmtTime, fmtValue } from '../lib/format'

const SCENARIOS: { value: DemoScenario; label: string; desc: string; icon: typeof Thermometer; sound: string }[] = [
  { value: 'TEMPERATURE', label: 'Temperatura', desc: 'Eleva a temperatura até a faixa crítica', icon: Thermometer, sound: 'bipe longo grave' },
  { value: 'HUMIDITY', label: 'Umidade', desc: 'Reduz a umidade abaixo do mínimo', icon: Droplets, sound: 'bipe duplo' },
  { value: 'LUMINOSITY', label: 'Luminosidade', desc: 'Simula luz excessiva na adega', icon: Sun, sound: 'bipe triplo agudo' },
]

const PHASE_LABEL: Record<string, string> = {
  IDLE: 'Pronto', BASELINE: 'Linha de base', INJECTING: 'Injetando anomalia', HOLDING: 'Anomalia ativa',
  RECOVERING: 'Normalizando', DONE: 'Concluída', ABORTED: 'Interrompida',
}

/** Modo Demo: roteiro guiado para o hands-on; cada passo é confirmado pelo estado real do sistema. */
export function DemoPage() {
  const { data: status, isLoading } = useDemoStatus()
  const { data: devices } = useDevices()
  const { start, recover, stop, clear } = useDemoActions()
  const [deviceId, setDeviceId] = useState<number | undefined>()
  const [scenario, setScenario] = useState<DemoScenario>('TEMPERATURE')
  const [hold, setHold] = useState(20)

  if (isLoading || !status) return <Spinner />
  const eligible = devices?.filter((d) => d.status === 'ACTIVE') ?? []
  // Preferência: ESP32 físico online > qualquer dispositivo online > primeiro da lista
  const online = eligible.filter((d) => d.live.health !== 'OFFLINE')
  const selected = deviceId ?? online.find((d) => !d.simulated)?.id ?? online[0]?.id ?? eligible[0]?.id
  const selectedDevice = eligible.find((d) => d.id === selected)
  const running = status.running
  const doneCount = status.steps.filter((s) => s.done).length
  const actionError = start.error ?? recover.error ?? stop.error ?? clear.error

  return (
    <>
      <PageHeader
        title="Modo Demo"
        subtitle="Roteiro automático para a apresentação: anomalia → incidente → LED + buzzer → normalização"
        actions={!running && (
          <Button variant="ghost" size="sm" icon={<Eraser className="size-4" />} loading={clear.isPending} onClick={() => clear.mutate()}>
            Limpar incidentes de demo
          </Button>
        )}
      />
      {actionError && <div className="mb-4"><ErrorBox error={actionError} /></div>}
      {clear.data && <p className="mb-4 text-xs text-muted">{clear.data.deleted} incidentes de demonstração removidos.</p>}

      <div className="grid gap-4 lg:grid-cols-12">
        {/* Configuração */}
        <Card className={clsx('lg:col-span-4', running && 'opacity-60')}>
          <CardHeader title="Preparar demonstração" />
          <div className="space-y-4 p-5">
            <Field label="Dispositivo" hint={selectedDevice?.simulated ? 'Virtual: alertas visíveis no dashboard' : 'ESP32 físico/Wokwi: LED e buzzer reais'}>
              <select className={inputClass} disabled={running} value={selected ?? ''} onChange={(e) => setDeviceId(Number(e.target.value))}>
                {eligible.map((d) => (
                  <option key={d.id} value={d.id}>{d.name} {d.simulated ? '(virtual)' : '(ESP32)'} · {d.live.health === 'OFFLINE' ? 'offline' : 'online'}</option>
                ))}
              </select>
            </Field>
            <div className="space-y-2">
              <span className="block text-xs font-medium text-muted">Cenário</span>
              {SCENARIOS.map((s) => (
                <button key={s.value} type="button" disabled={running} onClick={() => setScenario(s.value)}
                  className={clsx('flex w-full items-center gap-3 rounded-xl border p-3 text-left transition-colors',
                    scenario === s.value ? 'border-wine-500/60 bg-wine-700/25' : 'border-line-strong bg-surface-2 hover:border-line-strong/80')}>
                  <s.icon className="size-5 text-wine-300" />
                  <span>
                    <span className="block text-sm font-medium">{s.label}</span>
                    <span className="block text-[11px] text-muted">{s.desc} · {s.sound}</span>
                  </span>
                </button>
              ))}
            </div>
            <Field label={`Manter anomalia por ${hold}s após o alerta`}>
              <input type="range" min={5} max={90} step={5} value={hold} disabled={running} onChange={(e) => setHold(Number(e.target.value))} className="w-full accent-wine-500" />
            </Field>
            <Button variant="primary" className="w-full" icon={<Play className="size-4" />} disabled={running || !selected}
              loading={start.isPending} onClick={() => selected && start.mutate({ deviceId: selected, scenario, holdSeconds: hold })}>
              Iniciar demonstração
            </Button>
            <p className="text-[11px] leading-relaxed text-subtle">
              A anomalia é injetada pelo comando <span className="font-mono">demo</span> do firmware (offset gradual na leitura).
              Detecção, incidente, comandos e normalização usam o fluxo real FIWARE.
            </p>
          </div>
        </Card>

        {/* Painel ao vivo */}
        <Card className="lg:col-span-8">
          <CardHeader
            title={status.deviceName ? `${status.deviceName} · ${PHASE_LABEL[status.phase]}` : 'Aguardando início'}
            subtitle={status.message ?? undefined}
            action={running && (
              <div className="flex gap-2">
                {(status.phase === 'HOLDING' || status.phase === 'INJECTING') && (
                  <Button size="sm" icon={<RotateCcw className="size-3.5" />} loading={recover.isPending} onClick={() => recover.mutate()}>Normalizar agora</Button>
                )}
                <Button size="sm" variant="danger" icon={<Square className="size-3.5" />} loading={stop.isPending} onClick={() => stop.mutate()}>Encerrar</Button>
              </div>
            )}
          />
          <div className="grid gap-6 p-5 md:grid-cols-[auto_1fr]">
            <div className="flex flex-col items-center gap-4">
              <WineScoreGauge score={status.currentScore} size={150} />
              <div className="tabular grid w-full grid-cols-2 gap-2 text-center text-xs">
                <div className="rounded-lg bg-surface-2 p-2"><p className="text-subtle">inicial</p><p className="text-base font-semibold">{status.baselineScore ?? '—'}</p></div>
                <div className="rounded-lg bg-surface-2 p-2"><p className="text-subtle">mínimo</p><p className="text-base font-semibold">{status.minScore ?? '—'}</p></div>
              </div>
              {status.variable && (
                <div className="w-full rounded-xl bg-surface-2 p-3 text-center">
                  <p className="text-[11px] text-subtle">valor atual · limite</p>
                  <p className="tabular text-2xl font-semibold">{fmtValue(status.currentValue, status.unit ?? '')}</p>
                  <p className="tabular text-xs text-muted">limite {fmtValue(status.limitValue, status.unit ?? '')}</p>
                </div>
              )}
            </div>

            <div>
              <div className="mb-3 flex items-center justify-between text-xs text-muted">
                <span>Roteiro</span><span className="tabular">{doneCount}/11</span>
              </div>
              <div className="mb-4 h-1.5 overflow-hidden rounded-full bg-surface-3">
                <div className="h-full rounded-full bg-gradient-to-r from-wine-600 to-wine-400 transition-all duration-700" style={{ width: `${(doneCount / 11) * 100}%` }} />
              </div>
              <ol className="space-y-1.5">
                {status.steps.map((s, idx) => {
                  const current = running && !s.done && status.steps.slice(0, idx).every((p) => p.done)
                  return (
                    <li key={s.key} className={clsx('flex items-center gap-3 rounded-lg px-3 py-2 transition-colors',
                      s.done ? 'bg-emerald-400/[0.06]' : current ? 'bg-wine-700/25 ring-1 ring-wine-500/30' : '')}>
                      <span className={clsx('flex size-6 shrink-0 items-center justify-center rounded-full text-[11px] font-semibold',
                        s.done ? 'bg-emerald-400 text-emerald-950' : current ? 'bg-wine-500 text-white' : 'bg-surface-3 text-subtle')}>
                        {s.done ? <Check className="size-3.5" /> : current ? <Loader2 className="size-3.5 animate-spin" /> : s.number}
                      </span>
                      <span className={clsx('flex-1 text-sm', !s.done && !current && 'text-muted')}>
                        {s.label}
                        {s.detail && s.done && <span className="ml-2 text-xs text-subtle">{s.detail}</span>}
                      </span>
                      {s.at && <span className="tabular text-[11px] text-subtle">{fmtTime(s.at)}</span>}
                    </li>
                  )
                })}
              </ol>
            </div>
          </div>
        </Card>
      </div>
    </>
  )
}
