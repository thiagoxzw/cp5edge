// Tipos espelhando os DTOs do backend (com.wineguard.dto)

export type HealthStatus = 'ONLINE' | 'UNSTABLE' | 'ALERT' | 'OFFLINE' | 'INACTIVE'
export type Connectivity = 'ONLINE' | 'UNSTABLE' | 'OFFLINE' | 'NEVER_SEEN'
export type AlertLevel = 'NORMAL' | 'ATTENTION' | 'CRITICAL' | 'EMERGENCY'
export type Zone = 'PREMIUM_CELLAR' | 'RESERVE_CELLAR' | 'STOCK' | 'TASTING_ROOM'
export type SensorType = 'TEMPERATURE' | 'HUMIDITY' | 'LUMINOSITY'
export type DeviceStatus = 'ACTIVE' | 'INACTIVE'
export type ProvisioningStatus = 'PENDING' | 'PROVISIONED' | 'FAILED' | 'NOT_REQUIRED'
export type IncidentStatus = 'ACTIVE' | 'ACKNOWLEDGED' | 'RESOLVED'
export type IncidentType =
  | 'TEMPERATURE_HIGH'
  | 'TEMPERATURE_LOW'
  | 'HUMIDITY_HIGH'
  | 'HUMIDITY_LOW'
  | 'LUMINOSITY_HIGH'
  | 'DEVICE_OFFLINE'
export type VariableKey = 'temperature' | 'humidity' | 'luminosity'
export type Period = '1h' | '6h' | '24h' | '7d' | '30d'
export type EventLevel = 'INFO' | 'SUCCESS' | 'WARNING' | 'CRITICAL'
export type ZoneMapStatus = 'normal' | 'attention' | 'critical' | 'offline' | 'empty'

export interface ScoreComponent {
  key: string
  label: string
  penalty: number
  maxPenalty: number
  detail: string
}

export interface DeviceLive {
  health: HealthStatus
  connectivity: Connectivity
  environmentLevel: AlertLevel
  temperature: number | null
  humidity: number | null
  luminosity: number | null
  lastSeenAt: string | null
  secondsSinceLastSeen: number | null
  connectivitySince: string | null
  pending: string[]
  score: number | null
  scoreLabel: string | null
  scoreComponents: ScoreComponent[]
  ledActive: boolean | null
  buzzerAlerts: string[]
  demoMode: string | null
  uptime: number | null
  activeIncidents: number
}

export interface Device {
  id: number
  name: string
  deviceKey: string
  entityId: string
  zone: Zone
  zoneLabel: string
  description: string | null
  sensors: SensorType[]
  status: DeviceStatus
  simulated: boolean
  provisioning: ProvisioningStatus
  provisioningMessage: string | null
  createdAt: string
  updatedAt: string
  live: DeviceLive
}

export interface DeviceRequest {
  name: string
  deviceKey: string
  zone: Zone
  description: string
  sensors: SensorType[]
  status: DeviceStatus
  simulated: boolean
}

export interface Incident {
  id: number
  deviceId: number
  deviceName: string
  deviceKey: string
  zone: Zone | null
  zoneLabel: string | null
  type: IncidentType
  typeLabel: string
  variable: 'TEMPERATURE' | 'HUMIDITY' | 'LUMINOSITY' | null
  unit: string | null
  detectedValue: number | null
  peakValue: number | null
  lastValue: number | null
  limitValue: number | null
  severity: AlertLevel
  currentSeverity: AlertLevel
  status: IncidentStatus
  startedAt: string
  acknowledgedAt: string | null
  normalizedAt: string | null
  durationSeconds: number
  demo: boolean
}

export interface EventItem {
  id: number
  timestamp: string
  deviceId: number | null
  deviceName: string | null
  type: string
  level: EventLevel
  message: string
}

export interface ZoneDevice {
  id: number
  name: string
  health: HealthStatus
  environmentLevel: AlertLevel
  temperature: number | null
  humidity: number | null
  luminosity: number | null
  score: number | null
}

