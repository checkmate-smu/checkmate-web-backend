package com.checkmate.web.controller;

import com.checkmate.web.dto.request.AttachToSessionRequest;
import com.checkmate.web.dto.response.ArticleResponse;
import com.checkmate.web.service.ArticleService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Article 조회 및 세션 부착 컨트롤러 */
@RestController
@RequestMapping("/api/v1/articles")
@RequiredArgsConstructor
public class ArticleController {

  private final ArticleService articleService;

  /**
   * Article ID로 조회.
   *
   * @return 200 ArticleResponse (없으면 GlobalExceptionHandler가 404)
   */
  @GetMapping("/{id}")
  public ArticleResponse findById(@PathVariable UUID id) {
    return articleService.findById(id);
  }

  /**
   * Article을 분석 세션에 부착 (action endpoint — state transition 시맨틱).
   *
   * @return 200 ArticleResponse
   *     <ul>
   *       <li>404 — Article 또는 Session 미존재
   *       <li>409 — 이미 부착된 Article (재부착 거부, ConflictException)
   *       <li>400 — sessionId 누락 또는 invalid UUID (PathVariable / body)
   *     </ul>
   */
  @PostMapping("/{id}/attach")
  public ArticleResponse attachToSession(
      @PathVariable UUID id, @Valid @RequestBody AttachToSessionRequest request) {
    return articleService.attachToSession(id, request.sessionId());
  }
}
