package com.checkmate.web.converter;

import com.checkmate.web.dto.response.ArticleResponse;
import com.checkmate.web.entity.AnalysisSession;
import com.checkmate.web.entity.Article;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Article ↔ ArticleResponse 변환 유틸리티 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ArticleConverter {

  /**
   * Article → ArticleResponse 변환.
   *
   * <p>status는 entity에 필드가 없어 session 부착 여부에서 파생: session==null → "EXTRACTED" / session!=null →
   * "ATTACHED" (FE PLAN rev.6 ArticleStatus 2개와 1:1 매핑).
   */
  public static ArticleResponse toResponse(Article article) {
    AnalysisSession session = article.getSession();
    return ArticleResponse.builder()
        .id(article.getId())
        .url(article.getUrl())
        .title(article.getTitle())
        .body(article.getBody())
        .lang(article.getLang())
        .domain(article.getDomain())
        .status(session == null ? "EXTRACTED" : "ATTACHED")
        .sessionId(session == null ? null : session.getId())
        .extractedAt(article.getExtractedAt())
        .createdAt(article.getCreatedAt())
        .build();
  }
}
