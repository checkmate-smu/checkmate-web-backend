package com.truthscope.web.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.truthscope.web.entity.enums.SessionStatus;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * Article 도메인 invariant + 정적 팩토리 TDD 시범.
 *
 * <p>DDD "always-valid" 모델: 객체 생성 시점에 invariant 검증을 강제하여 invalid 상태가 메모리에 존재하지 못하도록 한다. Article은 분석
 * 파이프라인의 entry point이므로 url invariant가 도메인 정합성의 첫 게이트.
 */
class ArticleTest {

  @Test
  void extract_정상_https_url로_생성하면_invariant_통과() {
    Article article =
        Article.extract("https://example.com/news/123", "제목", "본문", "ko", "example.com");

    assertThat(article.getUrl()).isEqualTo("https://example.com/news/123");
    assertThat(article.getDomain()).isEqualTo("example.com");
    assertThat(article.getExtractedAt()).isNotNull();
  }

  @Test
  void extract_http_url도_허용() {
    Article article = Article.extract("http://example.com/legacy", null, null, null, null);

    assertThat(article.getUrl()).startsWith("http://");
  }

  @Test
  void extract_https_가_아닌_스킴은_거부() {
    assertThatThrownBy(() -> Article.extract("ftp://example.com/file", null, null, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("url");
  }

  @Test
  void extract_null_url은_거부() {
    assertThatThrownBy(() -> Article.extract(null, null, null, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("url");
  }

  @Test
  void extract_blank_url은_거부() {
    assertThatThrownBy(() -> Article.extract("   ", null, null, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("url");
  }

  @Test
  void attachTo_세션_부착_시_session_getter로_조회_가능() {
    Article article = Article.extract("https://example.com/news/1", null, null, null, null);
    AnalysisSession session = newSession();

    article.attachTo(session);

    assertThat(article.getSession()).isSameAs(session);
  }

  @Test
  void attachTo_null_session은_거부() {
    Article article = Article.extract("https://example.com/news/2", null, null, null, null);

    assertThatThrownBy(() -> article.attachTo(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("session");
  }

  @Test
  void attachTo_이미_부착된_Article은_재부착_불가() {
    Article article = Article.extract("https://example.com/news/3", null, null, null, null);
    article.attachTo(newSession());

    assertThatThrownBy(() -> article.attachTo(newSession()))
        .isInstanceOf(IllegalStateException.class);
  }

  private static AnalysisSession newSession() {
    return AnalysisSession.builder()
        .status(SessionStatus.PENDING)
        .requestedAt(LocalDateTime.now())
        .build();
  }
}
