# Wine Score — especificação

Índice de qualidade do ambiente de armazenamento, de **0 a 100**, calculado no backend a cada ciclo de monitoramento (2s) para cada dispositivo online.

Implementação: [`WineScoreCalculator.java`](../backend/src/main/java/com/wineguard/rules/WineScoreCalculator.java) · Testes: [`WineScoreCalculatorTest.java`](../backend/src/test/java/com/wineguard/rules/WineScoreCalculatorTest.java)

## Fórmula

```
Score = 100 − P_temperatura − P_umidade − P_luminosidade − P_exposição − P_ocorrências
        (limitado a 0..100 e arredondado)
```

### 1. Variáveis (valor atual)

Dentro da faixa configurada existe uma **penalidade de conforto** quadrática pela distância ao centro da faixa — um ambiente na borda do limite é pior do que um no ponto ideal, mesmo sem alarme.

Fora da faixa a penalidade cresce **linearmente com o desvio**:

| Variável | Conforto máx. (dentro) | Peso fora da faixa | Teto |
|---|---|---|---|
| Temperatura | 6 × (dist/meia-faixa)² | 6 + 8 pts por °C | 40 |
| Umidade | 4 × (dist/meia-faixa)² | 4 + 2 pts por p.p. | 30 |
| Luminosidade | 4 × (valor/máximo)² | 4 + 1 pt por p.p. | 25 |

A temperatura pesa mais porque variações térmicas aceleram a oxidação e "cozinham" o vinho; a luz pesa menos por unidade, mas tem teto alto.

### 2. Tempo em condição inadequada

Soma dos minutos de incidentes ambientais nas últimas 24h (somente a parte dentro da janela):

```
P_exposição = min(15, minutos / 8)        → 2 horas fora da faixa = −15
```

### 3. Ocorrências recentes

Incidentes ambientais iniciados nas últimas 24h, ponderados pela **severidade máxima** e com **decaimento linear**:

```
P_ocorrências = min(15, Σ peso × (1 − idade_h / 24))
peso: ATENÇÃO 2 · CRÍTICO 4 · EMERGÊNCIA 6
```

Incidentes de conectividade (offline) **não** afetam o score — ele mede o ambiente, não a rede.

## Classificação

| Faixa | Classe |
|---|---|
| 90–100 | Excelente |
| 75–89 | Bom |
| 50–74 | Atenção |
| 0–49 | Crítico |

## Exemplos (limites padrão 10–18 °C, 50–80 %, luz ≤ 30 %)

| Situação | Score |
|---|---|
| 14 °C, 65 %, 2 % luz, sem histórico | 100 (Excelente) |
| 17,5 °C, 55 %, 25 % luz | ~91 (Excelente, perto do limite) |
| 21,5 °C (3,5 °C acima), resto ideal | ~66 (Atenção) |
| 26 °C, 30 %, 70 % luz | 0–20 (Crítico) |
| Ideal agora, mas 2h de incidente EMERGÊNCIA terminado há 1h | ~80 (Bom) |

## Score geral

Média dos scores dos dispositivos com dados (offline não entram). O detalhamento por componente é exibido na página de cada dispositivo para que o operador saiba **por que** o score caiu.
