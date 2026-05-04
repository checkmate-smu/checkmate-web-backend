package com.truthscope.web.exception;

/** 잘못된 요청 예외 — 400 상태 코드 */
public class BadRequestException extends AppException {

  public BadRequestException(String message) {
    super(400, message);
  }
}
