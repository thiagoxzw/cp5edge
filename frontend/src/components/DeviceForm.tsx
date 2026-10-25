import clsx from 'clsx'
import { useState } from 'react'
import { ApiError } from '../api/client'
import { useSaveDevice } from '../api/hooks'
import type { Device, DeviceRequest, SensorType, Zone } from '../api/types'
import { Button, ErrorBox, Field, inputClass, Toggle } from './ui'

export const ZONES: { value: Zone; label: string }[] = [
  { value: 'PREMIUM_CELLAR', label: 'Adega Premium' },
  { value: 'RESERVE_CELLAR', label: 'Adega Reserva' },
  { value: 'STOCK', label: 'Estoque' },
  { value: 'TASTING_ROOM', label: 'Sala de Degustação' },
]

const SENSORS: { value: SensorType; label: string; hint: string }[] = [
  { value: 'TEMPERATURE', label: 'Temperatura', hint: 'DHT11/DHT22' },
  { value: 'HUMIDITY', label: 'Umidade', hint: 'DHT11/DHT22' },
  { value: 'LUMINOSITY', label: 'Luminosidade', hint: 'LDR' },
]

/** Formulário de cadastro/edição. O identificador vira o device_id no IoT Agent e no tópico MQTT. */
export function DeviceForm({ device, onDone }: { device?: Device; onDone: () => void }) {
  const save = useSaveDevice()
  const [form, setForm] = useState<DeviceRequest>({
    name: device?.name ?? '',
    deviceKey: device?.deviceKey ?? '',
    zone: device?.zone ?? 'PREMIUM_CELLAR',
    description: device?.description ?? '',
    sensors: device?.sensors ?? ['TEMPERATURE', 'HUMIDITY', 'LUMINOSITY'],
    status: device?.status ?? 'ACTIVE',
    simulated: device?.simulated ?? false,
  })
  const errors = save.error instanceof ApiError ? save.error.errors ?? {} : {}
  const set = <K extends keyof DeviceRequest>(k: K, v: DeviceRequest[K]) => setForm((f) => ({ ...f, [k]: v }))
  const toggleSensor = (s: SensorType) =>
    set('sensors', form.sensors.includes(s) ? form.sensors.filter((x) => x !== s) : [...form.sensors, s])

  return (
    <form
      className="space-y-4"
      onSubmit={(e) => {
        e.preventDefault()
        save.mutate({ id: device?.id, body: form }, { onSuccess: onDone })
      }}
    >
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="Nome" error={errors.name}>
          <input className={inputClass} value={form.name} onChange={(e) => set('name', e.target.value)} placeholder="Sensor Adega Premium" required maxLength={80} />
        </Field>
        <Field label="Identificador (device_id)" error={errors.deviceKey} hint={device ? 'Não pode ser alterado (provisionado no FIWARE)' : 'Ex.: wg005 — o mesmo configurado no firmware'}>
          <input className={clsx(inputClass, 'font-mono')} value={form.deviceKey} onChange={(e) => set('deviceKey', e.target.value)}
            placeholder="wg005" required pattern="[a-zA-Z0-9_\-]{2,40}" disabled={!!device} />
        </Field>
      </div>

      <Field label="Local (área da vinheria)" error={errors.zone}>
        <select className={inputClass} value={form.zone} onChange={(e) => set('zone', e.target.value as Zone)}>
          {ZONES.map((z) => <option key={z.value} value={z.value}>{z.label}</option>)}
        </select>
      </Field>

      <Field label="Descrição" error={errors.description}>
        <textarea className={clsx(inputClass, 'h-20 resize-none py-2')} value={form.description} maxLength={500}
          onChange={(e) => set('description', e.target.value)} placeholder="Posição, observações de instalação…" />
      </Field>

      <div>
        <span className="mb-1.5 block text-xs font-medium text-muted">Sensores</span>
        <div className="grid grid-cols-3 gap-2">
          {SENSORS.map((s) => {
            const on = form.sensors.includes(s.value)
            return (
              <button type="button" key={s.value} onClick={() => toggleSensor(s.value)}
                className={clsx('rounded-lg border px-3 py-2 text-left transition-colors',
                  on ? 'border-wine-500/60 bg-wine-700/25' : 'border-line-strong bg-surface-2 text-muted')}>
                <span className="block text-xs font-medium">{s.label}</span>
                <span className="block text-[10px] text-subtle">{s.hint}</span>
              </button>
            )
          })}
        </div>
      </div>

      <div className="space-y-3 rounded-xl border border-line bg-surface-2/60 p-4">
        <Toggle checked={form.status === 'ACTIVE'} onChange={(v) => set('status', v ? 'ACTIVE' : 'INACTIVE')}
          label="Ativo" description="Dispositivos desativados não são monitorados nem geram alertas" />
        <Toggle checked={form.simulated} onChange={(v) => set('simulated', v)}
          label="Dispositivo virtual" description="Simulado pelo backend com o mesmo protocolo do ESP32 (para demonstrar várias áreas)" />
      </div>

      {save.error && !Object.keys(errors).length && <ErrorBox error={save.error} />}

      <div className="flex justify-end gap-2 pt-1">
        <Button type="button" variant="ghost" onClick={onDone}>Cancelar</Button>
        <Button type="submit" variant="primary" loading={save.isPending}>{device ? 'Salvar alterações' : 'Cadastrar e provisionar'}</Button>
      </div>
    </form>
  )
}
