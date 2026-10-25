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
#include "config.h"
#include "sensors.h"
#include "alerts.h"
#include "fiware.h"


#include "lcd.h"
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
