# ESP32 — firmware e montagem

Código: [`firmware/WineGuard/`](../firmware/WineGuard) (Arduino framework, ESP32 core 2.x ou 3.x).

| Arquivo | Responsabilidade |
|---|---|
| `WineGuard.ino` | `setup()`/`loop()` não bloqueantes |
| `config.example.h` | modelo de configuração → copie para `config.h` |
| `sensors.h` | DHT11/DHT22, LDR (0–100 %), offset do Modo Demo |
| `alerts.h` | LED azul piscando + sequenciador de padrões do buzzer |
| `fiware.h` | Wi-Fi, MQTT, publicação UltraLight, interpretação dos comandos |

## Ligações

| Componente | Pino ESP32 | Observação |
|---|---|---|
| DHT11 / DHT22 — dados | GPIO 15 | VCC 3V3, GND; resistor pull-up 10 kΩ se o módulo não tiver |
| LDR (módulo) — AO | GPIO 34 | ADC1 (funciona com Wi-Fi ligado) |
| Buzzer (+) | GPIO 18 | buzzer passivo para padrões de frequência (`tone()`) |
| LED azul | GPIO 2 | LED onboard do DevKit V1 (no Wokwi: LED azul externo + 220 Ω) |

## Passo a passo

1. Arduino IDE → instale o pacote **esp32** (Espressif) e as bibliotecas **PubSubClient** (Nick O'Leary) e **DHT sensor library** (Adafruit) + **Adafruit Unified Sensor**.
2. Copie `config.example.h` para `config.h` e preencha Wi-Fi, `MQTT_BROKER` (IP da máquina/VM com o Mosquitto), `FIWARE_APIKEY` e `DEVICE_ID`.
3. **DHT11 físico**: mantenha `#define WG_DHT_TYPE DHT11`. Na simulação Wokwi é `DHT22` — é a única linha que muda.
4. Placa "ESP32 Dev Module", grave e abra o Serial Monitor (115200).
5. No dashboard, o dispositivo com o mesmo identificador (padrão `wg001`, "Sensor Adega Premium") fica **Online**.

## Calibração do LDR

Os módulos LDR comuns reduzem a tensão com mais luz. Leia `analogRead(34)` no escuro (tampando o sensor) e sob luz forte e coloque os valores em `LDR_RAW_DARK` e `LDR_RAW_BRIGHT`. O firmware envia luminosidade normalizada 0–100 %.

## Protocolo

```
publica  /TEF/wg001/attrs    t|13.4|h|66.0|l|4.0|a|none|k|off|d|off|u|1234   (a cada 5s)
assina   /TEF/wg001/cmd   e  /ul/TEF/wg001/cmd
recebe   wg001@alertTemp|           →  responde /TEF/wg001/cmdexe  wg001@alertTemp|OK
```

| Comando | Parâmetro | Efeito |
|---|---|---|
| `ledOn` / `ledOff` | — | LED azul pisca (4 Hz) / apaga |
| `alertTemp` | — | bipe longo grave (1 kHz, 700 ms a cada 2 s) |
| `alertHum` | — | bipe duplo médio (1,8 kHz) |
| `alertLux` | — | bipe triplo agudo (3 kHz) |
| `alertStop` | `temp` \| `hum` \| `lux` \| `all` | para um ou todos os padrões |
| `demo` | `temp:+8.5` \| `hum:-20` \| `lux:+40` \| `off` | Modo Demo: offset gradual (rampa) na leitura |

Com vários alertas simultâneos o buzzer alterna os padrões. O firmware reporta o estado real (`a` e `k`) em cada medição; o backend usa isso para reenviar comandos perdidos.

> Os limites (triggers) **não ficam no firmware**: o ESP32 só mede e executa comandos. Quem decide é o backend, com a configuração feita no frontend.

## Wokwi

Veja [`wokwi/`](../wokwi): `diagram.json` (ESP32 + DHT22 + fotoresistor + buzzer + LED azul), `libraries.txt` e `sketch.ino` **gerado** a partir do firmware real:

```bash
node firmware/tools/build-wokwi.mjs
```

1. Em wokwi.com crie um projeto ESP32, cole `sketch.ino`, `diagram.json` e `libraries.txt`.
2. Em `wokwi/config.wokwi.h` (antes de gerar) defina `MQTT_BROKER` com o **IP público** do FIWARE (o simulador acessa a internet pela rede `Wokwi-GUEST`; `localhost` não funciona). Com o Wokwi para VS Code + Private Gateway, use `host.wokwi.internal` para o Docker local.
3. Durante a simulação, clique no DHT22 para mudar temperatura/umidade e no fotoresistor para mudar o lux — o dashboard reage como no hardware físico.

## LCD 16x2 I2C

Fica **fora da protoboard** (na face do encapsulamento), ligado direto no ESP32:

| LCD | ESP32 |
| --- | --- |
| VCC | VIN (5 V) |
| GND | GND.2 |
| SDA | D21 |
| SCL | D22 |

- Biblioteca: **LiquidCrystal I2C** (já em `wokwi/libraries.txt`; na Arduino IDE, instale pelo gerenciador).
- Endereço: `LCD_ADDR` no `config.h` (padrão `0x27`; alguns módulos usam `0x3F`).
- Linha 1: `T:28.4 U:34%`. Linha 2: `L: 42% NORMAL`, `ALERTA` (buzzer ou LED acionados) ou `DEMO`.
- Atualiza a cada 1 s sem `delay` e sem alocação dinâmica: não atrasa os sensores nem o MQTT.

**Status:** validado no Wokwi. **Ainda não validado no hardware físico.** Para validar:

1. Grave o firmware com o LCD ligado e abra o Monitor Serial (115200).
2. Ao ligar, o LCD mostra `WineGuard` / `iniciando...`; depois as leituras, que devem bater com o serial e com o dashboard.
3. Tela acesa sem texto: gire o potenciômetro de contraste do módulo I2C. Ainda vazio: troque `LCD_ADDR` para `0x3F` e grave de novo.
4. Dispare **Alerta temp.** no dashboard: a linha 2 deve mudar para `ALERTA`; em **Parar alertas** / **LED off**, volta para `NORMAL`.
