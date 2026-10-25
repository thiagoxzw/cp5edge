package com.wineguard.integration.fiware;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wineguard.telemetry.HistoryPoint;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Valida o parsing do formato de resposta agregada do STH-Comet. */
class FiwareHistorySourceTest {

    private static final String AGGREGATED = """
            {
              "contextResponses": [{
                "contextElement": {
                  "attributes": [{
                    "name": "temperature",
                    "values": [{
                      "_id": {"origin": "2026-10-06T10:00:00.000Z", "resolution": "minute"},
                      "points": [
                        {"offset": 0, "samples": 12, "sum": 156.0},
                        {"offset": 1, "samples": 0, "sum": 0},
                        {"offset": 2, "samples": 10, "sum": 140.0}
                      ]
                    }]
                  }],
                  "id": "urn:ngsi-ld:WineSensor:wg001",
                  "isPattern": false,
                  "type": "WineSensor"
                },
                "statusCode": {"code": "200", "reasonPhrase": "OK"}
              }]
            }
            """;

    @Test
    void parsesAggregatedSumIntoAverages() throws Exception {
        JsonNode body = new ObjectMapper().readTree(AGGREGATED);
        List<HistoryPoint> points = FiwareHistorySource.parseAggregated(body, "minute",
                Instant.parse("2026-10-06T09:00:00Z"), Instant.parse("2026-10-06T11:00:00Z"));

        assertThat(points).hasSize(2); // offset 1 tem samples=0 e é ignorado
        assertThat(points.get(0).timestamp()).isEqualTo(Instant.parse("2026-10-06T10:00:00Z"));
        assertThat(points.get(0).value()).isEqualTo(13.0);
        assertThat(points.get(1).timestamp()).isEqualTo(Instant.parse("2026-10-06T10:02:00Z"));
        assertThat(points.get(1).value()).isEqualTo(14.0);
    }

    @Test
    void parsesNumbersSentAsStrings() {
        assertThat(FiwareTelemetrySource.toDouble("13.5")).isEqualTo(13.5);
        assertThat(FiwareTelemetrySource.toDouble(13)).isEqualTo(13.0);
        assertThat(FiwareTelemetrySource.toDouble(" ")).isNull();
        assertThat(FiwareTelemetrySource.toDouble("abc")).isNull();
    }
}
