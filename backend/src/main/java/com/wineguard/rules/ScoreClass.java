package com.wineguard.rules;

/** Classificação do Wine Score. */
public enum ScoreClass {
    EXCELLENT("Excelente", 90),
    GOOD("Bom", 75),
    ATTENTION("Atenção", 50),
    CRITICAL("Crítico", 0);

    private final String label;
    private final int minScore;

    ScoreClass(String label, int minScore) {
        this.label = label;
        this.minScore = minScore;
    }

    public String label() {
        return label;
    }

    public static ScoreClass of(int score) {
        for (ScoreClass c : values()) {
            if (score >= c.minScore) return c;
        }
        return CRITICAL;
    }
}
