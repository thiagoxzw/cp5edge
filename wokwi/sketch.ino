
/*
 * =============================================================================
 *  WineGuard — firmware ESP32 (CP5 Vinheria Full)
 * =============================================================================
 *  Responsabilidades (camada Hardware):
 *   - ler DHT11/DHT22 (temperatura e umidade) e LDR (luminosidade)
 *   - publicar as medições no FIWARE (IoT Agent UltraLight via MQTT) a cada 5s
 *   - executar os comandos remotos enviados pelo backend:
 *       ledOn / ledOff            -> LED azul piscando enquanto houver anomalia
 *       alertTemp / alertHum / alertLux -> padrão sonoro específico por variável
 *       alertStop|temp|hum|lux|all       -> para o(s) alerta(s)
 *       demo|temp:+9 ... demo|off        -> Modo Demo (offset gradual na leitura)
 *   - reportar o estado real dos atuadores (alertState / ledState) para que o
 *     backend possa reenviar comandos perdidos
 *
 *  NENHUM limite (trigger) fica no firmware: quem decide é o backend, com os
 *  valores configurados pelo frontend.
 *
 *  Bibliotecas: "PubSubClient" (Nick O'Leary) e "DHT sensor library" (Adafruit)
 *  Placa: ESP32 Dev Module (Arduino core 2.x ou 3.x)
 * =============================================================================
 */
// ---------- config.wokwi.h ----------
// =============================================================================
// Configuração usada na simulação Wokwi (inserida no sketch.ino pelo script
// firmware/tools/build-wokwi.mjs). Mesma estrutura do config.example.h.
// =============================================================================

// Rede virtual do Wokwi (sem senha)
#define WIFI_SSID       "Wokwi-GUEST"
#define WIFI_PASSWORD   ""

// IP PÚBLICO do broker MQTT do FIWARE (ex.: VM na nuvem com porta 1883 liberada).
// No Wokwi para VS Code com Private Gateway, use "host.wokwi.internal" para o Docker local.
#define MQTT_BROKER     "13.222.9.65"
#define MQTT_PORT       1883
#define MQTT_USER       ""
#define MQTT_PASSWORD   ""
#define FIWARE_APIKEY   "TEF"
#define DEVICE_ID       "wg001"

// Simulação usa DHT22 — no hardware físico troque para DHT11
#define WG_DHT_TYPE     DHT22

#define PIN_DHT         15
#define PIN_LDR         34
#define PIN_BUZZER      18
#define PIN_LED         2

// Endereço I2C do LCD 16x2 (0x27 é o mais comum; alguns módulos usam 0x3F)
#define LCD_ADDR        0x27

// Módulo fotorresistor do Wokwi: leitura cai com a luz (10 lux ~ 17%, 1000 lux ~ 83%)
#define LDR_RAW_DARK    4095
#define LDR_RAW_BRIGHT  0

#define SENSOR_INTERVAL_MS   2500
#define PUBLISH_INTERVAL_MS  5000

// ---------- sensors.h ----------
// =============================================================================
// Leitura dos sensores (DHT11/DHT22 + LDR) e offset do Modo Demo
// =============================================================================

#include <DHT.h>

struct Readings {
  float temperature = NAN;   // °C
  float humidity = NAN;      // %
  float luminosity = NAN;    // 0..100 %
};

DHT dht(PIN_DHT, WG_DHT_TYPE);
Readings readings;

// ----- Modo Demo: offset aplicado gradualmente (comando "demo|temp:+9") -------
struct DemoOffset {
  float target = 0;
  float applied = 0;
};
DemoOffset demoTemp, demoHum, demoLux;

static void rampOffset(DemoOffset &o) {
  float diff = o.target - o.applied;
  float step = fabs(diff) / 3.0f;
  if (step < 0.5f) step = 0.5f;
  if (fabs(diff) <= step) o.applied = o.target;
  else o.applied += (diff > 0 ? step : -step);
}

bool demoActive() {
  return demoTemp.target != 0 || demoHum.target != 0 || demoLux.target != 0;
}

/** Texto reportado no atributo demoMode ("off" ou "temp:+9"). */
String demoText() {
  if (!demoActive()) return "off";
  String s;
  auto add = [&s](const char *code, float v) {
    if (v == 0) return;
    if (s.length()) s += ",";
    s += code;
    s += ":";
    if (v > 0) s += "+";
    s += String((int)v);
  };
  add("temp", demoTemp.target);
  add("hum", demoHum.target);
  add("lux", demoLux.target);
  return s;
}

