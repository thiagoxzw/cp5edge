package com.wineguard.demo;

import com.wineguard.domain.Variable;

/** Cenários do Modo Demo — cada um força uma variável para a faixa CRÍTICA. */
public enum DemoScenario {
    TEMPERATURE(Variable.TEMPERATURE, "Temperatura acima do limite"),
    HUMIDITY(Variable.HUMIDITY, "Umidade abaixo do limite"),
    LUMINOSITY(Variable.LUMINOSITY, "Exposição excessiva à luz");

    private final Variable variable;
    private final String description;

    DemoScenario(Variable variable, String description) {
        this.variable = variable;
        this.description = description;
    }

    public Variable variable() { return variable; }
    public String description() { return description; }
}
