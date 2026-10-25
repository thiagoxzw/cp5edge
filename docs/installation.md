# Instalação

## Pré-requisitos

| Ferramenta | Versão | Para quê |
|---|---|---|
| Docker + Docker Compose | 24+ | FIWARE local, PostgreSQL e/ou stack completa |
| Java JDK | 21+ (testado com 25) | backend fora do Docker |
| Maven | 3.9+ | build do backend |
| Node.js | 20+ (testado com 24) | frontend |
| Arduino IDE 2 ou PlatformIO | — | gravar o ESP32 |

## Opção A — tudo em Docker (recomendado para a apresentação)

```bash
cp .env.example .env          # ajuste POSTGRES_PASSWORD
docker compose --profile fiware up -d --build
```

Abra **http://localhost:5173**. O backend provisiona sozinho o service group, os 4 dispositivos de exemplo e a subscription Orion → STH-Comet (veja a barra superior: "FIWARE conectado").

Grave o ESP32 apontando `MQTT_BROKER` para o IP da sua máquina na rede local (veja [esp32.md](esp32.md)).

## Opção B — FIWARE em VM remota (ex.: FIWARE Descomplicado na Azure)

1. No `.env`, troque as URLs do FIWARE pelo IP público da VM (exemplos comentados no `.env.example`), inclusive `MQTT_BROKER_URL` e `FIWARE_STH_NOTIFY_URL`.
2. Garanta portas abertas na VM: 1026, 4041, 8666, 1883.
3. Suba só a aplicação:

```bash
docker compose up -d --build
```

## Opção C — desenvolvimento local (sem Docker para a aplicação)

```bash
# 1) FIWARE + Postgres em Docker
docker compose --profile fiware up -d mongo-db orion mosquitto iot-agent sth-comet postgres

# 2) Backend (as URLs padrão já apontam para localhost)
cd backend
export DATABASE_PASSWORD=troque-esta-senha     # PowerShell: $env:DATABASE_PASSWORD="..."
mvn spring-boot:run

# 3) Frontend
cd ../frontend
npm install
npm run dev
```

## Opção D — sem FIWARE e sem PostgreSQL (teste rápido da interface)

```bash
cd backend
WINEGUARD_MODE=standalone SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
# PowerShell: $env:WINEGUARD_MODE="standalone"; $env:SPRING_PROFILES_ACTIVE="local"; mvn spring-boot:run
cd ../frontend && npm install && npm run dev
```

Neste modo só os dispositivos virtuais publicam; o ESP32 físico aparece como offline e o histórico é o produzido desde o boot (em memória). A barra superior indica "Modo standalone".

## Testes

```bash
cd backend && mvn test        # regras, Wine Score, UltraLight, parsing STH e fluxo completo (standalone)
cd frontend && npm run build  # type-check + build
```

## Endereços úteis

| Serviço | URL |
|---|---|
| Dashboard | http://localhost:5173 |
| API / Swagger | http://localhost:8080/swagger-ui.html |
| Orion | http://localhost:1026/version |
| IoT Agent | http://localhost:4041/iot/about |
| STH-Comet | http://localhost:8666/version |
