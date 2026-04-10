package com.checkmate.web.entity;

import com.checkmate.web.entity.enums.SessionStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "analysis_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AnalysisSession extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "member_id")
  private Member member;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private SessionStatus status;

  @Column(name = "requested_at", nullable = false)
  private LocalDateTime requestedAt;

  @Column(name = "completed_at")
  private LocalDateTime completedAt;

  @Column(name = "total_score", precision = 5, scale = 2)
  private BigDecimal totalScore;

  @Column(precision = 5, scale = 2)
  private BigDecimal coverage;

  @Column(name = "tier1_count")
  private Integer tier1Count;

  @Column(name = "tier2_count")
  private Integer tier2Count;

  @Column(name = "tier3_count")
  private Integer tier3Count;

  @Column(name = "error_message", columnDefinition = "TEXT")
  private String errorMessage;
}