/** Aplica o parâmetro do comando demo. Retorna false se inválido. */
bool applyDemo(const String &param) {
  if (param.length() == 0 || param == "off") {
    demoTemp.target = demoHum.target = demoLux.target = 0;
    return true;
  }
  int sep = param.indexOf(':');
  if (sep < 0) return false;
  String code = param.substring(0, sep);
  float value = param.substring(sep + 1).toFloat();
  if (code == "temp") demoTemp.target = value;
  else if (code == "hum") demoHum.target = value;
  else if (code == "lux") demoLux.target = value;
  else return false;
  return true;
}

void setupSensors() {
  dht.begin();
  analogReadResolution(12);
  pinMode(PIN_LDR, INPUT);
}

/** Converte a leitura do LDR em 0..100 % usando a calibração do config.h. */
static float readLuminosityPercent() {
  long sum = 0;
  for (int i = 0; i < 8; i++) sum += analogRead(PIN_LDR);   // média simples contra ruído
  float raw = sum / 8.0f;
  float pct = (raw - LDR_RAW_DARK) * 100.0f / (float)(LDR_RAW_BRIGHT - LDR_RAW_DARK);
  return constrain(pct, 0.0f, 100.0f);
}

void readSensors() {
  float t = dht.readTemperature();
  float h = dht.readHumidity();
  // DHT retorna NaN em falha de leitura: mantemos o último valor válido
  if (!isnan(t)) readings.temperature = t;
  if (!isnan(h)) readings.humidity = h;
  if (isnan(t) || isnan(h)) Serial.println(F("[DHT] falha de leitura (verifique o pino e o tipo DHT11/DHT22)"));
  readings.luminosity = readLuminosityPercent();

  rampOffset(demoTemp);
  rampOffset(demoHum);
  rampOffset(demoLux);
}

float reportedTemperature() { return isnan(readings.temperature) ? NAN : readings.temperature + demoTemp.applied; }
float reportedHumidity() { return isnan(readings.humidity) ? NAN : constrain(readings.humidity + demoHum.applied, 0.0f, 100.0f); }
float reportedLuminosity() { return constrain(readings.luminosity + demoLux.applied, 0.0f, 100.0f); }

// ---------- alerts.h ----------
// =============================================================================
// Atuadores: LED azul piscando + buzzer com padrão distinto por variável.
// Totalmente não bloqueante (millis), para não atrasar MQTT e leituras.
// =============================================================================

enum AlertBit : uint8_t { ALERT_TEMP = 1, ALERT_HUM = 2, ALERT_LUX = 4 };

struct Tone { uint16_t freq; uint16_t ms; };   // freq 0 = silêncio

// Temperatura: bipe longo e grave
const Tone PATTERN_TEMP[] = {{1000, 700}, {0, 1300}};
// Umidade: bipe duplo médio
const Tone PATTERN_HUM[] = {{1800, 150}, {0, 150}, {1800, 150}, {0, 1550}};
// Luminosidade: bipe triplo agudo
const Tone PATTERN_LUX[] = {{3000, 80}, {0, 80}, {3000, 80}, {0, 80}, {3000, 80}, {0, 1600}};

uint8_t activeAlerts = 0;    // bits ALERT_*
bool ledBlinking = false;

// --- estado interno do sequenciador ---
static uint8_t currentAlert = 0;
static uint8_t stepIndex = 0;
static unsigned long stepStartedAt = 0;
static unsigned long ledToggledAt = 0;
static bool ledOn = false;

// Lambda (e não função) de propósito: a IDE do Arduino cria protótipos das funções
// no topo do .ino, antes de 'Tone' existir, e no sketch único do Wokwi isso
// quebra a compilação ("'Tone' does not name a type").
static const auto patternFor = [](uint8_t bit, uint8_t &len) -> const Tone * {
  switch (bit) {
    case ALERT_TEMP: len = sizeof(PATTERN_TEMP) / sizeof(Tone); return PATTERN_TEMP;
    case ALERT_HUM:  len = sizeof(PATTERN_HUM) / sizeof(Tone);  return PATTERN_HUM;
    default:         len = sizeof(PATTERN_LUX) / sizeof(Tone);  return PATTERN_LUX;
  }
};

