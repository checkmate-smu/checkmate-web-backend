package com.checkmate.web.repository;

import com.checkmate.web.entity.AnalysisSession;
import com.checkmate.web.entity.enums.SessionStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisSessionRepository extends JpaRepository<AnalysisSession, UUID> {

  List<AnalysisSession> findByMemberId(UUID memberId);

  List<AnalysisSession> findByStatus(SessionStatus status);
}
