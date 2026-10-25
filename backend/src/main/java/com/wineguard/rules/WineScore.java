package com.wineguard.rules;

import java.util.List;

/**
 * Resultado do Wine Score com o detalhamento de cada penalidade (exibido no frontend
 * para que o operador entenda POR QUE o score caiu).
 */
public record WineScore(int score, ScoreClass classification, List<Component> components) {

    /**
     * @param penalty    pontos descontados
     * @param maxPenalty teto da penalidade deste componente
     */
    public record Component(String key, String label, double penalty, double maxPenalty, String detail) {
    }

    public String label() {
        return classification.label();
    }
}
