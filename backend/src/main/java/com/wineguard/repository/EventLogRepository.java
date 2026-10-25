package com.wineguard.repository;

import com.wineguard.entity.EventLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventLogRepository extends JpaRepository<EventLog, Long> {

    List<EventLog> findAllByOrderByTimestampDesc(Pageable pageable);

    List<EventLog> findByDeviceIdOrderByTimestampDesc(Long deviceId, Pageable pageable);
}
