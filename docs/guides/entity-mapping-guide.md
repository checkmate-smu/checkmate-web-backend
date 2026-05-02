# JPA Entity 매핑 가이드

> 대상: 이정훈(BE #5), 한지민(BE #6), 정세린(BE #7)
> Spring Boot + JPA 처음 하는 팀원을 위한 "이것만 따라하면 Entity 완성" 가이드

---

## 목차

1. [JPA Entity란?](#1-jpa-entity란)
2. [패키지 구조](#2-패키지-구조)
3. [BaseTimeEntity — 공통 상속](#3-basetimeentity--공통-상속)
4. [Enum 정의 규칙](#4-enum-정의-규칙)
5. [네이밍 컨벤션](#5-네이밍-컨벤션)
6. [UUID 전략](#6-uuid-전략)
7. [관계 매핑 패턴](#7-관계-매핑-패턴)
8. [완성 예시 — Member Entity](#8-완성-예시--member-entity)
9. [이슈별 작성 Entity 목록](#9-이슈별-작성-entity-목록)
10. [ERD 전체 관계 정리](#10-erd-전체-관계-정리)
11. [Entity 작성 후 체크리스트](#11-entity-작성-후-체크리스트)

---

## 1. JPA Entity란?

**JPA(Java Persistence API)** 는 Java 객체와 DB 테이블을 자동으로 연결해주는 표준 기술입니다.

```
Java 클래스(Entity)  ←→  DB 테이블
Java 필드(field)    ←→  DB 컬럼(column)
Java 객체(instance) ←→  DB 행(row)
```

즉, **`@Entity`를 붙인 클래스를 만들면 JPA가 SQL을 자동으로 생성**해줍니다.
직접 `INSERT INTO members ...` 같은 SQL을 짤 필요가 없습니다.

---

## 2. 패키지 구조

```
com.truthscope.web.entity/
├── BaseTimeEntity.java        ← 모든 Entity가 상속할 공통 클래스
├── Member.java                ← BE #5 (이정훈)
├── AnalysisSession.java       ← BE #5 (이정훈)
├── Article.java               ← BE #5 (이정훈)
├── Claim.java                 ← BE #5 (이정훈)
├── VerificationResult.java    ← BE #6 (한지민)
├── VerifySource.java          ← BE #6 (한지민)
├── FactcheckCache.java        ← BE #6 (한지민)
├── UserReaction.java          ← BE #7 (정세린)
├── ApiUsageLog.java           ← BE #7 (정세린)
└── enums/
    ├── MemberRole.java        ← BE #5
    ├── SessionStatus.java     ← BE #5
    ├── Importance.java        ← BE #5
    ├── DomainType.java        ← BE #5
    ├── ReactionType.java      ← BE #7
    ├── VerifyStance.java      ← BE #6
    └── Tier.java              ← BE #6
```

---

## 3. BaseTimeEntity — 공통 상속

### 왜 필요한가?

`created_at`, `updated_at` 컬럼은 거의 모든 테이블에 들어갑니다.
매번 똑같이 작성하는 대신, **공통 부모 클래스** 하나에 모아두고 상속받으면 됩니다.

### 코드

```java
// com/truthscope/web/entity/BaseTimeEntity.java
package com.truthscope.web.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@MappedSuperclass                                  // (1) DB 테이블을 만들지 않는 부모 클래스
@EntityListeners(AuditingEntityListener.class)     // (2) 자동으로 시간 기록
public abstract class BaseTimeEntity {

    @CreatedDate
    @Column(updatable = false)                     // (3) 한 번 저장하면 변경 불가
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
```

| 어노테이션 | 역할 |
|---|---|
| `@MappedSuperclass` | 이 클래스 자체는 테이블 없음. 자식 클래스가 필드를 물려받음 |
| `@EntityListeners(AuditingEntityListener.class)` | 저장/수정 시점에 JPA가 시간을 자동 기록 |
| `@CreatedDate` | INSERT 시점의 시간을 자동 저장 |
| `@LastModifiedDate` | UPDATE 시점의 시간을 자동 갱신 |
| `@Column(updatable = false)` | `created_at`은 처음 저장 후 절대 변경 안 됨 |

### @EnableJpaAuditing 설정

`BaseTimeEntity`의 자동 시간 기록이 동작하려면 **메인 애플리케이션 클래스**에 아래 어노테이션을 추가해야 합니다.

```java
// com/truthscope/web/TruthScopeWebApplication.java
@SpringBootApplication
@EnableJpaAuditing   // ← 이것을 추가해야 @CreatedDate, @LastModifiedDate가 동작
public class TruthScopeWebApplication {
    public static void main(String[] args) {
        SpringApplication.run(TruthScopeWebApplication.class, args);
    }
}
```

> **중요**: `@EnableJpaAuditing` 없이 실행하면 `createdAt`, `updatedAt`에 `null`이 들어갑니다.

---

## 4. Enum 정의 규칙

### 왜 Enum을 쓰나?

DB 컬럼에 `'pending'`, `'completed'` 같은 문자열을 그냥 넣으면 오타가 발생해도 컴파일 에러가 없습니다.
Enum을 사용하면 **허용된 값만 쓸 수 있어서** 실수를 컴파일 단계에서 잡아줍니다.

### 규칙: `@Enumerated(EnumType.STRING)` 반드시 사용

```java
// 잘못된 방법 (기본값 ORDINAL — 숫자로 저장됨. DB에 0, 1, 2... 로 들어가서 나중에 순서 바뀌면 데이터 깨짐)
@Enumerated
private SessionStatus status;

// 올바른 방법 (STRING — "PENDING", "COMPLETED" 문자열로 저장됨)
@Enumerated(EnumType.STRING)
private SessionStatus status;
```

### Enum 파일 예시

```java
// com/truthscope/web/entity/enums/MemberRole.java
package com.truthscope.web.entity.enums;

public enum MemberRole {
    USER,   // 일반 사용자
    ADMIN   // 관리자
}
```

```java
// com/truthscope/web/entity/enums/SessionStatus.java
package com.truthscope.web.entity.enums;

public enum SessionStatus {
    PENDING,     // 대기 중
    EXTRACTING,  // URL에서 기사 추출 중
    ANALYZING,   // 팩트체크 분석 중
    COMPLETED,   // 완료
    FAILED       // 실패
}
```

```java
// com/truthscope/web/entity/enums/Importance.java
package com.truthscope.web.entity.enums;

public enum Importance {
    HIGH,    // 높은 중요도
    MEDIUM,  // 중간 중요도
    LOW      // 낮은 중요도
}
```

```java
// com/truthscope/web/entity/enums/DomainType.java
package com.truthscope.web.entity.enums;

public enum DomainType {
    NEWS,        // 뉴스 사이트
    BLOG,        // 블로그
    GOVERNMENT,  // 정부 기관
    CORPORATE,   // 기업
    UNKNOWN      // 알 수 없음
}
```

```java
// com/truthscope/web/entity/enums/ReactionType.java
package com.truthscope.web.entity.enums;

public enum ReactionType {
    LIKE,    // 좋아요
    DISLIKE, // 싫어요
    REPORT   // 신고
}
```

```java
// com/truthscope/web/entity/enums/VerifyStance.java
package com.truthscope.web.entity.enums;

public enum VerifyStance {
    SUPPORTS, // 지지
    REFUTES,  // 반박
    NEUTRAL   // 중립
}
```

```java
// com/truthscope/web/entity/enums/Tier.java
package com.truthscope.web.entity.enums;

public enum Tier {
    TIER1(1), // AI 자체 검증
    TIER2(2), // 외부 팩트체크 DB 검색
    TIER3(3); // 웹 검색 보조

    private final int value;

    Tier(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
```

---

## 5. 네이밍 컨벤션

### DB ↔ Java 이름 변환 규칙

| DB (snake_case) | Java Entity (PascalCase/camelCase) |
|---|---|
| `members` 테이블 | `Member` 클래스 |
| `analysis_sessions` 테이블 | `AnalysisSession` 클래스 |
| `member_id` 컬럼 | `memberId` 필드 |
| `created_at` 컬럼 | `createdAt` 필드 |
| `domain_type` 컬럼 | `domainType` 필드 |

### @Table과 @Column은 항상 명시

JPA가 자동으로 이름을 추론하기도 하지만, **팀 프로젝트에서는 명시적으로 써주는 것이 안전**합니다.

```java
@Entity
@Table(name = "analysis_sessions")   // ← DB 테이블명 명시 (항상!)
public class AnalysisSession extends BaseTimeEntity {

    @Column(name = "total_score")     // ← DB 컬럼명과 다를 때 명시
    private Short totalScore;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
}
```

---

## 6. UUID 전략

### 일반 Entity: @GeneratedValue(strategy = GenerationType.UUID)

```java
@Id
@GeneratedValue(strategy = GenerationType.UUID)  // JPA가 UUID를 자동 생성
@Column(name = "id", updatable = false, nullable = false)
private UUID id;
```

### Member Entity 특수 케이스: @GeneratedValue 없음

`members` 테이블의 PK는 **Supabase Auth가 할당**합니다 (로그인 시 Supabase가 UUID를 생성).
따라서 `Member` Entity에서는 `@GeneratedValue`를 쓰지 않고, 직접 값을 세팅합니다.

```java
// Member.java
@Id
@Column(name = "id", updatable = false, nullable = false)
private UUID id;  // @GeneratedValue 없음! Supabase Auth에서 받아와서 직접 세팅
```

```java
// 서비스 코드에서 이렇게 사용
Member member = Member.builder()
    .id(UUID.fromString(supabaseUserId))  // Supabase에서 받은 UUID를 직접 넣음
    .email(email)
    .build();
```

---

## 7. 관계 매핑 패턴

### 핵심 개념: FK는 누가 가지고 있나?

DB에서 `member_id` 컬럼이 있는 테이블이 **FK 소유자**입니다.
JPA에서 FK 소유자 쪽에 `@JoinColumn`을 씁니다.

### 패턴 1: @ManyToOne + @JoinColumn (FK 소유 측)

```
상황: Member(1) ── (N) AnalysisSession
      → analysis_sessions 테이블에 member_id 컬럼이 있음
      → AnalysisSession이 FK 소유자
```

```java
// AnalysisSession.java (FK 소유자)
@ManyToOne(fetch = FetchType.LAZY)          // (1) Many(세션) To One(멤버)
@JoinColumn(name = "member_id")             // (2) DB 컬럼명 "member_id"
private Member member;                      // (3) Java 필드명
```

### 패턴 2: @OneToMany(mappedBy) (비소유 측, FK 없는 쪽)

```java
// Member.java (FK 없는 쪽)
@OneToMany(mappedBy = "member")             // (1) AnalysisSession의 "member" 필드를 참조
private List<AnalysisSession> sessions = new ArrayList<>();
```

> **mappedBy에 들어가는 값** = 상대방 클래스에서 나를 가리키는 **필드 이름** (컬럼명 아님!)

### 패턴 3: @OneToOne (1:1 관계)

```
상황: AnalysisSession(1) ── (1) Article
      → articles 테이블에 session_id 컬럼이 있음
      → Article이 FK 소유자
```

```java
// Article.java (FK 소유자)
@OneToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "session_id", unique = true)  // unique = true로 1:1 보장
private AnalysisSession session;
```

```java
// AnalysisSession.java (비소유 측)
@OneToOne(mappedBy = "session")
private Article article;
```

### cascade와 orphanRemoval

```java
// Article.java
@OneToMany(mappedBy = "article", cascade = CascadeType.ALL, orphanRemoval = true)
private List<Claim> claims = new ArrayList<>();
```

| 옵션 | 의미 |
|---|---|
| `cascade = CascadeType.ALL` | 부모(Article)를 저장/삭제하면 자식(Claim)도 자동으로 저장/삭제 |
| `orphanRemoval = true` | 부모와 연결이 끊긴 자식(고아 객체)을 자동으로 DB에서 삭제 |

> **cascade를 쓸 곳**: 부모 없이는 존재 의미가 없는 자식 Entity (Article→Claim, Claim→VerificationResult 등)

### fetch = FetchType.LAZY를 쓰는 이유

```java
// EAGER (기본값이 되면 안 됨)
@ManyToOne(fetch = FetchType.EAGER)
// → Member를 조회하면 연결된 모든 Session, Article, Claim까지 한꺼번에 DB 조회
// → 연결된 데이터가 많으면 심각한 성능 문제 발생

// LAZY (권장)
@ManyToOne(fetch = FetchType.LAZY)
// → Member를 조회할 때 Session은 가져오지 않음
// → session.getSomething()을 실제로 호출할 때만 추가 조회
// → 필요한 것만 가져오므로 성능에 훨씬 유리
```

**규칙**: `@ManyToOne`, `@OneToOne`은 기본값이 `EAGER`이므로 반드시 `fetch = FetchType.LAZY` 명시

---

## 8. 완성 예시 — Member Entity

아래 코드를 그대로 따라하면 됩니다.

```java
// com/truthscope/web/entity/Member.java
package com.truthscope.web.entity;

import com.truthscope.web.entity.enums.MemberRole;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity                          // (1) 이 클래스가 DB 테이블과 연결됨을 선언
@Table(name = "members")         // (2) DB 테이블명 명시
@Getter                          // (3) Lombok: 모든 필드의 getter 자동 생성
@NoArgsConstructor               // (4) Lombok: 기본 생성자 자동 생성 (JPA 필수)
public class Member extends BaseTimeEntity {    // (5) 공통 시간 필드 상속

    @Id                                                       // (6) PK 선언
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;
    // @GeneratedValue 없음! Supabase Auth가 UUID를 할당하므로 직접 세팅

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "nickname", length = 50)
    private String nickname;

    @Enumerated(EnumType.STRING)                             // (7) 문자열로 저장
    @Column(name = "role", nullable = false, length = 20)
    private MemberRole role = MemberRole.USER;               // (8) 기본값 설정

    // === 연관관계 (비소유 측, FK 없는 쪽) ===

    @OneToMany(mappedBy = "member")                          // (9) 비소유 측
    private List<AnalysisSession> sessions = new ArrayList<>();

    @OneToMany(mappedBy = "member")
    private List<UserReaction> reactions = new ArrayList<>();

    @OneToMany(mappedBy = "member")
    private List<ApiUsageLog> apiUsageLogs = new ArrayList<>();

    // === Builder 패턴 ===

    @Builder
    public Member(UUID id, String email, String nickname, MemberRole role) {
        this.id = id;
        this.email = email;
        this.nickname = nickname;
        this.role = (role != null) ? role : MemberRole.USER;
    }
}
```

> **왜 `@Builder`를 클래스 대신 생성자에 붙이나?**
> `@NoArgsConstructor`(기본 생성자)와 `@Builder`를 클래스에 동시에 쓰면 충돌이 납니다.
> 생성자에만 `@Builder`를 붙이면 두 어노테이션이 공존할 수 있습니다.

---

## 9. 이슈별 작성 Entity 목록

### BE #5 — 이정훈: 핵심 Entity 4개 + Enum 4개

#### AnalysisSession.java

```java
package com.truthscope.web.entity;

import com.truthscope.web.entity.enums.SessionStatus;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "analysis_sessions")
@Getter
@NoArgsConstructor
public class AnalysisSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // FK 소유자: member_id 컬럼이 이 테이블에 있음
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")          // NULL 허용 (비로그인 사용자도 분석 가능)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SessionStatus status = SessionStatus.PENDING;

    @Column(name = "requested_at")
    private LocalDateTime requestedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "total_score")
    private Short totalScore;                // SMALLINT → Short

    @Column(name = "coverage", length = 10)
    private String coverage;

    @Column(name = "tier1_count")
    private Short tier1Count;

    @Column(name = "tier2_count")
    private Short tier2Count;

    @Column(name = "tier3_count")
    private Short tier3Count;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    // 비소유 측: Article이 session_id FK를 갖고 있음
    @OneToOne(mappedBy = "session")
    private Article article;

    @Builder
    public AnalysisSession(Member member, SessionStatus status, LocalDateTime requestedAt) {
        this.member = member;
        this.status = (status != null) ? status : SessionStatus.PENDING;
        this.requestedAt = requestedAt;
    }
}
```

#### Article.java

```java
package com.truthscope.web.entity;

import com.truthscope.web.entity.enums.DomainType;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "articles")
@Getter
@NoArgsConstructor
public class Article extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // FK 소유자: session_id 컬럼이 이 테이블에 있음 (UNIQUE → 1:1)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", unique = true, nullable = false)
    private AnalysisSession session;

    @Column(name = "url", nullable = false, length = 2048)
    private String url;

    @Column(name = "title", length = 500)
    private String title;

    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    @Column(name = "lang", length = 10)
    private String lang;

    @Column(name = "domain", length = 255)
    private String domain;

    @Enumerated(EnumType.STRING)
    @Column(name = "domain_type", length = 20)
    private DomainType domainType;

    @Column(name = "extracted_at")
    private LocalDateTime extractedAt;

    // 자식 Claim을 함께 저장/삭제 (cascade)
    @OneToMany(mappedBy = "article", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Claim> claims = new ArrayList<>();

    @OneToMany(mappedBy = "article")
    private List<UserReaction> reactions = new ArrayList<>();

    @Builder
    public Article(AnalysisSession session, String url, String title, String body,
                   String lang, String domain, DomainType domainType, LocalDateTime extractedAt) {
        this.session = session;
        this.url = url;
        this.title = title;
        this.body = body;
        this.lang = lang;
        this.domain = domain;
        this.domainType = domainType;
        this.extractedAt = extractedAt;
    }
}
```

#### Claim.java

```java
package com.truthscope.web.entity;

import com.truthscope.web.entity.enums.Importance;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "claims")
@Getter
@NoArgsConstructor
public class Claim extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // FK 소유자: article_id 컬럼이 이 테이블에 있음
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_id", nullable = false)
    private Article article;

    @Column(name = "text", nullable = false, columnDefinition = "TEXT")
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(name = "importance", length = 10)
    private Importance importance;

    @Column(name = "sort_order")
    private Short sortOrder;

    // 비소유 측: VerificationResult가 claim_id FK를 갖고 있음
    @OneToOne(mappedBy = "claim", cascade = CascadeType.ALL, orphanRemoval = true)
    private VerificationResult verificationResult;

    @Builder
    public Claim(Article article, String text, Importance importance, Short sortOrder) {
        this.article = article;
        this.text = text;
        this.importance = importance;
        this.sortOrder = sortOrder;
    }
}
```

---

### BE #6 — 한지민: 검증 Entity 3개 + Enum 2개

#### VerificationResult.java

```java
package com.truthscope.web.entity;

import com.truthscope.web.entity.enums.Tier;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "verification_results")
@Getter
@NoArgsConstructor
public class VerificationResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // FK 소유자: claim_id 컬럼이 이 테이블에 있음 (UNIQUE → 1:1)
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "claim_id", unique = true, nullable = false)
    private Claim claim;

    @Enumerated(EnumType.STRING)
    @Column(name = "tier", nullable = false)
    private Tier tier;

    @Column(name = "label", nullable = false, length = 30)
    private String label;

    @Column(name = "score")
    private Short score;                   // NULL 허용 (0~100), SMALLINT → Short

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "disclaimer", columnDefinition = "TEXT")
    private String disclaimer;             // NULL 허용

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    // 자식 VerifySource를 함께 저장/삭제
    @OneToMany(mappedBy = "result", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<VerifySource> sources = new ArrayList<>();

    @Builder
    public VerificationResult(Claim claim, Tier tier, String label, Short score,
                               String reason, String disclaimer, LocalDateTime verifiedAt) {
        this.claim = claim;
        this.tier = tier;
        this.label = label;
        this.score = score;
        this.reason = reason;
        this.disclaimer = disclaimer;
        this.verifiedAt = verifiedAt;
    }
}
```

#### VerifySource.java

```java
package com.truthscope.web.entity;

import com.truthscope.web.entity.enums.VerifyStance;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "verify_sources")
@Getter
@NoArgsConstructor
public class VerifySource {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // FK 소유자: result_id 컬럼이 이 테이블에 있음
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "result_id", nullable = false)
    private VerificationResult result;

    @Column(name = "title", length = 500)
    private String title;

    @Column(name = "publisher", length = 200)
    private String publisher;

    @Column(name = "url", nullable = false, length = 2048)
    private String url;

    @Column(name = "rating", length = 50)
    private String rating;                  // NULL 허용

    @Enumerated(EnumType.STRING)
    @Column(name = "stance", length = 10)
    private VerifyStance stance;            // NULL 허용

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;                 // NULL 허용

    @Builder
    public VerifySource(VerificationResult result, String title, String publisher,
                        String url, String rating, VerifyStance stance, String summary) {
        this.result = result;
        this.title = title;
        this.publisher = publisher;
        this.url = url;
        this.rating = rating;
        this.stance = stance;
        this.summary = summary;
    }
}
```

#### FactcheckCache.java

```java
package com.truthscope.web.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "factcheck_cache")
@Getter
@NoArgsConstructor
public class FactcheckCache {
    // FactcheckCache는 BaseTimeEntity를 상속하지 않음
    // collected_at, expires_at은 감사 시간이 아닌 도메인 시간이므로 직접 선언

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "claim_text", nullable = false, columnDefinition = "TEXT")
    private String claimText;

    @Column(name = "source_org", nullable = false, length = 100)
    private String sourceOrg;

    @Column(name = "rating", nullable = false, length = 50)
    private String rating;

    @Column(name = "original_url", nullable = false, length = 2048)
    private String originalUrl;

    @Column(name = "language", length = 10)
    private String language = "ko";

    @Column(name = "collected_at")
    private LocalDateTime collectedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;       // NULL 허용 (만료 없음)

    // search_vector(TSVECTOR)는 DB 전용 컬럼 — JPA로 직접 매핑하지 않음
    // 전문 검색은 Native Query 또는 별도 처리

    @Builder
    public FactcheckCache(String claimText, String sourceOrg, String rating,
                          String originalUrl, String language,
                          LocalDateTime collectedAt, LocalDateTime expiresAt) {
        this.claimText = claimText;
        this.sourceOrg = sourceOrg;
        this.rating = rating;
        this.originalUrl = originalUrl;
        this.language = (language != null) ? language : "ko";
        this.collectedAt = collectedAt;
        this.expiresAt = expiresAt;
    }
}
```

---

### BE #7 — 정세린: 부가 Entity 2개 + Enum 1개

#### UserReaction.java

```java
package com.truthscope.web.entity;

import com.truthscope.web.entity.enums.ReactionType;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(
    name = "user_reactions",
    uniqueConstraints = {
        // UNIQUE(article_id, member_id, type) — 같은 사람이 같은 기사에 같은 반응 중복 불가
        @UniqueConstraint(columnNames = {"article_id", "member_id", "type"})
    }
)
@Getter
@NoArgsConstructor
public class UserReaction extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // FK 소유자: article_id 컬럼이 이 테이블에 있음
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_id", nullable = false)
    private Article article;

    // FK 소유자: member_id 컬럼이 이 테이블에 있음
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private ReactionType type;

    @Column(name = "reason", length = 500)
    private String reason;                 // NULL 허용

    @Builder
    public UserReaction(Article article, Member member, ReactionType type, String reason) {
        this.article = article;
        this.member = member;
        this.type = type;
        this.reason = reason;
    }
}
```

#### ApiUsageLog.java

```java
package com.truthscope.web.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "api_usage_logs")
@Getter
@NoArgsConstructor
public class ApiUsageLog {
    // BaseTimeEntity 미상속: 날짜별 집계 로그이므로 별도의 created_at 불필요

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // FK 소유자: member_id 컬럼이 이 테이블에 있음 (NULL 허용 — 비로그인 API 호출 가능)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @Column(name = "provider", nullable = false, length = 30)
    private String provider;               // 예: "openai", "google"

    @Column(name = "model", length = 50)
    private String model;                  // NULL 허용 (모델 무관한 API도 있음)

    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;           // DATE → LocalDate

    @Column(name = "request_count", nullable = false)
    private Integer requestCount = 0;

    @Column(name = "token_count", nullable = false)
    private Integer tokenCount = 0;

    @Builder
    public ApiUsageLog(Member member, String provider, String model,
                       LocalDate usageDate, Integer requestCount, Integer tokenCount) {
        this.member = member;
        this.provider = provider;
        this.model = model;
        this.usageDate = usageDate;
        this.requestCount = (requestCount != null) ? requestCount : 0;
        this.tokenCount = (tokenCount != null) ? tokenCount : 0;
    }
}
```

---

## 10. ERD 전체 관계 정리

```
Member (1) ──────────── (N) AnalysisSession
                              │
                             (1)
                              │
                           Article  ──── (N) Claim
                              │                │
                             (N)              (1)
                              │                │
                         UserReaction    VerificationResult
                                               │
                                              (N)
                                               │
                                          VerifySource

Member (1) ──── (N) UserReaction
Member (1) ──── (N) ApiUsageLog

FactcheckCache  ← 독립 테이블 (외래키 없음)
```

### 관계 매핑 요약표

| 관계 | FK 위치 | @JoinColumn 위치 | mappedBy 위치 |
|---|---|---|---|
| Member → AnalysisSession | analysis_sessions.member_id | AnalysisSession | Member |
| AnalysisSession → Article | articles.session_id | Article | AnalysisSession |
| Article → Claim | claims.article_id | Claim | Article |
| Claim → VerificationResult | verification_results.claim_id | VerificationResult | Claim |
| VerificationResult → VerifySource | verify_sources.result_id | VerifySource | VerificationResult |
| Article → UserReaction | user_reactions.article_id | UserReaction | Article |
| Member → UserReaction | user_reactions.member_id | UserReaction | Member |
| Member → ApiUsageLog | api_usage_logs.member_id | ApiUsageLog | Member |

---

## 11. Entity 작성 후 체크리스트

Entity 파일 하나를 완성할 때마다 아래 항목을 직접 확인하세요.

### 클래스 선언

- [ ] `@Entity` 어노테이션이 있는가?
- [ ] `@Table(name = "실제_테이블명")` 이 있는가?
- [ ] `@Getter`, `@NoArgsConstructor` (Lombok)가 있는가?
- [ ] BaseTimeEntity를 상속해야 하는 Entity인가? (created_at/updated_at이 DDL에 있으면 상속)

### PK 설정

- [ ] `@Id` 어노테이션이 있는가?
- [ ] Member Entity를 제외하고 `@GeneratedValue(strategy = GenerationType.UUID)`가 있는가?
- [ ] `@Column(name = "id", updatable = false, nullable = false)` 가 있는가?

### 일반 필드

- [ ] `@Column(name = "db_컬럼명")` 이 명시되어 있는가?
- [ ] `nullable = false` 가 필요한 NOT NULL 컬럼에 적용되어 있는가?
- [ ] TEXT 타입 컬럼에 `columnDefinition = "TEXT"` 가 있는가?
- [ ] SMALLINT 컬럼의 Java 타입이 `Short`인가?
- [ ] DATE 컬럼의 Java 타입이 `LocalDate`인가?
- [ ] TIMESTAMP 컬럼의 Java 타입이 `LocalDateTime`인가?

### Enum 필드

- [ ] `@Enumerated(EnumType.STRING)` 이 있는가? (`ORDINAL` 절대 금지)
- [ ] `@Column` 의 `length`가 DDL과 일치하는가?

### 연관관계

- [ ] FK를 가진 쪽(소유자)에 `@JoinColumn(name = "fk_컬럼명")` 이 있는가?
- [ ] FK가 없는 쪽(비소유자)에 `mappedBy = "상대방_필드명"` 이 있는가?
- [ ] `@ManyToOne`, `@OneToOne` 에 `fetch = FetchType.LAZY` 가 있는가?
- [ ] 부모-자식 관계에 `cascade = CascadeType.ALL` 이 적용되어 있는가?
- [ ] `@OneToMany` 컬렉션의 초기값이 `new ArrayList<>()`인가?

### Builder

- [ ] `@Builder`가 생성자 위에 붙어 있는가? (클래스 위가 아닌)
- [ ] Builder 생성자의 파라미터가 필수 필드만 포함하는가?

### 최종 확인

- [ ] 패키지 경로가 `com.truthscope.web.entity` 인가?
- [ ] Enum 클래스의 패키지 경로가 `com.truthscope.web.entity.enums` 인가?
- [ ] import 문에 오타나 누락이 없는가?
- [ ] `@EnableJpaAuditing` 이 메인 클래스에 추가되어 있는가? (팀 전체에서 한 번만 확인)

---

## 자주 하는 실수 모음

| 실수 | 원인 | 해결 |
|---|---|---|
| `created_at`이 null로 저장됨 | `@EnableJpaAuditing` 누락 | 메인 클래스에 추가 |
| Enum 저장 시 0, 1, 2 숫자로 저장됨 | `@Enumerated` 기본값이 ORDINAL | `@Enumerated(EnumType.STRING)` 명시 |
| N+1 문제 (쿼리가 너무 많이 나감) | `fetch = EAGER` 사용 | `fetch = FetchType.LAZY` 사용 |
| `mappedBy` 오류 | mappedBy에 컬럼명을 씀 | mappedBy에는 **Java 필드명**을 씀 |
| Builder와 NoArgsConstructor 충돌 | `@Builder`를 클래스에 붙임 | 생성자에만 `@Builder` 붙이기 |
| Member PK가 자동 생성됨 | `@GeneratedValue` 실수로 추가 | Member는 `@GeneratedValue` 제거 |
