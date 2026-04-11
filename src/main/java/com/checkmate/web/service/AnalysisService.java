package com.checkmate.web.service;

import com.checkmate.web.converter.AnalysisConverter;
import com.checkmate.web.dto.request.AnalysisRequest;
import com.checkmate.web.dto.response.AnalysisResponse;
import com.checkmate.web.dto.response.ExtractedArticle;
import com.checkmate.web.entity.AnalysisSession;
import com.checkmate.web.entity.Article;
import com.checkmate.web.entity.enums.SessionStatus;
import com.checkmate.web.repository.AnalysisSessionRepository;
import com.checkmate.web.repository.ArticleRepository;
import java.time.LocalDateTime;
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
    // 1. 세션 생성 (트랜잭션 1)
    AnalysisSession session = createPendingSession();

    // 2. 기사 본문 추출 (트랜잭션 밖 — 외부 HTTP 호출)
    ExtractedArticle extracted = contentExtractService.extract(request.url());

    // 3. Article 저장 + 상태 업데이트 (트랜잭션 2)
    persistArticleAndUpdateStatus(session, request.url(), extracted);

    return AnalysisConverter.toResponse(session);
  }

  @Transactional
  protected AnalysisSession createPendingSession() {
    AnalysisSession session =
        AnalysisSession.builder()
            .status(SessionStatus.PENDING)
            .requestedAt(LocalDateTime.now())
            .build();
    sessionRepository.save(session);
    return session;
  }

  @Transactional
  protected void persistArticleAndUpdateStatus(
      AnalysisSession session, String url, ExtractedArticle extracted) {
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
  }
}
