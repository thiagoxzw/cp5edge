# Manual de operação

## Rotina do operador

1. **Visão geral** — confira o Wine Score geral e o mapa. Áreas verdes estão normais; amarelas pedem atenção; vermelhas estão em incidente crítico; cinzas estão sem comunicação.
2. **Incidentes ativos** — cada anomalia aparece uma única vez, com valor atual, limite e duração ao vivo.
   - **Silenciar** reconhece o incidente: o buzzer para, o LED continua piscando até o ambiente normalizar.
   - Ao normalizar, o incidente é encerrado automaticamente, LED e buzzer desligam e o horário de normalização é registrado.
3. **Dispositivo** (clique no mapa ou no card) — leituras, gráfico, detalhamento do Wine Score, estado do LED/buzzer e último contato.
4. **Gráficos** — escolha dispositivo, variável e período; as linhas tracejadas mostram os limites atuais.

## Cadastrar um ESP32

1. **Dispositivos → Novo dispositivo**.
2. Identificador = `DEVICE_ID` do `config.h` do firmware (ex.: `wg005`).
3. Escolha a área e os sensores. Salve: o backend provisiona o dispositivo no IoT Agent (coluna FIWARE = "Provisionado").
4. Ligue o ESP32 — em até 5s ele aparece Online.

Desativar pausa o monitoramento (encerra incidentes abertos e desliga os atuadores). Excluir remove do IoT Agent e do Orion; o histórico do STH-Comet é preservado.

## Configurar triggers

**Configuração** → ajuste mínimo/máximo e *tempo para disparo* de cada variável, faixas de severidade, LED/buzzer e heartbeat → **Salvar**. Vale no próximo ciclo (≤ 2s), sem reiniciar nada.

Presets: **Adega (padrão)** para uso real; **Apresentação em sala** para o hands-on (a sala está a ~24 °C, o que dispararia alerta com limites de adega).

## Roteiro do hands-on (≈ 3 minutos)

Antes: FIWARE no ar, ESP32 ligado (ou Wokwi), dashboard aberto. Em **Configuração**, aplique o preset **Apresentação em sala** e salve.

| # | Ação | O que mostrar |
|---|---|---|
| 1 | Abra **Visão geral** | mapa verde, Wine Score ~95+, 4 dispositivos |
| 2 | Abra **Modo Demo**, escolha o ESP32 (Sensor Adega Premium), cenário **Temperatura**, 20s | explique: o backend envia `demo\|temp:+X` ao firmware |
| 3 | **Iniciar demonstração** | passos 1–2 marcados |
| 4 | Aguarde ~15s (tempo para disparo) | anomalia detectada → incidente criado → Wine Score caindo |
| 5 | Mostre o ESP32 | **LED azul piscando** + **bipe longo** (padrão temperatura) — passos 6–8 marcados pelo estado reportado pelo próprio ESP32 |
| 6 | Volte à Visão geral (opcional) | área vermelha no mapa, incidente com duração contando |
| 7 | Aguarde (ou **Normalizar agora**) | temperatura volta, incidente **Resolvido** com duração, LED e buzzer param, score recupera |
| 8 | **Central de incidentes** | registro com início, normalização, duração, severidade |

Extras rápidos:
- **Padrões sonoros**: no detalhe do ESP32 use "Alerta umid." e "Alerta luz" para mostrar os bipes duplo e triplo (o backend desliga sozinho em ~20s porque não há incidente).
- **Offline**: desligue o ESP32 (ou "Simular queda" em um virtual) → Instável em 20s, Offline em 60s com tempo offline e incidente de conectividade.
- **Gráficos**: mostre os dados vindos do STH-Comet (rodapé do gráfico indica a fonte).
- Após a apresentação: **Modo Demo → Limpar incidentes de demo**.

## Comandos manuais × alertas automáticos

- **Automáticos:** seguem os incidentes. Ao normalizar, o backend desliga o alerta daquela variável. Para silenciar um incidente ativo, use **Silenciar** na central de incidentes.
- **Manuais** (botões do detalhe do dispositivo): ficam ativos até **Parar alertas** / **LED off** ou por no máximo **10 minutos**. A sincronização automática não os desfaz, e o encerramento de um incidente automático não desliga um alerta manual da mesma variável.
- **Parar alertas** desliga os alertas manuais. Um alerta automático com incidente ativo volta a tocar em seguida; para ele, use **Silenciar**.
