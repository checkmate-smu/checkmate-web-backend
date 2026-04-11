package com.checkmate.web.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 뉴스 기사 URL에서 추출된 콘텐츠 응답 DTO */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtractedArticle {

  /** 기사 제목 */
  private String title;

  /** 기사 본문 (최대 8000자) */
  private String body;

  /** 언어 코드 (예: "ko", "en", "unknown") */
  private String lang;

  /** 도메인 (예: "news.naver.com") */
  private String domain;
}
