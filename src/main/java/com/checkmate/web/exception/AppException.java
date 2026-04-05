package com.checkmate.web.exception;

/** 애플리케이션 기본 예외 — 모든 커스텀 예외의 부모 클래스 */
public class AppException extends RuntimeException {

  private final int statusCode;

  /** HTTP 상태 코드와 메시지로 예외 생성 */
  public AppException(int statusCode, String message) {
    super(message);
    this.statusCode = statusCode;
  }

  /** HTTP 상태 코드 반환 */
  public int getStatusCode() {
    return statusCode;
  }

  /** 상태 문자열 반환 — 4xx는 "fail", 5xx는 "error" (프론트 AppError와 동일 분류) */
  public String getStatus() {
    return statusCode >= 500 ? "error" : "fail";
  }
}
