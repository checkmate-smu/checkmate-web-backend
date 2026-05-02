package com.checkmate.web.dto.response;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 뉴스 기사 분석 시작 응답 DTO */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisResponse {

  /** 분석 세션 ID */
  private UUID sessionId;

  /** 추출된 Article의 ID (FE Phase 21 attach wiring 위해 노출) */
  private UUID articleId;

  /** 현재 세션 상태 */
  private String status;
}
