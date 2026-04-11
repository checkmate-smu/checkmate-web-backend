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

  /**
   * 뉴스 기사 URL을 받아 분석 세션을 생성하고 본문을 추출한다.
   *
   * @param request URL이 담긴 요청 DTO
   * @return 생성된 세션 ID와 현재 상태
   */
  @Transactional
  public AnalysisResponse analyze(AnalysisRequest request) {
    // 1. AnalysisSession 생성 (status=PENDING, member=null — 비회원 허용)
    AnalysisSession session =
        AnalysisSession.builder()
            .status(SessionStatus.PENDING)
            .requestedAt(LocalDateTime.now())
            .build();
    sessionRepository.save(session);

    // 2. 기사 본문 추출
    ExtractedArticle extracted = contentExtractService.extract(request.url());

    // 3. Article 생성
    Article article =
        Article.builder()
            .session(session)
            .url(request.url())
            .title(extracted.getTitle())
            .body(extracted.getBody())
            .lang(extracted.getLang())
            .domain(extracted.getDomain())
            .extractedAt(LocalDateTime.now())
            .build();
    articleRepository.save(article);

    // 4. 세션 상태 → EXTRACTING (비즈니스 메서드 사용)
    session.updateStatus(SessionStatus.EXTRACTING);

    return AnalysisConverter.toResponse(session);
  }
}
