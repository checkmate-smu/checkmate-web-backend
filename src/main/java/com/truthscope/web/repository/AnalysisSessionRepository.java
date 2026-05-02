package com.truthscope.web.repository;

import com.truthscope.web.entity.AnalysisSession;
import com.truthscope.web.entity.enums.SessionStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisSessionRepository extends JpaRepository<AnalysisSession, UUID> {

  List<AnalysisSession> findByMemberId(UUID memberId);

  List<AnalysisSession> findByStatus(SessionStatus status);
}