export interface ZoneStatus {
  zone: Zone
  label: string
  status: ZoneMapStatus
  devices: ZoneDevice[]
}

export interface DashboardSummary {
  generatedAt: string
  mode: 'fiware' | 'standalone'
  counters: {
    total: number
    online: number
    unstable: number
    alert: number
    offline: number
    inactive: number
    activeIncidents: number
    criticalIncidents: number
  }
  overallScore: number | null
  overallScoreLabel: string | null
  zones: ZoneStatus[]
  devices: Device[]
  activeIncidents: Incident[]
  recentEvents: EventItem[]
}

export interface HistoryResponse {
  deviceId: number
  deviceName: string
  variable: VariableKey
  label: string
  unit: string
  period: Period
  source: string
  resolution: string
  limits: { min: number | null; max: number | null }
  stats: { min: number | null; max: number | null; avg: number | null; count: number }
  points: { t: string; v: number }[]
}

export interface EnvironmentConfig {
  temperatureMin: number
  temperatureMax: number
  temperatureTriggerSeconds: number
  humidityMin: number
  humidityMax: number
  humidityTriggerSeconds: number
  luminosityMax: number
  luminosityTriggerSeconds: number
  temperatureCriticalDelta: number
  temperatureEmergencyDelta: number
  humidityCriticalDelta: number
  humidityEmergencyDelta: number
  luminosityCriticalDelta: number
  luminosityEmergencyDelta: number
  escalationMinutes: number
  clearSeconds: number
  buzzerEnabled: boolean
  ledEnabled: boolean
  unstableAfterSeconds: number
  offlineTimeoutSeconds: number
  updatedAt?: string | null
}

export type DemoScenario = 'TEMPERATURE' | 'HUMIDITY' | 'LUMINOSITY'
export type DemoPhase = 'IDLE' | 'BASELINE' | 'INJECTING' | 'HOLDING' | 'RECOVERING' | 'DONE' | 'ABORTED'

export interface DemoStep {
  number: number
  key: string
  label: string
  done: boolean
  at: string | null
  detail: string | null
}

export interface DemoStatus {
  running: boolean
  phase: DemoPhase
  deviceId: number | null
  deviceName: string | null
  scenario: DemoScenario | null
  variable: VariableKey | null
  unit: string | null
  startedAt: string | null
  holdSeconds: number
  baselineScore: number | null
  currentScore: number | null
  minScore: number | null
  currentValue: number | null
  limitValue: number | null
  injectedParam: string | null
  incidentId: number | null
  message: string | null
  steps: DemoStep[]
}

export interface SystemStatus {
  mode: 'fiware' | 'standalone'
  fiwareService: string
  fiwareServicePath: string
  entityType: string
  components: { name: string; url: string; ok: boolean; version: string | null; detail: string | null }[]
  lastCycleAt: string | null
  telemetryAvailable: boolean
  telemetryError: string | null
  provisioningReady: boolean
  provisioningError: string | null
  simulatorReady: boolean
}

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

/* ---------- WineGuard Intelligence (índice de risco explicável) ---------- */
export type RiskLevel = 'LOW' | 'MODERATE' | 'HIGH' | 'CRITICAL' | 'INDETERMINATE'
export type RiskDataStatus = 'OK' | 'STALE' | 'NO_DATA'

export interface RiskFactor {
  variable: string
  label: string
  value: number | null
  unit: string
  range: string
  status: 'NORMAL' | 'ACIMA' | 'ABAIXO' | 'SEM_LEITURA'
  deviation: number
  minutesOutOfRange: number
  risk: number
  explanation: string
}

export interface RiskReport {
  index: number | null
  level: RiskLevel
  dataStatus: RiskDataStatus
  lastReadingAt: string | null
  factors: RiskFactor[]
  recommendations: string[]
  summary: string
  disclaimer: string
}

export interface Intelligence {
  deviceId: number
  deviceKey: string
  deviceName: string
  zoneLabel: string
  simulated: boolean
  report: RiskReport
}
