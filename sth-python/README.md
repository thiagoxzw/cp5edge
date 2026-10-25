# Histórico do STH-Comet em Python

Versão do notebook da disciplina (`fiware_sth-comet_demo.ipynb`, Colab do Prof. Fábio Cabrini) adaptada ao WineGuard.
Consulta no STH-Comet (porta 8666) as últimas leituras de **temperatura, umidade e luminosidade** de um dispositivo
e desenha um gráfico por variável, com a linha da média.

É uma forma independente do dashboard de conferir que os dados do ESP32/Wokwi chegaram ao histórico do FIWARE.

## Uso local

```bash
cd sth-python
pip install -r requirements.txt
python sth_dashboard.py --host http://<IP_DA_EC2>:8666 --lastN 30
python sth_dashboard.py --host http://<IP_DA_EC2>:8666 --save historico.png --no-show
```

| Opção | Padrão | Descrição |
|---|---|---|
| `--host` | `FIWARE_STH_URL` ou `http://localhost:8666` | URL do STH-Comet |
| `--entity` | `urn:ngsi-ld:WineSensor:wg001` | id da entidade no Orion |
| `--type` | `WineSensor` | tipo da entidade |
| `--lastN` | `30` | quantidade de leituras (1 a 100) |
| `--attr` | `all` | `temperature`, `humidity`, `luminosity` ou `all` |
| `--save` | — | salva o gráfico em .png |
| `--no-show` | — | não abre a janela do gráfico |

Cabeçalhos usados: `fiware-service: smart` e `fiware-servicepath: /` (os mesmos do provisionamento no IoT Agent).

## No Google Colab

Cole o conteúdo de `sth_dashboard.py` em uma célula e, em outra, rode:

```python
main(["--host", "http://<IP_DA_EC2>:8666", "--lastN", "30"])
```

A porta 8666 precisa estar liberada no Security Group da EC2 para o IP de quem consulta.
