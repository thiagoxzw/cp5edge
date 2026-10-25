import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from './client'
import type { Intelligence } from './types'
import type {
  DashboardSummary,
  DemoScenario,
  DemoStatus,
  Device,
  DeviceRequest,
  DeviceStatus,
  EnvironmentConfig,
  EventItem,
  HistoryResponse,
  Incident,
  Page,
  Period,
  SystemStatus,
  VariableKey,
} from './types'

/** Intervalos de atualização ("tempo real" por polling). */
const LIVE = 2500

export const useDashboard = () =>
  useQuery({ queryKey: ['dashboard'], queryFn: () => api.get<DashboardSummary>('/dashboard'), refetchInterval: LIVE })

export const useSystemStatus = () =>
  useQuery({ queryKey: ['system'], queryFn: () => api.get<SystemStatus>('/system/status'), refetchInterval: 10_000 })

export const useDevices = () =>
  useQuery({ queryKey: ['devices'], queryFn: () => api.get<Device[]>('/devices'), refetchInterval: LIVE })

export const useDevice = (id: number) =>
  useQuery({ queryKey: ['devices', id], queryFn: () => api.get<Device>(`/devices/${id}`), refetchInterval: LIVE })

export const useDeviceEvents = (id: number) =>
  useQuery({
    queryKey: ['devices', id, 'events'],
    queryFn: () => api.get<EventItem[]>(`/devices/${id}/events?limit=15`),
    refetchInterval: 5000,
  })

export const useHistory = (deviceId: number | undefined, variable: VariableKey, period: Period) =>
  useQuery({
    queryKey: ['history', deviceId, variable, period],
    queryFn: () => api.get<HistoryResponse>(`/devices/${deviceId}/history?variable=${variable}&period=${period}`),
    enabled: deviceId !== undefined,
    refetchInterval: period === '1h' ? 10_000 : 60_000,
    placeholderData: keepPreviousData,
    retry: 1,
  })

export interface IncidentFilters {
  status?: string
  deviceId?: number
  severity?: string
  page: number
}

export const useIncidents = (f: IncidentFilters) => {
  const params = new URLSearchParams({ page: String(f.page), size: '15' })
  if (f.status) params.set('status', f.status)
  if (f.deviceId) params.set('deviceId', String(f.deviceId))
  if (f.severity) params.set('severity', f.severity)
  return useQuery({
    queryKey: ['incidents', f],
    queryFn: () => api.get<Page<Incident>>(`/incidents?${params}`),
    refetchInterval: 4000,
    placeholderData: keepPreviousData,
  })
}

export const useEvents = (limit = 50) =>
  useQuery({ queryKey: ['events', limit], queryFn: () => api.get<EventItem[]>(`/events?limit=${limit}`), refetchInterval: 4000 })

export const useEnvironmentConfig = () =>
  useQuery({ queryKey: ['config'], queryFn: () => api.get<EnvironmentConfig>('/config/environment') })

export const useDemoStatus = () =>
  useQuery({ queryKey: ['demo'], queryFn: () => api.get<DemoStatus>('/demo'), refetchInterval: 1000 })

/** Após qualquer mutação, revalida tudo que é "ao vivo". */
function useInvalidateAll() {
  const qc = useQueryClient()
  return () => qc.invalidateQueries()
}

export function useSaveDevice() {
  const invalidate = useInvalidateAll()
  return useMutation({
    mutationFn: ({ id, body }: { id?: number; body: DeviceRequest }) =>
      id ? api.put<Device>(`/devices/${id}`, body) : api.post<Device>('/devices', body),
    onSuccess: invalidate,
  })
}

export function useDeviceStatus() {
  const invalidate = useInvalidateAll()
  return useMutation({
    mutationFn: ({ id, status }: { id: number; status: DeviceStatus }) =>
      api.patch<Device>(`/devices/${id}/status`, { status }),
    onSuccess: invalidate,
  })
}

export function useDeleteDevice() {
  const invalidate = useInvalidateAll()
  return useMutation({ mutationFn: (id: number) => api.del<void>(`/devices/${id}`), onSuccess: invalidate })
}

export function useProvisionDevice() {
  const invalidate = useInvalidateAll()
  return useMutation({ mutationFn: (id: number) => api.post<Device>(`/devices/${id}/provision`), onSuccess: invalidate })
}

export function useSendCommand() {
  const invalidate = useInvalidateAll()
  return useMutation({
    mutationFn: ({ id, command, param }: { id: number; command: string; param?: string }) =>
      api.post(`/devices/${id}/commands`, { command, param: param ?? '' }),
    onSuccess: invalidate,
  })
}

export function useSimulateConnection() {
  const invalidate = useInvalidateAll()
  return useMutation({
    mutationFn: ({ id, paused }: { id: number; paused: boolean }) =>
      api.post(`/devices/${id}/simulation/connection?paused=${paused}`),
    onSuccess: invalidate,
  })
}

export function useAcknowledgeIncident() {
  const invalidate = useInvalidateAll()
  return useMutation({ mutationFn: (id: number) => api.post<Incident>(`/incidents/${id}/acknowledge`), onSuccess: invalidate })
}

export function useSaveConfig() {
  const invalidate = useInvalidateAll()
  return useMutation({
    mutationFn: (cfg: EnvironmentConfig) => api.put<EnvironmentConfig>('/config/environment', cfg),
    onSuccess: invalidate,
  })
}

export function useDemoActions() {
  const invalidate = useInvalidateAll()
  const start = useMutation({
    mutationFn: (b: { deviceId: number; scenario: DemoScenario; holdSeconds: number }) => api.post<DemoStatus>('/demo/start', b),
    onSuccess: invalidate,
  })
  const recover = useMutation({ mutationFn: () => api.post<DemoStatus>('/demo/recover'), onSuccess: invalidate })
  const stop = useMutation({ mutationFn: () => api.post<DemoStatus>('/demo/stop'), onSuccess: invalidate })
  const clear = useMutation({ mutationFn: () => api.del<{ deleted: number }>('/demo/data'), onSuccess: invalidate })
  return { start, recover, stop, clear }
}

export function useRetryProvisioning() {
  const invalidate = useInvalidateAll()
  return useMutation({ mutationFn: () => api.post<SystemStatus>('/system/provisioning/retry'), onSuccess: invalidate })
}

/** WineGuard Intelligence: risco de conservação calculado no backend a partir dos dados reais. */
export const useDeviceIntelligence = (id: number) =>
  useQuery({
    queryKey: ['devices', id, 'intelligence'],
    queryFn: () => api.get<Intelligence>(`/devices/${id}/intelligence`),
    refetchInterval: 5000,
  })

export const useIntelligence = () =>
  useQuery({ queryKey: ['intelligence'], queryFn: () => api.get<Intelligence[]>('/intelligence'), refetchInterval: 5000 })
