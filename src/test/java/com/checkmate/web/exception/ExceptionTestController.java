package com.checkmate.web.exception;

import org.springframework.web.bind.annotation.GetMapping;
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
}
