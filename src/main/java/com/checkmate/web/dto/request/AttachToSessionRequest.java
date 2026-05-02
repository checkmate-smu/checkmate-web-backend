package com.checkmate.web.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Article을 분석 세션에 부착 요청 DTO */
public record AttachToSessionRequest(@NotNull(message = "sessionId는 필수입니다") UUID sessionId) {}