/** Próximo alerta ativo (em rodízio) quando há mais de uma variável fora da faixa. */
static uint8_t nextAlert(uint8_t after) {
  const uint8_t order[] = {ALERT_TEMP, ALERT_HUM, ALERT_LUX};
  int start = 0;
  for (int i = 0; i < 3; i++) if (order[i] == after) start = i + 1;
  for (int k = 0; k < 3; k++) {
    uint8_t b = order[(start + k) % 3];
    if (activeAlerts & b) return b;
  }
  return 0;
}

void setupAlerts() {
  pinMode(PIN_LED, OUTPUT);
  pinMode(PIN_BUZZER, OUTPUT);
  digitalWrite(PIN_LED, LOW);
  noTone(PIN_BUZZER);
}

String alertStateText() {
  if (!activeAlerts) return "none";
  String s;
  if (activeAlerts & ALERT_TEMP) s += "temp";
  if (activeAlerts & ALERT_HUM) { if (s.length()) s += ","; s += "hum"; }
  if (activeAlerts & ALERT_LUX) { if (s.length()) s += ","; s += "lux"; }
  return s;
}

void updateAlerts() {
  unsigned long now = millis();

  // LED azul: pisca 4 Hz enquanto houver anomalia
  if (ledBlinking) {
    if (now - ledToggledAt >= 250) {
      ledOn = !ledOn;
      digitalWrite(PIN_LED, ledOn ? HIGH : LOW);
      ledToggledAt = now;
    }
  } else if (ledOn) {
    ledOn = false;
    digitalWrite(PIN_LED, LOW);
  }

  // Buzzer
  if (!activeAlerts) {
    if (currentAlert) { noTone(PIN_BUZZER); currentAlert = 0; }
    return;
  }
  if (!(activeAlerts & currentAlert)) {          // começou agora ou o alerta atual foi parado
    currentAlert = nextAlert(currentAlert);
    stepIndex = 0;
    stepStartedAt = 0;
  }
  uint8_t len;
  const Tone *p = patternFor(currentAlert, len);
  if (stepStartedAt == 0 || now - stepStartedAt >= p[stepIndex].ms) {
    if (stepStartedAt != 0) {
      stepIndex++;
      if (stepIndex >= len) {                      // fim do padrão: próximo alerta ativo
        currentAlert = nextAlert(currentAlert);
        stepIndex = 0;
        p = patternFor(currentAlert, len);
      }
    }
    stepStartedAt = now;
    if (p[stepIndex].freq) tone(PIN_BUZZER, p[stepIndex].freq);
    else noTone(PIN_BUZZER);
  }
}

// ---------- fiware.h ----------
// =============================================================================
// Comunicação com o FIWARE via IoT Agent UltraLight 2.0 sobre MQTT
//
//  Medições  ->  /<APIKEY>/<DEVICE_ID>/attrs     t|13.4|h|66.0|l|4|a|none|k|off|d|off|u|1234
//  Comandos  <-  /<APIKEY>/<DEVICE_ID>/cmd       wg001@ledOn|
//                /ul/<APIKEY>/<DEVICE_ID>/cmd    (formato de tópico das versões novas do IoT Agent)
//  Resposta  ->  /<APIKEY>/<DEVICE_ID>/cmdexe    wg001@ledOn|OK
// =============================================================================

#include <WiFi.h>
#include <PubSubClient.h>

WiFiClient wifiClient;
PubSubClient mqtt(wifiClient);

const String TOPIC_ATTRS = String("/") + FIWARE_APIKEY + "/" + DEVICE_ID + "/attrs";
const String TOPIC_CMD = String("/") + FIWARE_APIKEY + "/" + DEVICE_ID + "/cmd";
const String TOPIC_CMD_UL = String("/ul/") + FIWARE_APIKEY + "/" + DEVICE_ID + "/cmd";
const String TOPIC_CMDEXE = String("/") + FIWARE_APIKEY + "/" + DEVICE_ID + "/cmdexe";

static unsigned long lastWifiAttempt = 0;
static unsigned long lastMqttAttempt = 0;

