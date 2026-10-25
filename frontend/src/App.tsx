import { Link, Route, Routes } from 'react-router-dom'
import { Layout } from './components/Layout'
import { EmptyState } from './components/ui'
import { DashboardPage } from './pages/DashboardPage'
import { DemoPage } from './pages/DemoPage'
import { DeviceDetailPage } from './pages/DeviceDetailPage'
import { DevicesPage } from './pages/DevicesPage'
import { HistoryPage } from './pages/HistoryPage'
import { IncidentsPage } from './pages/IncidentsPage'
import { SettingsPage } from './pages/SettingsPage'

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<DashboardPage />} />
        <Route path="devices" element={<DevicesPage />} />
        <Route path="devices/:id" element={<DeviceDetailPage />} />
        <Route path="incidents" element={<IncidentsPage />} />
        <Route path="history" element={<HistoryPage />} />
        <Route path="settings" element={<SettingsPage />} />
        <Route path="demo" element={<DemoPage />} />
        <Route path="*" element={<EmptyState title="Página não encontrada"><Link to="/" className="text-wine-300">Voltar ao início</Link></EmptyState>} />
      </Route>
    </Routes>
  )
}
