package com.checkmate.web.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Article 조회/부착 응답 DTO */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleResponse {

  /** Article ID */
  private UUID id;

  /** 기사 URL */
  private String url;

  /** 제목 (추출 단계에 따라 nullable) */
  private String title;

  /** 본문 (추출 단계에 따라 nullable) */
  private String body;

  /** 언어 코드 (추출 단계에 따라 nullable) */
  private String lang;

  /** 도메인 (추출 단계에 따라 nullable) */
  private String domain;

  /**
   * 부착 상태 ("EXTRACTED" | "ATTACHED") — Article entity에 status 필드 없음, 파생 계산 (ArticleConverter 참조).
   */
  private String status;

  /** 부착된 세션 ID (미부착 시 null — Jackson 기본 INCLUDE.ALWAYS로 null 직렬화) */
  private UUID sessionId;

  /** 추출 시각 */
  private LocalDateTime extractedAt;

  /** 생성 시각 (BaseTimeEntity audit) */
  private LocalDateTime createdAt;
}
