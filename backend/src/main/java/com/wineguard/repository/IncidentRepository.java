package com.wineguard.repository;

import com.wineguard.domain.IncidentStatus;
import com.wineguard.entity.Incident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface IncidentRepository extends JpaRepository<Incident, Long>, JpaSpecificationExecutor<Incident> {

    List<Incident> findByStatusIn(Collection<IncidentStatus> statuses);

    List<Incident> findByDeviceIdAndStatusIn(Long deviceId, Collection<IncidentStatus> statuses);

    /** Incidentes que se sobrepõem à janela [since, agora]: iniciados depois, abertos, ou encerrados depois. */
    @Query("""
            select i from Incident i
            where i.device.id = :deviceId
              and (i.startedAt >= :since or i.normalizedAt is null or i.normalizedAt >= :since)
            """)
    List<Incident> findOverlapping(@Param("deviceId") Long deviceId, @Param("since") Instant since);

    @Modifying
    @Query("delete from Incident i where i.device.id = :deviceId")
    void deleteByDeviceId(@Param("deviceId") Long deviceId);

    @Modifying
    @Query("delete from Incident i where i.demo = true and i.status = com.wineguard.domain.IncidentStatus.RESOLVED")
    int deleteResolvedDemoIncidents();
}
