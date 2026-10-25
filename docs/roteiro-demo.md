# Roteiro de demonstração (hands-on / vídeo)

Cerca de 8 minutos. Cada passo diz o que mostrar e o que deve acontecer.

## Antes de começar (10 min antes)

1. EC2 ligada; anote o IP público. Se mudou, atualize `.env` (backend), `firmware/WineGuard/config.h` (ESP32)
   e `wokwi/config.wokwi.h` (Wokwi), e reinicie o backend: `docker compose up -d backend`.
2. `docker compose ps`: `postgres`, `backend` e `frontend` em `Up`.
3. Dashboard em `http://localhost:5173` mostra Orion, IoT Agent e STH-Comet como OK na barra de status.
4. **Configuração** → preset **Apresentação em sala** → **Salvar** (faixa da sala, não da adega).
5. Incidentes antigos: na central, use **Silenciar** nos que não fazem parte da demonstração. Não apague dados.
6. ESP32 ligado; no Monitor Serial aparece `[MQTT] conectado` e uma publicação a cada 5 s. Use **só um**
   `wg001` por vez: o ESP32 físico **ou** o Wokwi, nunca os dois.

## Demonstração

| # | Mostrar | Esperado |
| --- | --- | --- |
| 1 | Visão geral | 4 ambientes online; `wg001` é o ESP32 real, `wg002`–`wg004` são virtuais (marcados) |
| 2 | Detalhe do `wg001` | temperatura, umidade e luz iguais ao Serial e ao LCD (`T:… U:…` / `L:… NORMAL`) |
| 3 | Lanterna do celular no LDR por ~15 s | luz acima do limite → incidente **Luminosidade alta**, LED azul piscando, bipe triplo, LCD `ALERTA` |
| 4 | WineGuard Intelligence (detalhe e ranking) | índice sobe, nível alto/crítico, fator "Luminosidade … acima do limite", recomendação de reduzir a luz |
| 5 | Retire a luz | após ~5 s normal: incidente **resolvido**, LED e buzzer param, LCD `NORMAL`, índice volta a 0 |
| 6 | Botão **Alerta temp.** no detalhe | bipe longo continua até **Parar alertas** (no máximo 10 min) |
| 7 | **Parar alertas** e **LED off** | buzzer e LED desligam |
| 8 | Gráficos (1h) | curva da luminosidade com o pico e as linhas de limite (dados do STH-Comet) |
| 9 | Modo Demo em um dispositivo virtual | ~1 min: anomalia, incidente, comando, normalização e Wine Score recuperado |

## Se algo falhar

| Sintoma | Ação |
| --- | --- |
| `wg001` offline | conferir Wi-Fi 2,4 GHz, IP da EC2 no `config.h`, porta 1883 no Security Group |
| Barra de status com Orion/IoT Agent em erro | rede filtrando o IP da EC2: usar o nome DNS `ec2-<ip>.compute-1.amazonaws.com` no `.env` |
| Modo Demo recusa | dispositivo precisa estar ONLINE e sem incidente aberto da mesma variável: escolha outro virtual |
| LCD aceso sem texto | ajustar o contraste no potenciômetro do módulo; depois trocar `LCD_ADDR` para `0x3F` |
