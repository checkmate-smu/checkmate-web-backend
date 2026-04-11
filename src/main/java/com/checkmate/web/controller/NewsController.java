package com.checkmate.web.controller;

import com.checkmate.web.dto.request.AnalysisRequest;
import com.checkmate.web.dto.response.AnalysisResponse;
import com.checkmate.web.service.AnalysisService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 뉴스 기사 분석 컨트롤러 */
@RestController
@RequestMapping("/api/v1/analysis-sessions")
@RequiredArgsConstructor
public class NewsController {

  private final AnalysisService analysisService;

  /**
   * 뉴스 기사 분석 요청
   *
   * @param request 분석할 뉴스 기사 URL
   * @return 생성된 세션 ID와 상태 (201 Created)
   */
  @PostMapping
  public ResponseEntity<AnalysisResponse> analyze(@Valid @RequestBody AnalysisRequest request) {
    AnalysisResponse response = analysisService.analyze(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }
}
