package com.truthscope.web.converter;

import com.truthscope.web.dto.response.AnalysisResponse;
import com.truthscope.web.entity.AnalysisSession;
import com.truthscope.web.entity.Article;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** AnalysisSession ↔ AnalysisResponse 변환 유틸리티 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AnalysisConverter {

  /**
   * AnalysisSession + Article → AnalysisResponse 변환.
   *
   * <p>FE Phase 21 P4 attach wiring을 위해 articleId 노출 의무. article은 항상 attached 상태로 전달됨
   * (AnalysisTransactionService에서 `Article.extract().attachTo(session).save()` 후 호출).
   */
  public static AnalysisResponse toResponse(AnalysisSession session, Article article) {
    return AnalysisResponse.builder()
        .sessionId(session.getId())
        .articleId(article.getId())
        .status(session.getStatus().name())
        .build();
  }
}
