package com.wineguard.service;

import com.wineguard.config.WineGuardProperties;
import com.wineguard.domain.AlertLevel;
import com.wineguard.domain.HealthStatus;
import com.wineguard.domain.IncidentType;
import com.wineguard.domain.Zone;
import com.wineguard.dto.DashboardSummary;
import com.wineguard.dto.DeviceLiveView;
import com.wineguard.dto.DeviceResponse;
import com.wineguard.dto.IncidentResponse;
import com.wineguard.rules.ScoreClass;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class DashboardService {

    private final DeviceService devices;
    private final IncidentService incidents;
    private final EventService events;
    private final WineGuardProperties props;

    public DashboardService(DeviceService devices, IncidentService incidents, EventService events, WineGuardProperties props) {
        this.devices = devices;
        this.incidents = incidents;
        this.events = events;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public DashboardSummary summary() {
        List<DeviceResponse> all = devices.list();
        List<IncidentResponse> open = incidents.allOpen().stream()
                .sorted(Comparator.comparing(i -> i.getStartedAt(), Comparator.reverseOrder()))
                .map(IncidentResponse::from).toList();

        int online = 0, unstable = 0, alert = 0, offline = 0, inactive = 0;
        List<Integer> scores = new ArrayList<>();
        for (DeviceResponse d : all) {
            DeviceLiveView l = d.live();
            switch (l.health()) {
                case ONLINE -> online++;
                case UNSTABLE -> { online++; unstable++; }
                case ALERT -> { online++; alert++; }
                case OFFLINE -> offline++;
                case INACTIVE -> inactive++;
            }
            if (l.score() != null) scores.add(l.score());
        }
        int critical = (int) open.stream().filter(i -> i.currentSeverity().isAtLeast(AlertLevel.CRITICAL)
                || i.type() == IncidentType.DEVICE_OFFLINE).count();
        Integer overall = scores.isEmpty() ? null
                : (int) Math.round(scores.stream().mapToInt(Integer::intValue).average().orElse(0));

        return new DashboardSummary(
                Instant.now(),
                props.isFiwareMode() ? "fiware" : "standalone",
                new DashboardSummary.Counters(all.size(), online, unstable, alert, offline, inactive, open.size(), critical),
                overall,
                overall != null ? ScoreClass.of(overall).label() : null,
                zones(all),
                all,
                open,
                events.recent(12));
    }

    /** Status de cada área do mapa: pior estado entre os dispositivos ativos da área. */
    static List<DashboardSummary.ZoneStatus> zones(List<DeviceResponse> all) {
        List<DashboardSummary.ZoneStatus> result = new ArrayList<>();
        for (Zone zone : Zone.values()) {
            List<DeviceResponse> inZone = all.stream()
                    .filter(d -> d.zone() == zone && d.live().health() != HealthStatus.INACTIVE).toList();
            String status = "empty";
            int rank = -1;
            for (DeviceResponse d : inZone) {
                String s = zoneStatusOf(d.live());
                int r = List.of("normal", "offline", "attention", "critical").indexOf(s);
                if (r > rank) {
                    rank = r;
                    status = s;
                }
            }
            List<DashboardSummary.ZoneDevice> zd = inZone.stream().map(d -> new DashboardSummary.ZoneDevice(
                    d.id(), d.name(), d.live().health(), d.live().environmentLevel(),
                    d.live().temperature(), d.live().humidity(), d.live().luminosity(), d.live().score())).toList();
            result.add(new DashboardSummary.ZoneStatus(zone, zone.label(), status, zd));
        }
        return result;
    }

    static String zoneStatusOf(DeviceLiveView l) {
        if (l.health() == HealthStatus.OFFLINE) return "offline";
        if (l.environmentLevel().isAtLeast(AlertLevel.CRITICAL)) return "critical";
        if (l.environmentLevel() == AlertLevel.ATTENTION || l.health() == HealthStatus.UNSTABLE) return "attention";
        return "normal";
    }
}
