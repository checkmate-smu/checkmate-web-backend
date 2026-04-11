package com.checkmate.web.service;

import com.checkmate.web.converter.AnalysisConverter;
import com.checkmate.web.dto.response.AnalysisResponse;
import com.checkmate.web.dto.response.ExtractedArticle;
import com.checkmate.web.entity.AnalysisSession;
import com.checkmate.web.entity.Article;
import com.checkmate.web.entity.enums.SessionStatus;
import com.checkmate.web.exception.NotFoundException;
import com.checkmate.web.repository.AnalysisSessionRepository;
import com.checkmate.web.repository.ArticleRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 분석 세션/기사 저장을 위한 트랜잭션 전용 서비스 — self-invocation 프록시 우회 방지 */
@Service
@RequiredArgsConstructor
public class AnalysisTransactionService {

  private final AnalysisSessionRepository sessionRepository;
  private final ArticleRepository articleRepository;

  /** 세션 생성 후 ID 반환 */
  @Transactional
  public UUID createPendingSession() {
    AnalysisSession session =
        AnalysisSession.builder()
            .status(SessionStatus.PENDING)
            .requestedAt(LocalDateTime.now())
            .build();
    return sessionRepository.save(session).getId();
  }

  /** Article 저장 + 세션 상태 EXTRACTING으로 전이 */
  @Transactional
  public AnalysisResponse persistArticleAndUpdateStatus(
      UUID sessionId, String url, ExtractedArticle extracted) {
    AnalysisSession session =
        sessionRepository
            .findById(sessionId)
            .orElseThrow(() -> new NotFoundException("세션을 찾을 수 없습니다"));

    Article article =
        Article.builder()
            .session(session)
            .url(url)
            .title(extracted.getTitle())
            .body(extracted.getBody())
            .lang(extracted.getLang())
            .domain(extracted.getDomain())
            .extractedAt(LocalDateTime.now())
            .build();
    articleRepository.save(article);

    session.updateStatus(SessionStatus.EXTRACTING);

    return AnalysisConverter.toResponse(session);
  }

  /** 세션 상태를 FAILED로 전이 */
  @Transactional
  public void markFailed(UUID sessionId) {
    sessionRepository
        .findById(sessionId)
        .ifPresent(session -> session.updateStatus(SessionStatus.FAILED));
  }
}
