// =============================================================================
// Atuadores: LED azul piscando + buzzer com padrão distinto por variável.
// Totalmente não bloqueante (millis), para não atrasar MQTT e leituras.
// =============================================================================
#pragma once

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
