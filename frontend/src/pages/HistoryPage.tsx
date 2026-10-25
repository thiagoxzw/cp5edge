import { useState } from 'react'
import { useDevices, useEnvironmentConfig } from '../api/hooks'
import type { Period, VariableKey } from '../api/types'
import { HistoryChart } from '../components/HistoryChart'
import { PageHeader } from '../components/Layout'
import { Card, CardHeader, Field, inputClass, Segmented } from '../components/ui'
import { VARIABLES } from '../lib/status'
import { PERIODS } from './DeviceDetailPage'

/** Gráficos históricos: dispositivo + período + variável, com linhas dos limites configurados. */
export function HistoryPage() {
  const { data: devices } = useDevices()
  const { data: config } = useEnvironmentConfig()
  const [deviceId, setDeviceId] = useState<number | undefined>()
  const [variable, setVariable] = useState<VariableKey>('temperature')
  const [period, setPeriod] = useState<Period>('1h')
  const selected = deviceId ?? devices?.[0]?.id

  return (
    <>
      <PageHeader title="Gráficos históricos" subtitle="Dados consumidos do STH-Comet (API :8666) — sem dados fictícios" />

      <Card className="mb-4 p-4">
        <div className="grid gap-4 md:grid-cols-[minmax(0,280px)_1fr_auto] md:items-end">
          <Field label="Dispositivo">
            <select className={inputClass} value={selected ?? ''} onChange={(e) => setDeviceId(Number(e.target.value))}>
              {devices?.map((d) => <option key={d.id} value={d.id}>{d.name} · {d.zoneLabel}</option>)}
            </select>
          </Field>
          <Field label="Variável">
            <Segmented value={variable} onChange={setVariable}
              options={(Object.keys(VARIABLES) as VariableKey[]).map((k) => ({ value: k, label: VARIABLES[k].label }))} />
          </Field>
          <Field label="Período">
            <Segmented value={period} onChange={setPeriod} options={PERIODS} />
          </Field>
        </div>
      </Card>

      <Card>
        <CardHeader
          title={`${VARIABLES[variable].label} · ${PERIODS.find((p) => p.value === period)?.label}`}
          subtitle="Linhas tracejadas: limites configurados em Configuração do Ambiente"
        />
        <div className="p-4"><HistoryChart deviceId={selected} variable={variable} period={period} height={380} /></div>
      </Card>

      {config && (
        <div className="mt-4 grid gap-3 text-xs text-muted sm:grid-cols-3">
          <Card className="p-4">Temperatura ideal <b className="text-fg">{config.temperatureMin}–{config.temperatureMax} °C</b></Card>
          <Card className="p-4">Umidade ideal <b className="text-fg">{config.humidityMin}–{config.humidityMax} %</b></Card>
          <Card className="p-4">Luminosidade máxima <b className="text-fg">{config.luminosityMax} %</b></Card>
        </div>
      )}
    </>
  )
}
