package com.checkmate.web.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 헬스 체크 컨트롤러 */
@RestController
@RequestMapping("/api/v1")
public class HealthController {

  /** 서버 상태 확인 엔드포인트 */
  @GetMapping("/health")
  public Map<String, String> health() {
    return Map.of("status", "ok");
  }
}
