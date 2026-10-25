import { Bell, Droplets, HeartPulse, Save, ShieldAlert, Sun, Thermometer } from 'lucide-react'
import { useEffect, useState } from 'react'
import { ApiError } from '../api/client'
import { useEnvironmentConfig, useSaveConfig } from '../api/hooks'
import type { EnvironmentConfig } from '../api/types'
import { PageHeader } from '../components/Layout'
import { Button, Card, CardHeader, ErrorBox, Field, inputClass, Spinner, Toggle } from '../components/ui'
import { fmtDateTime } from '../lib/format'

/**
 * Perfis prontos. "Apresentação" existe porque em sala de aula o DHT lê ~24 °C e o LDR vê a luz
 * ambiente: com os limites de adega o ESP32 físico ficaria em alerta permanente.
 */
const PRESETS: Record<string, { label: string; values: Partial<EnvironmentConfig> }> = {
  cellar: {
    label: 'Adega (padrão)',
    values: { temperatureMin: 10, temperatureMax: 18, humidityMin: 50, humidityMax: 80, luminosityMax: 30 },
  },
  classroom: {
    label: 'Apresentação em sala',
    values: { temperatureMin: 15, temperatureMax: 30, humidityMin: 30, humidityMax: 85, luminosityMax: 85 },
  },
}

type NumKey = { [K in keyof EnvironmentConfig]-?: EnvironmentConfig[K] extends number ? K : never }[keyof EnvironmentConfig]

