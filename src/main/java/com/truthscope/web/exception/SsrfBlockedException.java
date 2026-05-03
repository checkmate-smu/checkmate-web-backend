package com.truthscope.web.exception;

/** SSRF 차단 예외 — 400 상태 코드 (사용자 입력 URL이 내부망/금지 대역으로 해석됨) */
public class SsrfBlockedException extends AppException {

  public SsrfBlockedException(String message) {
    super(400, message);
  }
}
