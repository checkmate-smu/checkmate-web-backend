package com.checkmate.web.exception;

/** 리소스 미발견 예외 — 404 상태 코드 */
public class NotFoundException extends AppException {

  public NotFoundException(String message) {
    super(404, message);
  }
}
