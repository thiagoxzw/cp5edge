package com.wineguard.repository;

import com.wineguard.domain.DeviceStatus;
import com.wineguard.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeviceRepository extends JpaRepository<Device, Long> {

    List<Device> findAllByOrderByZoneAscNameAsc();

    List<Device> findByStatus(DeviceStatus status);

    Optional<Device> findByDeviceKey(String deviceKey);

    boolean existsByDeviceKey(String deviceKey);

    boolean existsByDeviceKeyAndIdNot(String deviceKey, Long id);
}
