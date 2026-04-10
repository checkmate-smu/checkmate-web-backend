package com.checkmate.web.entity;

import com.checkmate.web.entity.enums.DomainType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "articles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Article extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "session_id", nullable = false, unique = true)
  private AnalysisSession session;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String url;

  @Column(length = 500)
  private String title;

  @Column(columnDefinition = "TEXT")
  private String body;

  @Column(length = 10)
  private String lang;

  @Column(length = 255)
  private String domain;

  @Enumerated(EnumType.STRING)
  @Column(name = "domain_type", length = 20)
  private DomainType domainType;

  @Column(name = "extracted_at")
  private LocalDateTime extractedAt;
}
