package com.checkmate.web.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "articles")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class Article extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", columnDefinition = "uuid")
  private UUID id;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "session_id", unique = true)
  private AnalysisSession session;

  @Column(name = "url", length = 2048)
  private String url;

  @Column(name = "title", length = 500)
  private String title;

  @Column(name = "body", columnDefinition = "TEXT")
  private String body;

  @Column(name = "lang", length = 10)
  private String lang;

  @Column(name = "domain", length = 255)
  private String domain;

  @Column(name = "extracted_at")
  private LocalDateTime extractedAt;

  /**
   * 추출된 기사를 invariant를 만족하는 상태로만 생성한다 (DDD always-valid 모델).
   *
   * <p>url은 http(s) 스킴이어야 하며 null/blank 허용 안 함. 다른 필드는 추출 단계에 따라 부재할 수 있어 nullable.
   */
  public static Article extract(String url, String title, String body, String lang, String domain) {
    validateUrl(url);
    return Article.builder()
        .url(url)
        .title(title)
        .body(body)
        .lang(lang)
        .domain(domain)
        .extractedAt(LocalDateTime.now())
        .build();
  }

  private static void validateUrl(String url) {
    if (url == null || url.isBlank()) {
      throw new IllegalArgumentException("url은 null이거나 비어 있을 수 없습니다");
    }
    if (!url.startsWith("http://") && !url.startsWith("https://")) {
      throw new IllegalArgumentException("url은 http:// 또는 https://로 시작해야 합니다: " + url);
    }
  }
}
