// =============================================================================
// Comunicação com o FIWARE via IoT Agent UltraLight 2.0 sobre MQTT
//
//  Medições  ->  /<APIKEY>/<DEVICE_ID>/attrs     t|13.4|h|66.0|l|4|a|none|k|off|d|off|u|1234
//  Comandos  <-  /<APIKEY>/<DEVICE_ID>/cmd       wg001@ledOn|
//                /ul/<APIKEY>/<DEVICE_ID>/cmd    (formato de tópico das versões novas do IoT Agent)
//  Resposta  ->  /<APIKEY>/<DEVICE_ID>/cmdexe    wg001@ledOn|OK
// =============================================================================
#pragma once
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
