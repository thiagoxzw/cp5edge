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
