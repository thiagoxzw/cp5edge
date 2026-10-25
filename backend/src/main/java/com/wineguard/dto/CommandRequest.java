package com.wineguard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Comando manual enviado pelo painel do dispositivo.
 *
 * @param command ledOn, ledOff, alertTemp, alertHum, alertLux, alertStop ou demo
 * @param param   parâmetro opcional (ex.: "temp" para alertStop, "temp:+9" para demo)
 */
public record CommandRequest(@NotBlank String command, @Size(max = 40) String param) {
}