// ----- Interpretação dos comandos enviados pelo backend ----------------------
static String executeCommand(const String &cmd, const String &param) {
  if (cmd == "ledOn") { ledBlinking = true; return "OK"; }
  if (cmd == "ledOff") { ledBlinking = false; return "OK"; }
  if (cmd == "alertTemp") { activeAlerts |= ALERT_TEMP; return "OK"; }
  if (cmd == "alertHum") { activeAlerts |= ALERT_HUM; return "OK"; }
  if (cmd == "alertLux") { activeAlerts |= ALERT_LUX; return "OK"; }
  if (cmd == "alertStop") {
    if (param == "temp") activeAlerts &= ~ALERT_TEMP;
    else if (param == "hum") activeAlerts &= ~ALERT_HUM;
    else if (param == "lux") activeAlerts &= ~ALERT_LUX;
    else activeAlerts = 0;                        // "all" ou vazio
    return "OK";
  }
  if (cmd == "demo") return applyDemo(param) ? "OK" : "ERROR invalid param";
  return "ERROR unknown command";
}

static void onMqttMessage(char *topic, byte *payload, unsigned int length) {
  String msg;
  msg.reserve(length);
  for (unsigned int i = 0; i < length; i++) msg += (char)payload[i];
  msg.trim();
  Serial.printf("[MQTT] <- %s : %s\n", topic, msg.c_str());

  // Formato UltraLight: <device>@<comando>|<parâmetro>
  int at = msg.indexOf('@');
  if (at < 0) return;
  String device = msg.substring(0, at);
  if (device != DEVICE_ID) return;
  String rest = msg.substring(at + 1);
  int bar = rest.indexOf('|');
  String cmd = bar >= 0 ? rest.substring(0, bar) : rest;
  String param = bar >= 0 ? rest.substring(bar + 1) : "";
  param.trim();

  String result = executeCommand(cmd, param);
  String ack = device + "@" + cmd + "|" + result;
  mqtt.publish(TOPIC_CMDEXE.c_str(), ack.c_str());
  Serial.printf("[MQTT] -> %s : %s\n", TOPIC_CMDEXE.c_str(), ack.c_str());
}

void setupNetwork() {
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  mqtt.setServer(MQTT_BROKER, MQTT_PORT);
  mqtt.setCallback(onMqttMessage);
  mqtt.setBufferSize(512);
  // 60 s de keepalive: o Wokwi roda mais devagar que o tempo real e, com o padrão de 15 s,
  // o broker derrubava a conexão logo após conectar.
  mqtt.setKeepAlive(60);
}

/** Mantém Wi-Fi e MQTT conectados sem bloquear o loop (tentativas espaçadas). */
void maintainNetwork() {
  unsigned long now = millis();
  if (WiFi.status() != WL_CONNECTED) {
    if (now - lastWifiAttempt > 10000) {
      Serial.println(F("[WiFi] reconectando..."));
      WiFi.disconnect();
      WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
      lastWifiAttempt = now;
    }
    return;
  }
  if (!mqtt.connected()) {
    if (now - lastMqttAttempt > 5000) {
      lastMqttAttempt = now;
      // ID único por inicialização: o Wokwi usa o mesmo MAC em toda simulação, e dois clientes com o
      // mesmo ID se derrubam no broker (um conecta, o outro cai, em loop).
      static const String clientId = String("wineguard-") + DEVICE_ID + "-" + String((uint32_t)ESP.getEfuseMac(), HEX)
                                     + "-" + String(esp_random() & 0xFFFF, HEX);
      bool ok = strlen(MQTT_USER) > 0
                    ? mqtt.connect(clientId.c_str(), MQTT_USER, MQTT_PASSWORD)
                    : mqtt.connect(clientId.c_str());
      if (ok) {
        mqtt.subscribe(TOPIC_CMD.c_str());
        mqtt.subscribe(TOPIC_CMD_UL.c_str());
        Serial.printf("[MQTT] conectado em %s:%d, assinando %s\n", MQTT_BROKER, MQTT_PORT, TOPIC_CMD.c_str());
      } else {
        Serial.printf("[MQTT] falha (estado %d), nova tentativa em 5s\n", mqtt.state());
      }
    }
    return;
  }
  mqtt.loop();
}

