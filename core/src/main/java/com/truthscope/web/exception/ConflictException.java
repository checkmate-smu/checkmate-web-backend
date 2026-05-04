package com.truthscope.web.exception;

/** 리소스 충돌 예외 — 409 상태 코드 */
public class ConflictException extends AppException {

  public ConflictException(String message) {
    super(409, message);
  }
}
