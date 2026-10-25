import clsx from 'clsx'
import { BarChart3, Cpu, LayoutDashboard, Menu, PlayCircle, ShieldAlert, SlidersHorizontal, Wine, X } from 'lucide-react'
import { useState } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import { useDashboard, useDemoStatus } from '../api/hooks'
import { SystemStatusBar } from './SystemStatusBar'

const NAV = [
  { to: '/', label: 'Visão geral', icon: LayoutDashboard, end: true },
  { to: '/devices', label: 'Dispositivos', icon: Cpu },
  { to: '/incidents', label: 'Incidentes', icon: ShieldAlert },
  { to: '/history', label: 'Gráficos', icon: BarChart3 },
  { to: '/settings', label: 'Configuração', icon: SlidersHorizontal },
  { to: '/demo', label: 'Modo Demo', icon: PlayCircle },
]

function Brand() {
  return (
    <div className="flex items-center gap-2.5 px-2">
      <span className="flex size-9 items-center justify-center rounded-xl bg-gradient-to-br from-wine-500 to-wine-800 shadow-lg shadow-wine-900/60 ring-1 ring-wine-400/30">
        <Wine className="size-5 text-wine-200" />
      </span>
      <div>
        <p className="text-[15px] font-semibold tracking-tight">WineGuard</p>
        <p className="text-[10px] uppercase tracking-[0.18em] text-subtle">Cellar Intelligence</p>
      </div>
    </div>
  )
}

function Nav({ onNavigate }: { onNavigate?: () => void }) {
  const { data } = useDashboard()
  const { data: demo } = useDemoStatus()
  const incidents = data?.counters.activeIncidents ?? 0
  return (
    <nav className="mt-8 space-y-1">
      {NAV.map(({ to, label, icon: Icon, end }) => (
        <NavLink
          key={to}
          to={to}
          end={end}
          onClick={onNavigate}
          className={({ isActive }) =>
            clsx(
              'flex items-center gap-3 rounded-lg px-3 py-2 text-sm transition-colors',
              isActive ? 'bg-wine-700/35 text-fg ring-1 ring-inset ring-wine-500/30' : 'text-muted hover:bg-surface-3 hover:text-fg',
            )
          }
        >
          <Icon className="size-4" />
          <span className="flex-1">{label}</span>
          {to === '/incidents' && incidents > 0 && (
            <span className="tabular rounded-full bg-red-500/90 px-1.5 text-[10px] font-semibold text-white">{incidents}</span>
          )}
          {to === '/demo' && demo?.running && <span className="size-2 animate-pulse-soft rounded-full bg-wine-400" />}
        </NavLink>
      ))}
    </nav>
  )
}

export function Layout() {
  const [open, setOpen] = useState(false)
  return (
    <div className="min-h-screen lg:pl-60">
      {/* Sidebar desktop */}
      <aside className="fixed inset-y-0 left-0 hidden w-60 flex-col border-r border-line bg-surface/70 p-4 backdrop-blur lg:flex">
        <Brand />
        <Nav />
        <p className="mt-auto px-2 text-[10px] leading-relaxed text-subtle">
          CP5 · Vinheria Full<br />ESP32 · FIWARE · STH-Comet
        </p>
      </aside>

      {/* Sidebar mobile */}
      {open && (
        <div className="fixed inset-0 z-40 bg-black/60 lg:hidden" onClick={() => setOpen(false)}>
          <aside className="h-full w-64 border-r border-line bg-surface p-4" onClick={(e) => e.stopPropagation()}>
            <div className="flex items-center justify-between">
              <Brand />
              <button onClick={() => setOpen(false)} className="rounded-md p-1 text-muted" aria-label="Fechar menu"><X className="size-5" /></button>
            </div>
            <Nav onNavigate={() => setOpen(false)} />
          </aside>
        </div>
      )}

      <header className="sticky top-0 z-30 flex items-center gap-3 border-b border-line bg-bg/80 px-4 py-2.5 backdrop-blur sm:px-6">
        <button className="rounded-md p-1.5 text-muted hover:bg-surface-3 lg:hidden" onClick={() => setOpen(true)} aria-label="Abrir menu">
          <Menu className="size-5" />
        </button>
        <SystemStatusBar />
      </header>

      <main className="mx-auto max-w-[1400px] px-4 py-6 sm:px-6">
        <Outlet />
      </main>
    </div>
  )
}

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: string; actions?: React.ReactNode }) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div>
        <h1 className="text-xl font-semibold tracking-tight sm:text-2xl">{title}</h1>
        {subtitle && <p className="mt-1 text-sm text-muted">{subtitle}</p>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
    </div>
  )
}