export function SettingsPage() {
  const { data, isLoading, error } = useEnvironmentConfig()
  const save = useSaveConfig()
  const [form, setForm] = useState<EnvironmentConfig | null>(null)
  const [saved, setSaved] = useState(false)

  useEffect(() => { if (data) setForm(data) }, [data])

  if (isLoading || !form) return error ? <ErrorBox error={error} /> : <Spinner />
  const errors = save.error instanceof ApiError ? save.error.errors ?? {} : {}

  const num = (key: NumKey, label: string, opts: { step?: number; unit?: string; hint?: string } = {}) => (
    <Field label={label} hint={opts.hint} error={errors[key]}>
      <div className="relative">
        <input
          type="number"
          step={opts.step ?? 1}
          className={`${inputClass} tabular pr-10`}
          value={Number.isFinite(form[key]) ? form[key] : ''}
          onChange={(e) => { setSaved(false); setForm({ ...form, [key]: e.target.value === '' ? NaN : Number(e.target.value) }) }}
          required
        />
        {opts.unit && <span className="pointer-events-none absolute inset-y-0 right-3 flex items-center text-xs text-subtle">{opts.unit}</span>}
      </div>
    </Field>
  )
  // Regras entre campos (@AssertTrue no backend) chegam com chaves que não são campos do formulário
  const globalErrors = Object.entries(errors).filter(([k]) => !(k in form)).map(([, v]) => v)

  return (
    <form onSubmit={(e) => { e.preventDefault(); save.mutate(form, { onSuccess: () => setSaved(true) }) }}>
      <PageHeader
        title="Configuração do ambiente"
        subtitle={`Triggers persistidos no banco e aplicados no próximo ciclo · atualizado em ${fmtDateTime(form.updatedAt)}`}
        actions={
          <>
            {Object.entries(PRESETS).map(([k, p]) => (
              <Button key={k} type="button" size="sm" variant="ghost" onClick={() => { setSaved(false); setForm({ ...form, ...p.values }) }}>{p.label}</Button>
            ))}
            <Button type="submit" variant="primary" icon={<Save className="size-4" />} loading={save.isPending}>Salvar</Button>
          </>
        }
      />
      {saved && <p className="mb-4 rounded-xl border border-emerald-400/30 bg-emerald-400/10 px-4 py-2.5 text-sm text-emerald-200">Configuração salva. O monitoramento já está usando os novos valores.</p>}
      {save.error && (globalErrors.length > 0 || !Object.keys(errors).length) && <div className="mb-4"><ErrorBox error={globalErrors.length ? globalErrors.join(' · ') : save.error} /></div>}

      <div className="grid gap-4 lg:grid-cols-3">
        <Card>
          <CardHeader title="Temperatura" subtitle="Faixa ideal para envelhecimento: 10–18 °C" icon={<Thermometer className="size-4" />} />
          <div className="grid gap-4 p-5 sm:grid-cols-2">
            {num('temperatureMin', 'Mínimo', { step: 0.5, unit: '°C' })}
            {num('temperatureMax', 'Máximo', { step: 0.5, unit: '°C' })}
            <div className="sm:col-span-2">{num('temperatureTriggerSeconds', 'Tempo para disparo', { unit: 's', hint: 'Fora da faixa por este tempo antes de abrir incidente' })}</div>
          </div>
        </Card>
        <Card>
          <CardHeader title="Umidade" subtitle="Evita ressecamento de rolhas e mofo" icon={<Droplets className="size-4" />} />
          <div className="grid gap-4 p-5 sm:grid-cols-2">
            {num('humidityMin', 'Mínimo', { unit: '%' })}
            {num('humidityMax', 'Máximo', { unit: '%' })}
            <div className="sm:col-span-2">{num('humidityTriggerSeconds', 'Tempo para disparo', { unit: 's' })}</div>
          </div>
        </Card>
        <Card>
          <CardHeader title="Luminosidade" subtitle="Luz degrada o vinho (“gosto de luz”)" icon={<Sun className="size-4" />} />
          <div className="grid gap-4 p-5">
            {num('luminosityMax', 'Máximo', { unit: '%', hint: 'Leitura do LDR normalizada de 0 a 100%' })}
            {num('luminosityTriggerSeconds', 'Tempo para disparo', { unit: 's' })}
          </div>
        </Card>

        <Card className="lg:col-span-2">
          <CardHeader title="Níveis de alerta" subtitle="Severidade derivada do desvio além do limite" icon={<ShieldAlert className="size-4" />} />
          <div className="p-5">
            <div className="mb-4 flex flex-wrap gap-3 text-xs">
              <span className="rounded-full bg-emerald-400/10 px-2.5 py-1 text-emerald-300">🟢 Normal: dentro da faixa</span>
              <span className="rounded-full bg-amber-400/10 px-2.5 py-1 text-amber-300">🟡 Atenção: desvio &lt; crítico</span>
              <span className="rounded-full bg-orange-500/10 px-2.5 py-1 text-orange-300">🟠 Crítico: desvio ≥ crítico</span>
              <span className="rounded-full bg-red-500/10 px-2.5 py-1 text-red-300">🔴 Emergência: desvio ≥ emergência ou crítico prolongado</span>
            </div>
            <div className="grid gap-4 sm:grid-cols-3">
              {num('temperatureCriticalDelta', 'Temp. crítico (+/−)', { step: 0.5, unit: '°C' })}
              {num('humidityCriticalDelta', 'Umid. crítico (+/−)', { unit: 'pp' })}
              {num('luminosityCriticalDelta', 'Luz crítico (+)', { unit: 'pp' })}
              {num('temperatureEmergencyDelta', 'Temp. emergência', { step: 0.5, unit: '°C' })}
              {num('humidityEmergencyDelta', 'Umid. emergência', { unit: 'pp' })}
              {num('luminosityEmergencyDelta', 'Luz emergência', { unit: 'pp' })}
              {num('escalationMinutes', 'Escalonar crítico após', { unit: 'min' })}
              {num('clearSeconds', 'Normal por (encerrar)', { unit: 's', hint: 'Evita abrir/fechar repetidamente' })}
            </div>
          </div>
        </Card>

        <div className="space-y-4">
          <Card>
            <CardHeader title="Processo de alerta" subtitle="Atuadores acionados remotamente no ESP32" icon={<Bell className="size-4" />} />
            <div className="space-y-4 p-5">
              <Toggle checked={form.ledEnabled} onChange={(v) => { setSaved(false); setForm({ ...form, ledEnabled: v }) }}
                label="LED azul" description="Pisca enquanto houver anomalia ativa" />
              <Toggle checked={form.buzzerEnabled} onChange={(v) => { setSaved(false); setForm({ ...form, buzzerEnabled: v }) }}
                label="Buzzer" description="Padrão sonoro distinto por variável" />
              <ul className="space-y-1 rounded-lg bg-surface-2 p-3 text-[11px] text-muted">
                <li>🌡 Temperatura: bipe longo grave</li>
                <li>💧 Umidade: bipe duplo médio</li>
                <li>☀ Luminosidade: bipe triplo agudo</li>
              </ul>
            </div>
          </Card>
          <Card>
            <CardHeader title="Heartbeat" subtitle="ESP32 publica a cada 5s" icon={<HeartPulse className="size-4" />} />
            <div className="grid grid-cols-2 gap-4 p-5">
              {num('unstableAfterSeconds', 'Instável após', { unit: 's' })}
              {num('offlineTimeoutSeconds', 'Offline após', { unit: 's' })}
            </div>
          </Card>
        </div>
      </div>
    </form>
  )
}
