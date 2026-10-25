# WineGuard Intelligence — índice de risco de conservação

Índice **operacional e explicável** de 0 a 100 que indica quanto cada ambiente precisa de atenção agora,
com os fatores que pesaram e uma recomendação prática para cada um.

> Não é uma certificação científica da qualidade do vinho e não afirma que houve dano. Ele só traduz os
> limites configurados no projeto (tela **Configuração**) em uma prioridade de atenção.

## Como é calculado

O cálculo fica no backend ([`RiskIndexCalculator`](../backend/src/main/java/com/wineguard/rules/RiskIndexCalculator.java)),
alimentado por [`IntelligenceService`](../backend/src/main/java/com/wineguard/service/IntelligenceService.java)
com dados reais: a última leitura do dispositivo (vinda do Orion), o instante dessa leitura e desde quando
cada variável está fora da faixa (contagem do monitoramento e início dos incidentes abertos).

Para cada variável com leitura:

| Situação | Risco da variável |
| --- | --- |
| Dentro da faixa (limites inclusive) | 0 |
| Fora da faixa | `25 + 75 × (0,7 × severidade + 0,3 × duração)` |

- **severidade** = `min(1, desvio / desvio de emergência)` — desvio em °C ou p.p. além do limite.
- **duração** = `min(1, minutos fora da faixa / minutos de escalonamento)`.
- **Índice** = o maior risco entre as variáveis. O pior fator define a prioridade; não há peso arbitrário
  entre temperatura, umidade e luz.

Todos os parâmetros vêm da Configuração (nada é fixo no código além dos coeficientes 25 / 0,7 / 0,3):

| Parâmetro | Configuração | Padrão (adega) |
| --- | --- | --- |
| Faixa de temperatura | mínimo e máximo | 10–18 °C |
| Faixa de umidade | mínimo e máximo | 50–80 % |
| Luz máxima | máximo | 30 % |
| Desvio de emergência | temperatura / umidade / luz | 5 °C / 15 p.p. / 30 p.p. |
| Escalonamento | minutos | 10 min |
| Dados desatualizados | offline após | 60 s |

### Por que esses coeficientes

- **Piso de 25:** qualquer variável fora da faixa configurada já é, no mínimo, risco moderado — sair do
  limite que o próprio responsável definiu não pode aparecer como "baixo".
- **0,7 para severidade e 0,3 para duração:** o quanto o valor se afastou pesa mais que o tempo, mas uma
  condição que persiste até o tempo de escalonamento sobe uma faixa.

## Classificação

| Índice | Nível |
| --- | --- |
| 0–24 | Baixo |
| 25–49 | Moderado |
| 50–74 | Alto |
| 75–100 | Crítico |
| — | Indeterminado (sem leitura ou leitura mais antiga que o timeout de offline) |

Exemplos com a configuração padrão: +0,5 °C acima do máximo = 30 (moderado); +2 °C = 46 (moderado);
+2 °C por 10 min = 69 (alto); +5 °C = 78 (crítico); umidade 10 p.p. abaixo = 60 (alto).

## Ausência de dados

- **Nunca recebeu leitura** ou todos os sensores sem valor → `NO_DATA`, índice nulo, nível **Indeterminado**.
- **Última leitura mais antiga que o timeout de offline** → `STALE`, índice nulo, nível **Indeterminado**; os
  últimos valores continuam visíveis, mas não são avaliados.
- **Um sensor sem leitura** → aparece como "sem leitura", fica fora do índice e gera recomendação de
  verificar o sensor.

Ausência de dados nunca é tratada como risco baixo.

## Onde aparece

- **Visão geral:** ranking dos ambientes do maior para o menor risco.
- **Detalhe do dispositivo:** índice, nível, fatores com explicação e recomendações.
- **API:** `GET /api/intelligence` (todos os dispositivos ativos) e `GET /api/devices/{id}/intelligence`.

## Limitações

- Usa a condição atual e há quanto tempo ela persiste. **Não consulta o histórico do STH-Comet** e não faz
  previsão de tendência.
- A configuração é única para todos os ambientes: se a faixa for ajustada para a sala de aula, ambientes
  simulados como adega podem aparecer fora da faixa.
- A contagem de "desde quando" fica em memória; após reiniciar o backend ela recomeça do zero, exceto
  para variáveis com incidente aberto (o início do incidente fica no banco).

## Testes

[`RiskIndexCalculatorTest`](../backend/src/test/java/com/wineguard/rules/RiskIndexCalculatorTest.java): valores
normais, limites exatos, desvio gradual, persistência, condição crítica, umidade abaixo da faixa, pior fator,
ausência de dados, dados desatualizados, sensor sem leitura, formatação de durações e o preset de sala.
