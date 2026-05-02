package com.truthscope.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.truthscope.web.dto.request.AttachToSessionRequest;
import com.truthscope.web.dto.response.ArticleResponse;
import com.truthscope.web.entity.AnalysisSession;
import com.truthscope.web.entity.Article;
import com.truthscope.web.entity.enums.SessionStatus;
import com.truthscope.web.repository.AnalysisSessionRepository;
import com.truthscope.web.repository.ArticleRepository;
import com.truthscope.web.support.AbstractIntegrationTest;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** ArticleController 통합 테스트 — Testcontainers PostgreSQL 기반 */
class ArticleControllerIntegrationTest extends AbstractIntegrationTest {

  @Autowired private TestRestTemplate rest;
  @Autowired private ArticleRepository articleRepository;
  @Autowired private AnalysisSessionRepository sessionRepository;

  private Article savedExtractedArticle;
  private AnalysisSession savedSession;

  @BeforeEach
  void setup() {
    // FK 안전 순서: Article(자식) 먼저, Session(부모) 나중
    articleRepository.deleteAllInBatch();
    sessionRepository.deleteAllInBatch();

    AnalysisSession session =
        AnalysisSession.builder()
            .status(SessionStatus.PENDING)
            .requestedAt(LocalDateTime.now())
            .build();
    savedSession = sessionRepository.saveAndFlush(session);

    Article article =
        Article.extract(
            "https://example.com/news/integration", "통합테스트 제목", "통합테스트 본문", "ko", "example.com");
    savedExtractedArticle = articleRepository.saveAndFlush(article);
  }

  @Test
  @DisplayName("GET /api/v1/articles/{id} — happy path: 200 + EXTRACTED 상태")
  void findById_happyPath() {
    ResponseEntity<ArticleResponse> response =
        rest.getForEntity(
            "/api/v1/articles/" + savedExtractedArticle.getId(), ArticleResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getId()).isEqualTo(savedExtractedArticle.getId());
    assertThat(response.getBody().getUrl()).isEqualTo("https://example.com/news/integration");
    assertThat(response.getBody().getStatus()).isEqualTo("EXTRACTED");
    assertThat(response.getBody().getSessionId()).isNull();
    assertThat(response.getBody().getCreatedAt()).isNotNull();
  }

  @Test
  @DisplayName("GET /api/v1/articles/{id} — 미존재 → 404")
  void findById_notFound() {
    ResponseEntity<String> response =
        rest.getForEntity("/api/v1/articles/" + UUID.randomUUID(), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  @DisplayName("POST /api/v1/articles/{id}/attach — happy path: EXTRACTED → ATTACHED")
  void attachToSession_happyPath() {
    AttachToSessionRequest request = new AttachToSessionRequest(savedSession.getId());

    ResponseEntity<ArticleResponse> response =
        rest.postForEntity(
            "/api/v1/articles/" + savedExtractedArticle.getId() + "/attach",
            request,
            ArticleResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getStatus()).isEqualTo("ATTACHED");
    assertThat(response.getBody().getSessionId()).isEqualTo(savedSession.getId());

    Article persisted = articleRepository.findById(savedExtractedArticle.getId()).orElseThrow();
    assertThat(persisted.getSession()).isNotNull();
    assertThat(persisted.getSession().getId()).isEqualTo(savedSession.getId());
  }

  @Test
  @DisplayName("POST /api/v1/articles/{id}/attach — 재부착 거부 → 409")
  void attachToSession_alreadyAttached_returns409() {
    AttachToSessionRequest request = new AttachToSessionRequest(savedSession.getId());

    // 1차 부착: 명시적 success 검증 — 실패 시 후속 409 검증 무효화 방지 (CodeRabbit 리뷰)
    ResponseEntity<ArticleResponse> firstAttach =
        rest.postForEntity(
            "/api/v1/articles/" + savedExtractedArticle.getId() + "/attach",
            request,
            ArticleResponse.class);
    assertThat(firstAttach.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(firstAttach.getBody()).isNotNull();
    assertThat(firstAttach.getBody().getStatus()).isEqualTo("ATTACHED");

    // 2차 부착 시도: 재부착 거부 검증
    ResponseEntity<String> response =
        rest.postForEntity(
            "/api/v1/articles/" + savedExtractedArticle.getId() + "/attach", request, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  @DisplayName("POST /api/v1/articles/{id}/attach — Session 미존재 → 404")
  void attachToSession_sessionNotFound_returns404() {
    AttachToSessionRequest request = new AttachToSessionRequest(UUID.randomUUID());

    ResponseEntity<String> response =
        rest.postForEntity(
            "/api/v1/articles/" + savedExtractedArticle.getId() + "/attach", request, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }
}
