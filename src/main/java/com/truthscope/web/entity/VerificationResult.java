package com.truthscope.web.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "verification_results")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class VerificationResult extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", columnDefinition = "uuid")
  private UUID id;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "claim_id", unique = true)
  private Claim claim;

  @Column(name = "tier")
  private Short tier;

  @Column(name = "label", length = 30)
  private String label;

  @Column(name = "score")
  private Short score;

  @Column(name = "reason", columnDefinition = "TEXT")
  private String reason;

  @Column(name = "disclaimer", columnDefinition = "TEXT")
  private String disclaimer;

  @Column(name = "verified_at")
  private LocalDateTime verifiedAt;
}
