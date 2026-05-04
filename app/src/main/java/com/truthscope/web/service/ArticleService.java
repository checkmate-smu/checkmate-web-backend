package com.truthscope.web.service;

import com.truthscope.web.converter.ArticleConverter;
import com.truthscope.web.dto.response.ArticleResponse;
import com.truthscope.web.entity.AnalysisSession;
import com.truthscope.web.entity.Article;
import com.truthscope.web.exception.ConflictException;
import com.truthscope.web.exception.NotFoundException;
import com.truthscope.web.repository.AnalysisSessionRepository;
import com.truthscope.web.repository.ArticleRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Article 도메인 조회 및 세션 부착 서비스 */
@Service
@RequiredArgsConstructor
public class ArticleService {

  private final ArticleRepository articleRepository;
  private final AnalysisSessionRepository sessionRepository;

  /**
   * ID로 Article 조회 후 ArticleResponse 반환. 없으면 404.
   *
   * <p>Converter 호출을 트랜잭션 내부로 두어 LAZY @OneToOne session lazy access를 안전하게 처리한다 (open-in-view=false
   * 가정에서 controller 시점 lazy access 시 LazyInitializationException 회피).
   */
  @Transactional(readOnly = true)
  public ArticleResponse findById(UUID id) {
    Article article =
        articleRepository
            .findById(id)
            .orElseThrow(() -> new NotFoundException("Article not found: " + id));
    return ArticleConverter.toResponse(article);
  }

  /**
   * Article을 분석 세션에 부착 후 ArticleResponse 반환.
   *
   * <p>재부착 시도 시 entity의 attachTo()가 IllegalStateException → Service에서 ConflictException(409)으로 명시적
   * 변환. GlobalExceptionHandler IllegalStateException 핸들러는 백스톱.
   *
   * <p>Hibernate dirty checking이 트랜잭션 commit 시 자동 영속화하므로 saveAndFlush 호출 불필요.
   */
  @Transactional
  public ArticleResponse attachToSession(UUID articleId, UUID sessionId) {
    Article article =
        articleRepository
            .findById(articleId)
            .orElseThrow(() -> new NotFoundException("Article not found: " + articleId));
    AnalysisSession session =
        sessionRepository
            .findById(sessionId)
            .orElseThrow(() -> new NotFoundException("Session not found: " + sessionId));

    try {
      article.attachTo(session);
    } catch (IllegalStateException ex) {
      throw new ConflictException(ex.getMessage());
    }

    return ArticleConverter.toResponse(article);
  }
}
