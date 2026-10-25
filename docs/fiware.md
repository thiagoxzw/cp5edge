# Configuração FIWARE

O WineGuard usa os componentes padrão do FIWARE, nas mesmas APIs documentadas pelo projeto:

| Componente | Imagem | Porta | Uso |
|---|---|---|---|
| Orion Context Broker | `fiware/orion:3.10.1` | 1026 | estado atual (NGSI v2) e roteamento de comandos |
| IoT Agent UltraLight | `fiware/iotagent-ul:3.4.0` | 4041 (API), 7896 (HTTP) | traduz MQTT/UL ⇄ NGSI |
| Mosquitto | `eclipse-mosquitto:2.0.18` | 1883 | broker MQTT |
| STH-Comet | `fiware/sth-comet:2.10.0` | 8666 | séries históricas (raw + agregadas) |
| MongoDB | `mongo:4.4` | — | persistência do Orion, IoT Agent e STH |

## Variáveis

| Variável | Padrão | Descrição |
|---|---|---|
| `FIWARE_ORION_URL` | `http://localhost:1026` | Orion visto pelo backend |
| `FIWARE_IOTA_URL` | `http://localhost:4041` | API de provisionamento do IoT Agent |
| `FIWARE_STH_URL` | `http://localhost:8666` | API de histórico do STH-Comet |
| `FIWARE_SERVICE` / `FIWARE_SERVICE_PATH` | `smart` / `/` | headers `fiware-service`/`fiware-servicepath` — iguais em TODAS as chamadas |
| `FIWARE_API_KEY` | `TEF` | apikey do service group (= `FIWARE_APIKEY` do firmware) |
| `FIWARE_ENTITY_TYPE` | `WineSensor` | tipo NGSI das entidades |
| `FIWARE_IOTA_CBROKER_URL` | `http://orion:1026` | Orion visto **pelo IoT Agent** |
| `FIWARE_STH_NOTIFY_URL` | `http://sth-comet:8666/notify` | STH visto **pelo Orion** |
| `FIWARE_AUTO_PROVISION` | `true` | o backend cria service group, dispositivos e subscription |
| `MQTT_BROKER_URL` | `tcp://localhost:1883` | broker usado pelos dispositivos virtuais |

## O que o backend provisiona automaticamente

Com `FIWARE_AUTO_PROVISION=true` (repetido a cada 30s até ter sucesso; também pelo botão "Reprovisionar FIWARE"):

### 1. Service group

```http
POST {IOTA}/iot/services
fiware-service: smart
fiware-servicepath: /
Content-Type: application/json

{ "services": [ { "apikey": "TEF", "cbroker": "http://orion:1026", "entity_type": "WineSensor", "resource": "/iot/d" } ] }
```

### 2. Dispositivo (no cadastro pelo dashboard)

```http
POST {IOTA}/iot/devices
{
  "devices": [{
    "device_id": "wg001",
    "entity_name": "urn:ngsi-ld:WineSensor:wg001",
    "entity_type": "WineSensor",
    "protocol": "PDI-IoTA-UltraLight",
    "transport": "MQTT",
    "apikey": "TEF",
    "attributes": [
      { "object_id": "t", "name": "temperature", "type": "Number" },
      { "object_id": "h", "name": "humidity",    "type": "Number" },
      { "object_id": "l", "name": "luminosity",  "type": "Number" },
      { "object_id": "a", "name": "alertState",  "type": "Text" },
      { "object_id": "k", "name": "ledState",    "type": "Text" },
      { "object_id": "d", "name": "demoMode",    "type": "Text" },
      { "object_id": "u", "name": "uptime",      "type": "Number" }
    ],
    "commands": [
      { "name": "ledOn", "type": "command" },  { "name": "ledOff", "type": "command" },
      { "name": "alertTemp", "type": "command" }, { "name": "alertHum", "type": "command" },
      { "name": "alertLux", "type": "command" }, { "name": "alertStop", "type": "command" },
      { "name": "demo", "type": "command" }
    ],
    "static_attributes": [ { "name": "zone", "type": "Text", "value": "PREMIUM_CELLAR" } ]
  }]
}
```

### 3. Subscription Orion → STH-Comet

```http
POST {ORION}/v2/subscriptions
{
  "description": "WineGuard: Orion -> STH-Comet",
  "subject": { "entities": [ { "idPattern": ".*", "type": "WineSensor" } ] },
  "notification": {
    "http": { "url": "http://sth-comet:8666/notify" },
    "attrs": [ "temperature", "humidity", "luminosity" ],
    "attrsFormat": "legacy"
  }
}
```

> `attrsFormat: legacy` é obrigatório: o STH-Comet só entende notificações NGSIv1.

## Chamadas usadas em tempo de execução

| Finalidade | Requisição |
|---|---|
| Valores atuais (polling 2s) | `GET {ORION}/v2/entities?type=WineSensor&options=keyValues&attrs=temperature,humidity,luminosity,alertState,ledState,demoMode,uptime,TimeInstant,dateModified&limit=1000` |
| Comando | `PATCH {ORION}/v2/entities/urn:ngsi-ld:WineSensor:wg001/attrs?type=WineSensor` body `{"ledOn":{"type":"command","value":""}}` |
| Histórico (1h) | `GET {STH}/STH/v1/contextEntities/type/WineSensor/id/{id}/attributes/temperature?hLimit=100&hOffset=0&dateFrom=…&dateTo=…` (paginado) |
| Histórico (6h–30d) | `GET {STH}/STH/v1/contextEntities/type/WineSensor/id/{id}/attributes/temperature?aggrMethod=sum&aggrPeriod=minute|hour&dateFrom=…&dateTo=…` (média = sum/samples) |

Todas com `fiware-service: smart` e `fiware-servicepath: /`.

## Verificação manual

Arquivo pronto para o REST Client do VS Code / IntelliJ: [fiware-requests.http](fiware-requests.http).

```bash
# Entidades atuais
curl -s "http://localhost:1026/v2/entities?type=WineSensor&options=keyValues" -H "fiware-service: smart" -H "fiware-servicepath: /"

# Simular uma medição do wg001 via MQTT (sem ESP32)
docker compose exec mosquitto mosquitto_pub -t /TEF/wg001/attrs -m "t|13.5|h|66|l|5|a|none|k|off|d|off|u|1"

# Ver comandos chegando ao ESP32
docker compose exec mosquitto mosquitto_sub -v -t "/TEF/+/cmd" -t "/ul/TEF/+/cmd" -t "/TEF/+/cmdexe"

# Últimos 10 valores no STH
curl -s "http://localhost:8666/STH/v1/contextEntities/type/WineSensor/id/urn:ngsi-ld:WineSensor:wg001/attributes/temperature?lastN=10" -H "fiware-service: smart" -H "fiware-servicepath: /"
```

## Usando um FIWARE já existente (ex.: FIWARE Descomplicado)

- Mantenha `FIWARE_SERVICE=smart`, `FIWARE_SERVICE_PATH=/` e `FIWARE_API_KEY=TEF` (padrões do Descomplicado) ou ajuste os três lados.
- Se o service group `TEF` já existir, o backend recebe 409 e segue normalmente.
- `FIWARE_STH_NOTIFY_URL` deve ser uma URL que o **Orion** consiga acessar (na mesma VM: `http://sth-comet:8666/notify` ou o IP público).
