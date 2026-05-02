package com.checkmate.web.exception;

import com.checkmate.web.dto.ApiErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** 전역 예외 처리 핸들러 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  /** AppException 처리 — 적절한 상태 코드와 메시지 반환 */
  @ExceptionHandler(AppException.class)
  public ResponseEntity<ApiErrorResponse> handleAppException(AppException ex) {
    ApiErrorResponse body =
        ApiErrorResponse.builder()
            .status(ex.getStatus())
            .statusCode(ex.getStatusCode())
            .message(ex.getMessage())
            .build();
    return ResponseEntity.status(ex.getStatusCode()).body(body);
  }

  /** Article.attachTo 재부착 등 도메인 invariant 위반 → 409 변환 */
  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<ApiErrorResponse> handleIllegalState(IllegalStateException ex) {
    ApiErrorResponse body =
        ApiErrorResponse.builder().status("fail").statusCode(409).message(ex.getMessage()).build();
    return ResponseEntity.status(409).body(body);
  }

  /** Article.extract URL invariant 위반 등 → 400 변환 */
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
    ApiErrorResponse body =
        ApiErrorResponse.builder().status("fail").statusCode(400).message(ex.getMessage()).build();
    return ResponseEntity.status(400).body(body);
  }

  /** 유효성 검사 실패 처리 — 첫 번째 필드 오류 메시지 반환 */
  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    String message =
        ex.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .orElse("유효하지 않은 요청");

    ApiErrorResponse body =
        ApiErrorResponse.builder().status("fail").statusCode(400).message(message).build();
    return ResponseEntity.status(400).body(body);
  }

  /**
   * @PathVariable UUID 등 type 변환 실패 → ApiErrorResponse 통일
   */
  @Override
  protected ResponseEntity<Object> handleTypeMismatch(
      TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    String paramName =
        (ex instanceof MethodArgumentTypeMismatchException matm) ? matm.getName() : "parameter";
    ApiErrorResponse body =
        ApiErrorResponse.builder()
            .status("fail")
            .statusCode(400)
            .message(paramName + ": 잘못된 값 형식")
            .build();
    return ResponseEntity.status(400).body(body);
  }

  /** malformed JSON body (Bean Validation 전 단계) → ApiErrorResponse 통일 */
  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ApiErrorResponse body =
        ApiErrorResponse.builder()
            .status("fail")
            .statusCode(400)
            .message("요청 본문을 파싱할 수 없습니다")
            .build();
    return ResponseEntity.status(400).body(body);
  }

  /** 예상치 못한 예외 처리 — 500 에러 응답 반환 + 로깅 */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiErrorResponse> handleException(Exception ex) {
    log.error("Unhandled exception", ex);
    ApiErrorResponse body =
        ApiErrorResponse.builder().status("error").statusCode(500).message("서버 내부 오류").build();
    return ResponseEntity.status(500).body(body);
  }
}
