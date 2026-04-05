package com.checkmate.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 에러 응답 DTO — 전역 예외 처리 시 반환 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiErrorResponse {

  /** 요청 처리 상태 ("fail" | "error") */
  private String status;

  /** HTTP 상태 코드 */
  private int statusCode;

  /** 에러 메시지 */
  private String message;
}
