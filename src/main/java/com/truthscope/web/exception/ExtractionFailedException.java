package com.truthscope.web.exception;

/** 기사 본문 추출 실패 예외 — 500 상태 코드 */
public class ExtractionFailedException extends AppException {

  public ExtractionFailedException(String message) {
    super(500, message);
  }
}