/** Publica a medição UltraLight. Campos inválidos (NaN) são omitidos. */
void publishMeasurement() {
  if (!mqtt.connected()) return;
  String p;
  float t = reportedTemperature();
  float h = reportedHumidity();
  if (!isnan(t)) p += "t|" + String(t, 1) + "|";
  if (!isnan(h)) p += "h|" + String(h, 1) + "|";
  p += "l|" + String(reportedLuminosity(), 1);
  p += "|a|" + alertStateText();
  p += "|k|" + String(ledBlinking ? "on" : "off");
  p += "|d|" + demoText();
  p += "|u|" + String(millis() / 1000);
  mqtt.publish(TOPIC_ATTRS.c_str(), p.c_str());
  Serial.printf("[MQTT] -> %s : %s\n", TOPIC_ATTRS.c_str(), p.c_str());
}

// ---------- lcd.h ----------
// =============================================================================
// Display LCD 16x2 I2C, fora da protoboard (ligado direto no ESP32)
//   VCC -> VIN (5 V) | GND -> GND.2 | SDA -> D21 | SCL -> D22
//   Linha 1: T:28.4 U:34%     Linha 2: L: 42% NORMAL | ALERTA | DEMO
// Atualiza a cada 1 s, sem delay e sem alocação dinâmica (não atrasa sensores nem MQTT).
// =============================================================================

#include <Wire.h>
#include <LiquidCrystal_I2C.h>

#ifndef LCD_ADDR
#define LCD_ADDR 0x27          // defina no config.h; alguns módulos usam 0x3F
#endif
#define LCD_REFRESH_MS 1000

LiquidCrystal_I2C lcd(LCD_ADDR, 16, 2);
static unsigned long lastLcdUpdate = 0;

/** Escreve uma linha inteira (16 colunas), apagando o que sobrou do texto anterior. */
static void lcdPrintLine(uint8_t row, const char *text) {
  char buf[17];
  snprintf(buf, sizeof(buf), "%-16s", text);
  lcd.setCursor(0, row);
  lcd.print(buf);
}

void setupLcd() {
  Wire.begin(21, 22);          // SDA, SCL
  lcd.init();
  lcd.backlight();
  lcdPrintLine(0, "WineGuard");
  lcdPrintLine(1, "iniciando...");
}

void updateLcd() {
  unsigned long now = millis();
  if (now - lastLcdUpdate < LCD_REFRESH_MS) return;
  lastLcdUpdate = now;

  // Mesmos valores publicados no FIWARE (incluem o offset do Modo Demo, se ativo).
  float t = reportedTemperature();
  float h = reportedHumidity();
  float l = reportedLuminosity();

  char tBuf[8], hBuf[6], line[17];
  if (isnan(t)) strcpy(tBuf, "--"); else snprintf(tBuf, sizeof(tBuf), "%.1f", t);
  if (isnan(h)) strcpy(hBuf, "--"); else snprintf(hBuf, sizeof(hBuf), "%d", (int)lroundf(h));
  snprintf(line, sizeof(line), "T:%s U:%s%%", tBuf, hBuf);
  lcdPrintLine(0, line);

  // ALERTA = buzzer ou LED acionados (inclui incidente silenciado, em que só o LED pisca).
  const char *status = (activeAlerts || ledBlinking) ? "ALERTA" : (demoActive() ? "DEMO" : "NORMAL");
  snprintf(line, sizeof(line), "L:%3d%% %s", (int)lroundf(l), status);
  lcdPrintLine(1, line);
}

unsigned long lastSensorRead = 0;
unsigned long lastPublish = 0;

void setup() {
  Serial.begin(115200);
  delay(200);
  Serial.printf("\nWineGuard %s iniciando (DHT tipo %d)\n", DEVICE_ID, WG_DHT_TYPE);

  setupAlerts();
  setupLcd();
  setupSensors();
  setupNetwork();

  // Sinal de vida: duas piscadas rápidas
  for (int i = 0; i < 2; i++) {
    digitalWrite(PIN_LED, HIGH); delay(120);
    digitalWrite(PIN_LED, LOW);  delay(120);
  }
  readSensors();
}

void loop() {
  unsigned long now = millis();

  maintainNetwork();   // Wi-Fi + MQTT (não bloqueante)
  updateAlerts();      // LED + buzzer (não bloqueante)
  updateLcd();         // display LCD (1 s)

  if (now - lastSensorRead >= SENSOR_INTERVAL_MS) {
    lastSensorRead = now;
    readSensors();
  }
  if (now - lastPublish >= PUBLISH_INTERVAL_MS) {
    lastPublish = now;
    publishMeasurement();
  }
}
