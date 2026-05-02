package com.truthscope.web.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "verify_sources")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class VerifySource extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", columnDefinition = "uuid")
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "result_id")
  private VerificationResult result;

  @Column(name = "title", length = 500)
  private String title;

  @Column(name = "publisher", length = 200)
  private String publisher;

  @Column(name = "url", length = 2048)
  private String url;

  @Column(name = "rating", length = 50)
  private String rating;

  @Column(name = "stance", length = 10)
  private String stance;

  @Column(name = "summary", columnDefinition = "TEXT")
  private String summary;
}
