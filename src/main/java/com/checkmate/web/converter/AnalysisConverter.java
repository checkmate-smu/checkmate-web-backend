package com.checkmate.web.converter;

import com.checkmate.web.dto.response.AnalysisResponse;
import com.checkmate.web.entity.AnalysisSession;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** AnalysisSession ↔ AnalysisResponse 변환 유틸리티 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AnalysisConverter {

  /** AnalysisSession → AnalysisResponse 변환 */
  public static AnalysisResponse toResponse(AnalysisSession session) {
    return AnalysisResponse.builder()
        .sessionId(session.getId())
        .status(session.getStatus().name())
        .build();
  }
}
