package com.truthscope.web.exception;

import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 예외 처리 테스트용 컨트롤러 */
@RestController
@RequestMapping("/test")
public class ExceptionTestController {

  @GetMapping("/not-found")
  public void notFound() {
    throw new NotFoundException("리소스를 찾을 수 없음");
  }

  @GetMapping("/bad-request")
  public void badRequest() {
    throw new BadRequestException("잘못된 요청");
  }

  @GetMapping("/server-error")
  public void serverError() {
    throw new RuntimeException("예상치 못한 오류");
  }

  @GetMapping("/illegal-argument")
  public void throwIllegalArgument() {
    throw new IllegalArgumentException("잘못된 인자");
  }

  @GetMapping("/illegal-state")
  public void throwIllegalState() {
    throw new IllegalStateException("잘못된 상태");
  }

  @GetMapping("/type-mismatch/{uuid}")
  public String typeMismatch(@PathVariable UUID uuid) {
    return uuid.toString();
  }

  @PostMapping("/malformed-body")
  public Map<String, Object> malformedBody(@RequestBody Map<String, Object> body) {
    return body;
  }
}
