package com.checkmate.web.service;

import com.checkmate.web.dto.request.AnalysisRequest;
import com.checkmate.web.dto.response.AnalysisResponse;
import com.checkmate.web.dto.response.ExtractedArticle;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 뉴스 기사 분석 오케스트레이션 서비스 */
@Service
@RequiredArgsConstructor
public class AnalysisService {

  private final AnalysisTransactionService transactionService;
  private final ContentExtractService contentExtractService;

  /** 뉴스 기사 URL을 받아 분석 세션을 생성하고 본문을 추출한다. 외부 HTTP 호출(Jsoup)은 트랜잭션 밖에서 수행하여 DB 커넥션 점유를 최소화한다. */
  public AnalysisResponse analyze(AnalysisRequest request) {
    // 1. 세션 생성 (트랜잭션 — 별도 빈)
    UUID sessionId = transactionService.createPendingSession();

    try {
      // 2. 기사 본문 추출 (트랜잭션 밖 — 외부 HTTP 호출)
      ExtractedArticle extracted = contentExtractService.extract(request.url());

      // 3. Article 저장 + 상태 업데이트 (트랜잭션 — 별도 빈)
      return transactionService.persistArticleAndUpdateStatus(sessionId, request.url(), extracted);
    } catch (RuntimeException ex) {
      // 4. 실패 시 세션 상태 → FAILED (markFailed 실패 시 원본 예외 보존)
      try {
        transactionService.markFailed(sessionId);
      } catch (RuntimeException markFailedEx) {
        ex.addSuppressed(markFailedEx);
      }
      throw ex;
    }
  }
}
