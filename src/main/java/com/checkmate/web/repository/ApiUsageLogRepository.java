package com.checkmate.web.repository;

import com.checkmate.web.entity.ApiUsageLog;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiUsageLogRepository extends JpaRepository<ApiUsageLog, UUID> {

  List<ApiUsageLog> findByProviderAndUsageDateBetween(
      String provider, LocalDate start, LocalDate end);
}
