package com.truthscope.web.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.truthscope.web.html.ArticleHtmlParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * ContentExtractService helper 테스트 — ValidateUrl 7건은 SsrfGuardTest로 migration 완료, ExtractDomain 3 +
 * BodyTruncation 3 보존 (extractDomain/truncate는 ArticleHtmlParser로 이동).
 */
@DisplayName("ContentExtractService 단위 테스트 (helper)")
class ContentExtractServiceTest {

  @Nested
  @DisplayName("도메인 추출 (ArticleHtmlParser)")
  class ExtractDomain {

    @Test
    @DisplayName("일반 URL에서 도메인 추출")
    void simpleDomain() {
      String domain = ArticleHtmlParser.extractDomain("https://example.com/path/to/article");
      assertThat(domain).isEqualTo("example.com");
    }

    @Test
    @DisplayName("서브도메인 포함 URL에서 도메인 추출")
    void subDomain() {
      String domain =
          ArticleHtmlParser.extractDomain("https://news.naver.com/article/001/0012345678");
      assertThat(domain).isEqualTo("news.naver.com");
    }

    @Test
    @DisplayName("쿼리 파라미터 포함 URL에서 도메인 추출")
    void urlWithQueryParams() {
      String domain =
          ArticleHtmlParser.extractDomain(
              "https://www.yna.co.kr/view/AKR20260101001500?section=industry");
      assertThat(domain).isEqualTo("www.yna.co.kr");
    }
  }

  @Nested
  @DisplayName("본문 길이 제한 (8000자)")
  class BodyTruncation {

    @Test
    @DisplayName("8000자 이하 텍스트는 그대로 반환")
    void shortTextNotTruncated() {
      String shortText = "a".repeat(7999);
      String result = ArticleHtmlParser.truncate(shortText);
      assertThat(shortText.length()).isLessThan(8000);
      assertThat(result).isEqualTo(shortText);
    }

    @Test
    @DisplayName("8000자 초과 텍스트는 8000자로 잘림")
    void longTextTruncated() {
      String longText = "가".repeat(9000);
      String truncated = ArticleHtmlParser.truncate(longText);
      assertThat(truncated.length()).isEqualTo(8000);
    }

    @Test
    @DisplayName("정확히 8000자 텍스트는 그대로 반환")
    void exactLimitTextNotTruncated() {
      String exactText = "b".repeat(8000);
      String result = ArticleHtmlParser.truncate(exactText);
      assertThat(result.length()).isEqualTo(8000);
    }
  }
}
