// =============================================================================
// Leitura dos sensores (DHT11/DHT22 + LDR) e offset do Modo Demo
// =============================================================================
#pragma once
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
