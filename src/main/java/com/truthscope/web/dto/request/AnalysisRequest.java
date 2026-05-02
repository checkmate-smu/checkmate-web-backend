package com.truthscope.web.dto.request;

import jakarta.validation.constraints.NotBlank;

/** 뉴스 기사 분석 요청 DTO */
public record AnalysisRequest(@NotBlank(message = "URL은 필수입니다") String url) {}
