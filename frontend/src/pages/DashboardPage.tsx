import clsx from 'clsx'
import { Activity, CheckCircle2, Cpu, Map as MapIcon, ShieldAlert, Siren, Sparkles, WifiOff } from 'lucide-react'
import { Link } from 'react-router-dom'
import { useDashboard, useIntelligence } from '../api/hooks'
import { DeviceCard } from '../components/DeviceCard'
import { EventFeed } from '../components/EventFeed'
import { IncidentCard } from '../components/IncidentCard'
import { PageHeader } from '../components/Layout'
import { Badge, Card, CardHeader, EmptyState, ErrorBox, Spinner } from '../components/ui'
import { WineryMap } from '../components/WineryMap'
import { WineScoreGauge } from '../components/WineScoreGauge'
import { RISK, ZONE_STATUS } from '../lib/status'

function Stat({ label, value, icon, tone, hint }: { label: string; value: number; icon: React.ReactNode; tone: string; hint?: string }) {
  return (
    <Card className="p-4">
      <div className="flex items-center justify-between">
        <span className="text-xs font-medium text-muted">{label}</span>
        <span className={clsx('flex size-8 items-center justify-center rounded-lg', tone)}>{icon}</span>
      </div>
      <p className="tabular mt-3 text-3xl font-semibold tracking-tight">{value}</p>
      {hint && <p className="mt-1 text-[11px] text-subtle">{hint}</p>}
    </Card>
  )
}

export function DashboardPage() {
  const { data, isLoading, error } = useDashboard()
  const intelligence = useIntelligence()
  if (isLoading) return <Spinner />
  if (error || !data) return <ErrorBox error={error ?? 'Sem dados'} />

  const c = data.counters
  return (
    <>
      <PageHeader title="Visão geral" subtitle="Monitoramento em tempo real dos ambientes de armazenamento" />

      <div className="grid gap-4 lg:grid-cols-12">
        {/* Wine Score geral */}
        <Card className="flex items-center gap-5 p-5 lg:col-span-4">
          <WineScoreGauge score={data.overallScore} label={data.overallScoreLabel} size={128} />
          <div className="min-w-0">
            <p className="text-xs font-medium uppercase tracking-wider text-subtle">Wine Score geral</p>
            <p className="mt-1 text-xs leading-relaxed text-muted">Qualidade média dos ambientes (0–100), atualizada a cada leitura.</p>
            <p className="mt-2 text-[11px] text-subtle">90+ Excelente · 75+ Bom · 50+ Atenção</p>
          </div>
        </Card>

        <div className="grid grid-cols-2 gap-4 lg:col-span-8 lg:grid-cols-4">
          <Stat label="Online" value={c.online} icon={<Cpu className="size-4" />} tone="bg-emerald-400/10 text-emerald-300"
            hint={`${c.total - c.inactive} ativos${c.unstable ? ` · ${c.unstable} instável` : ''}`} />
          <Stat label="Em alerta" value={c.alert} icon={<Siren className="size-4" />} tone="bg-red-500/10 text-red-300" hint="anomalia ambiental ativa" />
          <Stat label="Offline" value={c.offline} icon={<WifiOff className="size-4" />} tone="bg-zinc-500/15 text-zinc-300" hint="sem heartbeat" />
          <Stat label="Incidentes ativos" value={c.activeIncidents} icon={<ShieldAlert className="size-4" />} tone="bg-wine-500/15 text-wine-300"
            hint={c.criticalIncidents ? `${c.criticalIncidents} críticos/emergência` : 'nenhum crítico'} />
        </div>

        {/* Mapa */}
        <Card className="lg:col-span-8">
          <CardHeader
            title="Mapa da vinheria"
            subtitle="Clique em uma área para abrir o dispositivo"
            icon={<MapIcon className="size-4" />}
            action={
              <div className="hidden flex-wrap gap-3 text-[11px] text-muted sm:flex">
                {(['normal', 'attention', 'critical', 'offline'] as const).map((s) => (
                  <span key={s} className="inline-flex items-center gap-1.5">
                    <span className="size-2 rounded-full" style={{ background: ZONE_STATUS[s].stroke }} /> {ZONE_STATUS[s].label}
                  </span>
                ))}
              </div>
            }
          />
          <div className="p-4"><WineryMap zones={data.zones} /></div>
        </Card>

        {/* Incidentes ativos */}
        <Card className="flex flex-col lg:col-span-4">
          <CardHeader title="Incidentes ativos" icon={<ShieldAlert className="size-4" />}
            action={<Link to="/incidents" className="text-xs text-wine-300 hover:text-wine-200">Central →</Link>} />
          <div className="flex-1 space-y-2 overflow-y-auto p-3 lg:max-h-[372px]">
            {data.activeIncidents.length === 0 ? (
              <EmptyState icon={<CheckCircle2 className="size-7 text-emerald-400" />} title="Tudo sob controle">
                Nenhuma anomalia ativa nos ambientes monitorados.
              </EmptyState>
            ) : (
              data.activeIncidents.map((i) => <IncidentCard key={i.id} incident={i} />)
            )}
          </div>
        </Card>

        {/* WineGuard Intelligence: ranking de risco */}
        <Card className="lg:col-span-12">
          <CardHeader title="WineGuard Intelligence" subtitle="Ambientes por risco de conservação (0–100), do maior para o menor"
            icon={<Sparkles className="size-4" />} />
          <div className="divide-y divide-white/5">
            {(intelligence.data ?? []).map((it) => (
              <Link key={it.deviceId} to={`/devices/${it.deviceId}`} className="flex items-center gap-4 px-4 py-3 hover:bg-white/[0.03]">
                <span className={clsx('w-12 text-right text-xl font-semibold tabular-nums', RISK[it.report.level].text)}>{it.report.index ?? '—'}</span>
                <div className="min-w-0 flex-1">
                  <div className="text-sm">
                    {it.zoneLabel} <span className="text-xs text-muted">· {it.deviceKey}{it.simulated ? ' · virtual' : ''}</span>
                  </div>
                  <div className="truncate text-xs text-muted">{it.report.summary}</div>
                </div>
                <Badge tone={RISK[it.report.level]} />
              </Link>
            ))}
          </div>
        </Card>

        {/* Dispositivos */}
        <div className="lg:col-span-8">
          <div className="mb-3 flex items-center justify-between">
            <h2 className="text-sm font-semibold">Dispositivos</h2>
            <Link to="/devices" className="text-xs text-wine-300 hover:text-wine-200">Gerenciar →</Link>
          </div>
          {data.devices.length === 0 ? (
            <Card><EmptyState title="Nenhum dispositivo cadastrado"><Link className="text-wine-300" to="/devices">Cadastre o primeiro ESP32</Link></EmptyState></Card>
          ) : (
            <div className="grid gap-3 sm:grid-cols-2">
              {data.devices.map((d) => <DeviceCard key={d.id} device={d} />)}
            </div>
          )}
        </div>

        {/* Eventos */}
        <Card className="lg:col-span-4">
          <CardHeader title="Últimos eventos" icon={<Activity className="size-4" />} />
          <div className="max-h-[520px] overflow-y-auto"><EventFeed events={data.recentEvents} compact /></div>
        </Card>
      </div>
    </>
  )
}
