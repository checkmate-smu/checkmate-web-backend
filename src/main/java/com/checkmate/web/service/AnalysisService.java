package com.checkmate.web.service;

import com.checkmate.web.converter.AnalysisConverter;
import com.checkmate.web.dto.request.AnalysisRequest;
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

/** 뉴스 기사 분석 오케스트레이션 서비스 */
@Service
@RequiredArgsConstructor
public class AnalysisService {

  private final AnalysisSessionRepository sessionRepository;
  private final ArticleRepository articleRepository;
  private final ContentExtractService contentExtractService;

  /** 뉴스 기사 URL을 받아 분석 세션을 생성하고 본문을 추출한다. 외부 HTTP 호출(Jsoup)은 트랜잭션 밖에서 수행하여 DB 커넥션 점유를 최소화한다. */
  public AnalysisResponse analyze(AnalysisRequest request) {
    // 1. 세션 생성 (트랜잭션)
    UUID sessionId = createPendingSession();

    try {
      // 2. 기사 본문 추출 (트랜잭션 밖 — 외부 HTTP 호출)
      ExtractedArticle extracted = contentExtractService.extract(request.url());

      // 3. Article 저장 + 상태 업데이트 (트랜잭션)
      return persistArticleAndUpdateStatus(sessionId, request.url(), extracted);
    } catch (RuntimeException ex) {
      // 4. 실패 시 세션 상태 → FAILED
      markFailed(sessionId);
      throw ex;
    }
  }

  /** 세션 생성 후 ID 반환 — 영속 컨텍스트 독립 */
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
