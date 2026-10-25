# Troubleshooting

| Sintoma | Causa provável | Solução |
|---|---|---|
| Barra superior: "Backend indisponível" | backend parado ou porta diferente | suba o backend; no `npm run dev` ajuste `VITE_BACKEND_URL` |
| "FIWARE com problemas" | Orion/IoT Agent/STH inacessível | clique na barra para ver qual componente falhou e a URL usada; confira `FIWARE_*` e firewall da VM |
| Provisionamento "Falhou" | IoT Agent fora ou payload rejeitado | passe o mouse na coluna FIWARE para ver a mensagem; use "Reprovisionar" |
| ESP32 sempre **Offline** | MQTT não conecta, `DEVICE_ID`/`FIWARE_APIKEY` diferentes do backend, ou tenant diferente | Serial Monitor mostra `[MQTT] falha (estado N)`; `mosquitto_sub -v -t "/TEF/#"` deve mostrar as medições; confira `fiware-service` |
| ESP32 Online mas valores `—` | atributos chegam com nomes diferentes | o dispositivo precisa ser provisionado pelo WineGuard (object_ids `t,h,l,a,k,d,u`); se foi provisionado manualmente antes, exclua no IoT Agent e reprovisione |
| Comandos não chegam ao ESP32 | dispositivo não provisionado com `commands`, ou tópico | veja `/TEF/wg001/cmd` com `mosquitto_sub`; o firmware assina também `/ul/TEF/...` |
| LED/buzzer não param | comando `alertStop`/`ledOff` perdido | o backend reenvia após 20s de divergência (o firmware reporta `a`/`k`); confirme no detalhe do dispositivo |
| Gráfico "Sem dados no período" | subscription STH ausente ou `attrsFormat` errado | `GET /v2/subscriptions` deve ter "WineGuard: Orion -> STH-Comet" com `legacy`; "Reprovisionar FIWARE" recria |
| Gráfico de 7d/30d vazio mas 1h ok | agregação não numérica | garanta `IOTA_AUTOCAST=true`; o backend cai para dados raw automaticamente |
| `[DHT] falha de leitura` no Serial | tipo errado (DHT11 x DHT22) ou pino | ajuste `WG_DHT_TYPE` e `PIN_DHT` |
| Luminosidade invertida | módulo com lógica oposta | troque `LDR_RAW_DARK`/`LDR_RAW_BRIGHT` |
| ESP32 físico em alerta permanente na sala | limites de adega (10–18 °C) vs. sala (~24 °C) | preset **Apresentação em sala** |
| Demo: "O dispositivo precisa estar ONLINE" | heartbeat ausente | verifique o dispositivo; ou use um virtual |
| Demo não avança do passo 3 | "tempo para disparo" longo | reduza em Configuração ou aguarde |
| Wokwi não conecta ao MQTT | `localhost` no `MQTT_BROKER` | use IP público (ou `host.wokwi.internal` com Private Gateway) |
| `Port 8080 already in use` | outra aplicação na porta | `SERVER_PORT=8088` e `VITE_BACKEND_URL=http://localhost:8088` |
| Horários estranhos no "último contato" | relógio da VM FIWARE diferente | o backend usa o próprio relógio quando os dados mudam; se persistir, sincronize NTP na VM |
