package com.checkmate.web.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "factcheck_cache")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class FactcheckCache extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", columnDefinition = "uuid")
  private UUID id;

  @Column(name = "claim_text", columnDefinition = "TEXT")
  private String claimText;

  @Column(name = "source_org", length = 100)
  private String sourceOrg;

  @Column(name = "rating", length = 50)
  private String rating;

  @Column(name = "original_url", length = 2048)
  private String originalUrl;

  @Column(name = "language", length = 10)
  private String language;

  @Column(name = "collected_at")
  private LocalDateTime collectedAt;

  @Column(name = "expires_at")
  private LocalDateTime expiresAt;
}
