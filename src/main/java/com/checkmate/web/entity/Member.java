package com.checkmate.web.entity;

import com.checkmate.web.entity.enums.MemberRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "members")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class Member extends BaseTimeEntity {

  @Id
  @Column(name = "id", columnDefinition = "uuid")
  private UUID id;

  @Column(name = "email", length = 255, unique = true)
  private String email;

  @Column(name = "nickname", length = 50)
  private String nickname;

  @Enumerated(EnumType.STRING)
  @Column(name = "role", length = 20)
  private MemberRole role;
}
