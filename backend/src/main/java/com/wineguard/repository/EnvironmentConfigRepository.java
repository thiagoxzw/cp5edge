package com.wineguard.repository;

import com.wineguard.entity.EnvironmentConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnvironmentConfigRepository extends JpaRepository<EnvironmentConfig, Long> {
}
