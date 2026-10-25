// =============================================================================
// Configuração usada na simulação Wokwi (inserida no sketch.ino pelo script
// firmware/tools/build-wokwi.mjs). Mesma estrutura do config.example.h.
// =============================================================================
#pragma once

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
