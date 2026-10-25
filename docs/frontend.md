# Frontend (React 19 · Vite · TypeScript · Tailwind CSS 4 · Recharts · Lucide)

## Executar

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173 (proxy /api -> VITE_BACKEND_URL, padrão http://localhost:8080)
npm run build      # type-check + build de produção em dist/
```

| Variável | Uso |
|---|---|
| `VITE_BACKEND_URL` | destino do proxy `/api` no `npm run dev` |
| `VITE_API_URL` | (opcional) base absoluta da API no build, se o frontend não for servido pelo nginx do compose |

Em Docker, o nginx serve o build e encaminha `/api` para o backend (mesma origem, sem CORS).

## Estrutura

```
src/
├── api/          client.ts (fetch + erros RFC 7807), types.ts (DTOs), hooks.ts (React Query)
├── lib/          format.ts, status.ts (cores semânticas), useNow.ts
├── components/   Layout, SystemStatusBar, WineryMap, DeviceCard, IncidentCard,
│                 HistoryChart, WineScoreGauge, DeviceForm, ui.tsx (Card, Badge, Button, Modal…)
└── pages/        Dashboard, Devices, DeviceDetail, Incidents, History, Settings, Demo
```

## Páginas

| Rota | Conteúdo |
|---|---|
| `/` | Wine Score geral, online/alerta/offline, incidentes ativos, mapa da vinheria, cards dos dispositivos, últimos eventos |
| `/devices` | CRUD completo (criar, editar, visualizar, ativar/desativar, excluir, reprovisionar) |
| `/devices/:id` | leituras atuais, gráfico, breakdown do Wine Score, estado do LED/buzzer, comandos manuais, simulação de queda (virtual), incidentes e eventos |
| `/incidents` | central de incidentes com filtros (abertos/resolvidos, severidade, dispositivo) e paginação; "Silenciar" |
| `/history` | gráficos por dispositivo × variável × período (1h, 6h, 24h, 7d, 30d) com linhas de limite |
| `/settings` | Configuração do Ambiente: faixas, tempo para disparo, severidades, LED/buzzer, heartbeat, presets |
| `/demo` | Modo Demo com roteiro de 11 passos |

## Atualização em tempo real

Polling via React Query: dashboard/dispositivos 2,5s, incidentes 4s, demo 1s, histórico 10s (1h) / 60s. Durações de incidentes abertos contam no cliente a cada segundo.

## Design

Tema escuro com tons de vinho para marca/navegação; **cores de status são reservadas para estado** (verde normal, âmbar atenção, laranja crítico, vermelho emergência, cinza offline) — o mesmo código de cores no mapa, cards, badges e gráficos.
