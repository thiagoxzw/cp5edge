"""
WineGuard - historico do STH-Comet em Python (porta 8666)

Versao do exemplo da disciplina (fiware_sth-comet_demo.ipynb) adaptada ao WineGuard:
consulta o historico de temperatura, umidade e luminosidade de um dispositivo no STH-Comet
e desenha um grafico por variavel, com a media de cada uma.

Uso:
    pip install -r requirements.txt
    python sth_dashboard.py --host http://<IP_DA_EC2>:8666 --lastN 30
    python sth_dashboard.py --host http://<IP_DA_EC2>:8666 --save historico.png --no-show

No Google Colab: copie este arquivo para uma celula e rode main(["--host", "http://<IP_DA_EC2>:8666"]).
"""

import argparse
import os
import sys
from datetime import datetime, timedelta

import matplotlib.pyplot as plt
import requests

# Cabecalhos de multi-tenancy do FIWARE: precisam ser os mesmos usados no IoT Agent e no Orion.
FIWARE_HEADERS = {
    "fiware-service": "smart",
    "fiware-servicepath": "/",
}

# Intervalo sem leituras a partir do qual a linha do gráfico é interrompida (o ESP32 publica a cada 5 s)
GAP = timedelta(minutes=10)

# Atributos publicados pelo ESP32 (nome no Orion/STH -> titulo e unidade do grafico)
ATTRIBUTES = {
    "temperature": ("Temperatura", "°C"),
    "humidity": ("Umidade", "%"),
    "luminosity": ("Luminosidade", "%"),
}


def obter_historico(host, entity_type, entity_id, attribute, last_n):
    """Busca os ultimos `last_n` valores de um atributo no STH-Comet.

    Retorna uma lista de pares (instante, valor). Lista vazia se nao houver dados.
    """
    url = (f"{host.rstrip('/')}/STH/v1/contextEntities/type/{entity_type}"
           f"/id/{entity_id}/attributes/{attribute}")
    response = requests.get(url, headers=FIWARE_HEADERS, params={"lastN": last_n}, timeout=10)
    response.raise_for_status()  # erro HTTP vira excecao com o status

    # Formato da resposta do STH: contextResponses[0].contextElement.attributes[0].values[]
    values = response.json()["contextResponses"][0]["contextElement"]["attributes"][0]["values"]
    pontos = []
    for entry in values:
        try:
            instante = datetime.fromisoformat(entry["recvTime"].replace("Z", "+00:00"))
            pontos.append((instante, float(entry["attrValue"])))
        except (KeyError, ValueError, TypeError):
            continue  # ignora amostras malformadas em vez de quebrar o grafico
    return pontos


def plotar(historicos, entity_id, save_path=None, show=True):
    """Desenha um grafico por variavel, com a linha da media de cada uma."""
    fig, eixos = plt.subplots(len(historicos), 1, figsize=(12, 3.2 * len(historicos)), sharex=True)
    if len(historicos) == 1:
        eixos = [eixos]

    for eixo, (attribute, pontos) in zip(eixos, historicos.items()):
        titulo, unidade = ATTRIBUTES[attribute]
        if not pontos:
            eixo.text(0.5, 0.5, "Sem dados no STH-Comet", ha="center", va="center", transform=eixo.transAxes)
            eixo.set_title(titulo)
            continue
        valores = [p[1] for p in pontos]
        media = sum(valores) / len(valores)
        # Interrompe a linha onde ficou mais de GAP sem leituras, para não desenhar dados que não existem
        tempos_plot, valores_plot = [], []
        for i, (instante, valor) in enumerate(pontos):
            if i > 0 and instante - pontos[i - 1][0] > GAP:
                tempos_plot.append(instante)
                valores_plot.append(float("nan"))
            tempos_plot.append(instante)
            valores_plot.append(valor)
        eixo.plot(tempos_plot, valores_plot, marker="o", markersize=3, linestyle="-")
        eixo.axhline(media, linestyle="--", color="gray", label=f"Média: {media:.1f} {unidade}")
        eixo.set_title(f"{titulo} ({len(valores)} leituras)")
        eixo.set_ylabel(unidade)
        eixo.grid(True, alpha=0.3)
        eixo.legend(loc="upper left")

    eixos[-1].set_xlabel("Horário da leitura (UTC)")
    fig.suptitle(f"WineGuard — histórico do STH-Comet · {entity_id}")
    fig.autofmt_xdate()
    fig.tight_layout()

    if save_path:
        fig.savefig(save_path, dpi=120)
        print(f"Gráfico salvo em {save_path}")
    if show:
        plt.show()


def main(argv=None):
    parser = argparse.ArgumentParser(description="Histórico do WineGuard no STH-Comet")
    parser.add_argument("--host", default=os.environ.get("FIWARE_STH_URL", "http://localhost:8666"),
                        help="URL do STH-Comet (padrão: variável FIWARE_STH_URL ou http://localhost:8666)")
    parser.add_argument("--entity", default="urn:ngsi-ld:WineSensor:wg001", help="id da entidade no Orion")
    parser.add_argument("--type", default="WineSensor", help="tipo da entidade")
    parser.add_argument("--lastN", type=int, default=30, help="quantidade de leituras (1 a 100)")
    parser.add_argument("--attr", choices=list(ATTRIBUTES) + ["all"], default="all", help="variável a mostrar")
    parser.add_argument("--save", help="salva o gráfico neste arquivo .png")
    parser.add_argument("--no-show", action="store_true", help="não abre a janela do gráfico")
    args = parser.parse_args(argv)

    if not 1 <= args.lastN <= 100:
        parser.error("--lastN deve estar entre 1 e 100")

    attributes = list(ATTRIBUTES) if args.attr == "all" else [args.attr]
    historicos = {}
    for attribute in attributes:
        try:
            historicos[attribute] = obter_historico(args.host, args.type, args.entity, attribute, args.lastN)
            print(f"{attribute}: {len(historicos[attribute])} leituras")
        except requests.RequestException as e:
            print(f"Erro ao consultar {attribute} no STH-Comet ({args.host}): {e}", file=sys.stderr)
            historicos[attribute] = []

    if not any(historicos.values()):
        print("Nenhum dado retornado. Confira o host, o id da entidade e se o dispositivo está publicando.")
        return 1
    plotar(historicos, args.entity, save_path=args.save, show=not args.no_show)
    return 0


if __name__ == "__main__":
    sys.exit(main())
