// =============================================================================
// WineGuard - configuração do firmware
// Copie este arquivo para "config.h" (ignorado pelo git) e preencha seus dados.
// =============================================================================
#pragma once

// ----- Wi-Fi -----------------------------------------------------------------
#define WIFI_SSID       "SUA_REDE_WIFI"
#define WIFI_PASSWORD   "SUA_SENHA_WIFI"

// ----- FIWARE: IoT Agent UltraLight 2.0 via MQTT ------------------------------
// IP/host do broker Mosquitto usado pelo IoT Agent (VM FIWARE ou docker local)
#define MQTT_BROKER     "192.168.0.10"
#define MQTT_PORT       1883
#define MQTT_USER       ""            // vazio = sem autenticação
#define MQTT_PASSWORD   ""
// Precisa ser IGUAL ao FIWARE_API_KEY do backend e ao device_id cadastrado no dashboard
#define FIWARE_APIKEY   "TEF"
#define DEVICE_ID       "wg001"

// ----- Sensor de temperatura/umidade -----------------------------------------
// Hardware físico: DHT11 | Simulação Wokwi: DHT22  (única linha a trocar)
#define WG_DHT_TYPE     DHT11

// ----- Pinos (ESP32 DevKit V1) -----------------------------------------------
#define PIN_DHT         15
#define PIN_LDR         34   // entrada analógica (ADC1)
#define PIN_BUZZER      18
#define PIN_LED         2    // LED azul onboard

// Endereço I2C do LCD 16x2 (0x27 é o mais comum; alguns módulos usam 0x3F)
#define LCD_ADDR        0x27

// ----- Calibração do LDR (leitura ADC 0..4095) -------------------------------
// Meça no escuro e sob luz forte. Em módulos LDR comuns a leitura CAI com a luz.
#define LDR_RAW_DARK    4095
#define LDR_RAW_BRIGHT  0

// ----- Tempos -----------------------------------------------------------------
#define SENSOR_INTERVAL_MS   2500   // DHT11 >= 1s, DHT22 >= 2s
#define PUBLISH_INTERVAL_MS  5000   // heartbeat: o backend marca "instável" após 20s sem dados
