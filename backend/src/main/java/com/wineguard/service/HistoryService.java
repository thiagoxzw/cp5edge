package com.wineguard.service;

import com.wineguard.domain.Variable;
import com.wineguard.dto.HistoryResponse;
import com.wineguard.entity.Device;
import com.wineguard.entity.EnvironmentConfig;
import com.wineguard.telemetry.HistoryPeriod;
import com.wineguard.telemetry.HistoryPoint;
import com.wineguard.telemetry.HistorySeries;
import com.wineguard.telemetry.HistorySource;
import org.springframework.stereotype.Service;

import java.util.DoubleSummaryStatistics;
import java.util.List;

/** Consulta de histórico (STH-Comet no modo fiware) + limites atuais para as linhas do gráfico. */
@Service
public class HistoryService {

    private final HistorySource source;
    private final DeviceService devices;
    private final EnvironmentConfigService config;

    public HistoryService(HistorySource source, DeviceService devices, EnvironmentConfigService config) {
        this.source = source;
        this.devices = devices;
        this.config = config;
    }

    public HistoryResponse history(Long deviceId, String variableCode, String periodCode) {
        Device device = devices.find(deviceId);
        Variable variable = Variable.fromCode(variableCode);
        HistoryPeriod period = HistoryPeriod.fromCode(periodCode);

        HistorySeries series = source.fetch(device, variable, period);
        List<HistoryResponse.Point> points = series.points().stream()
                .map(p -> new HistoryResponse.Point(p.timestamp(), Math.round(p.value() * 100) / 100.0))
                .toList();

        DoubleSummaryStatistics st = series.points().stream().mapToDouble(HistoryPoint::value).summaryStatistics();
        HistoryResponse.Stats stats = st.getCount() == 0
                ? new HistoryResponse.Stats(null, null, null, 0)
                : new HistoryResponse.Stats(round(st.getMin()), round(st.getMax()), round(st.getAverage()), (int) st.getCount());

        return new HistoryResponse(device.getId(), device.getName(), variable.attribute(), variable.label(), variable.unit(),
                period.code(), series.source(), series.resolution(), limits(variable, config.current()), stats, points);
    }

    static HistoryResponse.Limits limits(Variable v, EnvironmentConfig c) {
        return switch (v) {
            case TEMPERATURE -> new HistoryResponse.Limits(c.getTemperatureMin(), c.getTemperatureMax());
            case HUMIDITY -> new HistoryResponse.Limits(c.getHumidityMin(), c.getHumidityMax());
            case LUMINOSITY -> new HistoryResponse.Limits(null, c.getLuminosityMax());
        };
    }

    private static double round(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
