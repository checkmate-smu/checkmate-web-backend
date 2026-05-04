# Backend DataSourceAdapter Scaffold Reference

> **목적**: BE #22 (DataSourceAdapter 인터페이스 + MockDataSourceAdapter) Week 2 구현 시 한지민이 그대로 따라 작성할 수 있도록 PM 사전 작성한 reference.
> **선행 의무**: 본 가이드의 "권고 시그니처 (§3)" finalize 전에 PM과 1회 협의. 본 인터페이스는 S3a/b 어댑터 + S7 GDELT 어댑터의 베이스라 시그니처 변경 시 후속 영향 큼 (이슈 본문 "주의사항" 참조).
> **이슈**: <https://github.com/truthscope-smu/truthscope-web-backend/issues/22>
> **관련 ADR**: [ADR-006 core/app 분리](../../../context/decisions/ADR-006-core-app-separation.md)

---

## 1. 개요

`DataSourceAdapter`는 외부 검증 데이터 소스(BigKinds / KOSIS / 정책브리핑 / GDELT 등)를 추상화하는 핵심 인터페이스다. ADR-006의 D1(OSS 공개) + D3(재현성) 원칙을 구조적으로 보장한다.

| 비전 | 설명 |
|---|---|
| OCP (개방-폐쇄) | `DataSourceAdapter`를 교체해도 `VerificationPipeline` 코드 수정 불필요 |
| OSS 단독 배포 | 인터페이스는 `core` 모듈 (Spring 의존 0) → 외부에서 `truthscope-verification-core` jar로 사용 가능 |
| 재현성 (D3) | 구현체별로 fixture 메서드 제공 → 외부 API 없이도 동일 결과 재현 |
| 테스트 용이성 | `MockDataSourceAdapter`로 통합 테스트 + Spring Context 없이 단위 테스트 |

---

## 2. 모듈 배치 (BE #23 Phase 1 후속 정합)

| 심볼 | 모듈 | 패키지 |
|---|---|---|
| `DataSourceAdapter` (interface) | **core** | `com.truthscope.web.adapter.datasource` |
| `AdapterQuery` (record) | **core** | 동일 |
| `RawResponse` (record) | **core** | 동일 |
| `ExtractedClaim` (record) | **core** | 동일 |
| `HealthStatus` (record) | **core** | 동일 |
| `AdapterMetadata` (record) | **core** | 동일 |
| `MockDataSourceAdapter` (구현) | **app** | `com.truthscope.web.adapter.datasource` |
| 테스트 | **app** | `com.truthscope.web.adapter.datasource` (test sourceSet) |

> **이유**: ADR-006에 따라 인터페이스 + 도메인 record는 Spring 무관 → core. HTTP 호출/Spring DI/외부 API 키 사용은 app.
> **생성 명령**: 패키지 디렉토리는 미존재 — 신규 작성 시 `core/src/main/java/com/truthscope/web/adapter/datasource/` 직접 생성.

---

## 3. 권고 시그니처 (PM 협의 후 finalize)

본 시그니처는 PM 사전 작성 권고안이다. **Week 2 코드 진입 전 PM과 1회 협의 후 finalize**한다 (이슈 본문 "주의사항" 강제 사항).

### 3.1 인터페이스

```java
package com.truthscope.web.adapter.datasource;

import java.util.List;

/**
 * 외부 검증 데이터 소스 추상화 (Tier 1 cascade의 1차 진입점).
 *
 * <p>구현 예: {@link com.truthscope.web.adapter.datasource.MockDataSourceAdapter} (app),
 * 향후 BigKindsAdapter / KosisAdapter / PolicyBriefingAdapter / GdeltAdapter (모두 app).
 *
 * <p>본 인터페이스 + 의존 record는 Spring 무관 (core 모듈). HTTP/DI/API 키 의존은 구현체에서 처리.
 */
public interface DataSourceAdapter {

  /**
   * 외부 데이터 소스 호출.
   *
   * @param query 검색 쿼리 (keyword, lang, dateRange 등)
   * @return raw 응답 (body string + status + format)
   * @throws IllegalArgumentException query가 null 또는 invalid invariant 위반
   * @throws java.io.IOException 외부 네트워크 오류
   */
  RawResponse fetch(AdapterQuery query) throws java.io.IOException;

  /**
   * raw 응답 → 정규화된 Claim 리스트.
   *
   * <p>구현체는 JSON/XML/HTML 등 source format 구애받지 않고 ExtractedClaim record로 정규화한다.
   *
   * @param rawResponse {@link #fetch(AdapterQuery)}의 반환값
   * @return ExtractedClaim 리스트 (응답이 비어있으면 empty list)
   * @throws IllegalArgumentException rawResponse가 null 또는 parse 불가
   */
  List<ExtractedClaim> parse(RawResponse rawResponse);

  /**
   * 어댑터 가용성 체크 (외부 API health endpoint 호출 또는 ping).
   *
   * @return UP/DOWN 상태 + latencyMs
   */
  HealthStatus health();

  /**
   * 어댑터 식별 메타데이터.
   *
   * @return 이름/버전/유료여부/제공자 등
   */
  AdapterMetadata metadata();

  /**
   * 테스트용 고정 fixture (외부 API 호출 없이 결과 재현).
   *
   * <p>D3 재현성 원칙 — core 단독 검증 시 본 메서드로 deterministic 결과 보장.
   *
   * @return 고정 ExtractedClaim 리스트 (구현체별 5건 이상 권장)
   */
  List<ExtractedClaim> fixture();
}
```

### 3.2 의존 record (모두 core)

```java
package com.truthscope.web.adapter.datasource;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 검색 쿼리 값 객체.
 *
 * @param keyword 검색 키워드 (null/blank 금지)
 * @param lang ISO 639-1 (예: "ko", "en")
 * @param fromDate 검색 시작일 (inclusive, nullable = 전체)
 * @param toDate 검색 종료일 (inclusive, nullable = 전체)
 * @param limit 최대 결과 건수 (1-100)
 */
public record AdapterQuery(String keyword, String lang, LocalDate fromDate, LocalDate toDate, int limit) {
  public AdapterQuery {
    if (keyword == null || keyword.isBlank()) {
      throw new IllegalArgumentException("keyword는 null/blank 금지");
    }
    if (limit < 1 || limit > 100) {
      throw new IllegalArgumentException("limit은 1-100 범위");
    }
  }
}
```

```java
package com.truthscope.web.adapter.datasource;

/**
 * 외부 API raw 응답 값 객체.
 *
 * @param body 응답 본문 (JSON/XML/HTML 문자열)
 * @param statusCode HTTP status
 * @param format "JSON" / "XML" / "HTML"
 */
public record RawResponse(String body, int statusCode, String format) {
  public RawResponse {
    if (body == null) {
      throw new IllegalArgumentException("body는 null 금지 (empty string은 허용)");
    }
    if (format == null || format.isBlank()) {
      throw new IllegalArgumentException("format 필수");
    }
  }
}
```

```java
package com.truthscope.web.adapter.datasource;

import java.time.Instant;

/**
 * 정규화된 Claim 값 객체.
 *
 * @param claimText Claim 본문 (null/blank 금지)
 * @param sourceUrl 출처 URL (http(s), nullable = unknown)
 * @param lang ISO 639-1
 * @param extractedAt 추출 시각
 */
public record ExtractedClaim(String claimText, String sourceUrl, String lang, Instant extractedAt) {
  public ExtractedClaim {
    if (claimText == null || claimText.isBlank()) {
      throw new IllegalArgumentException("claimText는 null/blank 금지");
    }
    if (extractedAt == null) {
      throw new IllegalArgumentException("extractedAt 필수");
    }
  }
}
```

```java
package com.truthscope.web.adapter.datasource;

import java.time.Instant;

/**
 * 어댑터 health 상태.
 *
 * @param status "UP" / "DOWN"
 * @param latencyMs ping 응답 시간 (ms, DOWN이면 -1)
 * @param checkedAt 체크 시각
 */
public record HealthStatus(String status, long latencyMs, Instant checkedAt) {
  public HealthStatus {
    if (!"UP".equals(status) && !"DOWN".equals(status)) {
      throw new IllegalArgumentException("status는 UP/DOWN 둘 중 하나");
    }
  }

  public static HealthStatus up(long latencyMs) {
    return new HealthStatus("UP", latencyMs, Instant.now());
  }

  public static HealthStatus down() {
    return new HealthStatus("DOWN", -1, Instant.now());
  }
}
```

```java
package com.truthscope.web.adapter.datasource;

/**
 * 어댑터 식별 메타데이터.
 *
 * @param name 어댑터 이름 (예: "Mock", "BigKinds", "KOSIS")
 * @param version 시맨틱 버전 (예: "1.0.0")
 * @param isPaid 유료 API 여부
 * @param provider 제공자 (예: "internal", "google", "data.go.kr")
 */
public record AdapterMetadata(String name, String version, boolean isPaid, String provider) {
  public AdapterMetadata {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name 필수");
    }
    if (version == null || !version.matches("\\d+\\.\\d+\\.\\d+")) {
      throw new IllegalArgumentException("version은 시맨틱 버전 (예: 1.0.0)");
    }
  }
}
```

### 3.3 협의 사항 체크리스트 (PM과 finalize 시)

- [ ] `fetch`가 `IOException` throw 적정한가, 또는 sealed `FetchException` wrapper로 래핑할 것인가
- [ ] `AdapterQuery.lang`이 String인가, 또는 enum (`Language`)인가
- [ ] `RawResponse.format`이 String인가, 또는 enum (`ResponseFormat`)인가
- [ ] `ExtractedClaim`에 추가 필드 필요한가 (예: `confidenceScore`, `originalLang`)
- [ ] `health`가 동기인가, async (`CompletableFuture<HealthStatus>`)인가 — Mock은 동기로 충분
- [ ] `fixture`가 항상 non-empty가 invariant인가, 또는 빈 리스트 허용인가

---

## 4. MockDataSourceAdapter 구현 골격

```java
package com.truthscope.web.adapter.datasource;

import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 테스트/로컬 환경용 목 어댑터.
 *
 * <p>외부 API 호출 없이 fixture 5건 반환. 통합 테스트 + 로컬 개발 시 사용.
 */
@Component
public class MockDataSourceAdapter implements DataSourceAdapter {

  private static final List<ExtractedClaim> FIXTURES =
      List.of(
          new ExtractedClaim(
              "한국 정부의 경제 성장률 전망은 2.1%이다.",
              "https://example.com/kor-economy",
              "ko",
              Instant.parse("2026-05-01T00:00:00Z")),
          new ExtractedClaim(
              "OECD 평균 실업률은 5.2%이다.",
              "https://example.com/oecd-unemployment",
              "ko",
              Instant.parse("2026-05-01T00:00:00Z")),
          new ExtractedClaim(
              "2025년 한국 출생률은 0.78이다.",
              "https://example.com/birth-rate",
              "ko",
              Instant.parse("2026-05-01T00:00:00Z")),
          new ExtractedClaim(
              "Korea ranked 7th in global GDP 2025.",
              "https://example.com/gdp-rank",
              "en",
              Instant.parse("2026-05-01T00:00:00Z")),
          new ExtractedClaim(
              "한국의 65세 이상 인구 비율은 18.4%이다.",
              "https://example.com/demographics",
              "ko",
              Instant.parse("2026-05-01T00:00:00Z")));

  private static final AdapterMetadata METADATA =
      new AdapterMetadata("Mock", "1.0.0", false, "internal");

  @Override
  public RawResponse fetch(AdapterQuery query) {
    if (query == null) {
      throw new IllegalArgumentException("query는 null 금지");
    }
    return new RawResponse("{\"mock\":true}", 200, "JSON");
  }

  @Override
  public List<ExtractedClaim> parse(RawResponse rawResponse) {
    if (rawResponse == null) {
      throw new IllegalArgumentException("rawResponse는 null 금지");
    }
    if (rawResponse.body().isBlank()) {
      return List.of();
    }
    return FIXTURES;
  }

  @Override
  public HealthStatus health() {
    return HealthStatus.up(0L);
  }

  @Override
  public AdapterMetadata metadata() {
    return METADATA;
  }

  @Override
  public List<ExtractedClaim> fixture() {
    return FIXTURES;
  }
}
```

> **주의**: `@Component`는 Spring DI 어노테이션이라 Mock은 app 모듈에 둔다. core 모듈에 두면 컴파일 실패 (core/build.gradle에 spring-context 없음).

---

## 5. 테스트 5+ cases 골격

위치: `app/src/test/java/com/truthscope/web/adapter/datasource/MockDataSourceAdapterTest.java`

```java
package com.truthscope.web.adapter.datasource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("MockDataSourceAdapter")
class MockDataSourceAdapterTest {

  private MockDataSourceAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new MockDataSourceAdapter();
  }

  @Nested
  @DisplayName("fetch")
  class Fetch {

    @Test
    @DisplayName("정상 query → RawResponse 200/JSON 반환")
    void happyPath() {
      AdapterQuery query =
          new AdapterQuery("한국 경제", "ko", LocalDate.now().minusDays(7), LocalDate.now(), 10);

      RawResponse response = adapter.fetch(query);

      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.format()).isEqualTo("JSON");
      assertThat(response.body()).isNotBlank();
    }

    @Test
    @DisplayName("null query → IllegalArgumentException")
    void nullQuery() {
      assertThatThrownBy(() -> adapter.fetch(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("query");
    }
  }

  @Nested
  @DisplayName("parse")
  class Parse {

    @Test
    @DisplayName("empty body → empty list")
    void emptyResponse() {
      RawResponse empty = new RawResponse("", 200, "JSON");

      List<ExtractedClaim> claims = adapter.parse(empty);

      assertThat(claims).isEmpty();
    }

    @Test
    @DisplayName("정상 body → fixture 5건 반환")
    void parseFixtures() {
      RawResponse response = new RawResponse("{\"mock\":true}", 200, "JSON");

      List<ExtractedClaim> claims = adapter.parse(response);

      assertThat(claims).hasSizeGreaterThanOrEqualTo(5);
      assertThat(claims).allMatch(c -> !c.claimText().isBlank());
    }

    @Test
    @DisplayName("null rawResponse → IllegalArgumentException")
    void nullResponse() {
      assertThatThrownBy(() -> adapter.parse(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("rawResponse");
    }
  }

  @Nested
  @DisplayName("health")
  class Health {

    @Test
    @DisplayName("Mock은 항상 UP")
    void alwaysUp() {
      HealthStatus status = adapter.health();

      assertThat(status.status()).isEqualTo("UP");
      assertThat(status.latencyMs()).isGreaterThanOrEqualTo(0);
      assertThat(status.checkedAt()).isNotNull();
    }
  }

  @Nested
  @DisplayName("metadata")
  class Metadata {

    @Test
    @DisplayName("name=Mock, isPaid=false")
    void mockMetadata() {
      AdapterMetadata metadata = adapter.metadata();

      assertThat(metadata.name()).isEqualTo("Mock");
      assertThat(metadata.version()).matches("\\d+\\.\\d+\\.\\d+");
      assertThat(metadata.isPaid()).isFalse();
      assertThat(metadata.provider()).isEqualTo("internal");
    }
  }

  @Nested
  @DisplayName("fixture")
  class Fixture {

    @Test
    @DisplayName("5건 이상 반환 (수용 기준)")
    void atLeast5Claims() {
      List<ExtractedClaim> claims = adapter.fixture();

      assertThat(claims).hasSizeGreaterThanOrEqualTo(5);
    }

    @Test
    @DisplayName("모든 fixture가 invariant 만족 (claimText non-blank)")
    void allInvariantSatisfied() {
      List<ExtractedClaim> claims = adapter.fixture();

      assertThat(claims).allMatch(c -> !c.claimText().isBlank() && c.extractedAt() != null);
    }
  }
}
```

> **테스트 패턴**: `@Nested` 그룹화 + AssertJ + DisplayName 한국어. AbstractIntegrationTest 상속 불필요 (Spring Context 미사용).
> **수용 기준 매핑**: 위 8 cases가 BE #22 "단위 테스트 5+ cases (happy / null query / empty response / parse error / health fail)" 충족 + 추가 metadata / fixture invariant.

---

## 6. ArchUnit 룰 정합

기존 `app/src/test/java/com/truthscope/web/architecture/ArchitectureTest.java`의 `entityShouldNotExposeSetters` 룰은 entity 패키지에만 적용. 본 어댑터는 별도 룰 불필요 (record는 immutable, MockDataSourceAdapter는 setter 없음).

> **F9 (HANDOFF 박제)**: core ArchUnit 룰 (`com.truthscope.web.entity..` → `com.truthscope.web.controller..` 의존 금지 등) 추가는 별도 30분 작업으로 deferred.

---

## 7. 구현 단계 (한지민 Week 2 follow)

> **Week 1 (5/4~5/10) 선행**: API key 신청 (S3a 정책뉴스 + S3a' 보도자료 + S3b KOSIS) — 이슈 본문 "Week 1 할 일" 참조. **본 가이드는 Week 2 코드 진입 단계 안내.**

### Step 1 — 시그니처 협의 (PM과 1회, 30분)

- 본 가이드 §3.3 체크리스트 6건 결정
- 변경 사항이 있으면 본 가이드 patch + commit

### Step 2 — 브랜치 + core 인터페이스 (45분)

```bash
cd checkmate-web-backend          # 또는 truthscope-web-backend (rename 후)
git checkout dev && git pull origin dev
git checkout -b feat/S2-01-datasource-adapter

# core 패키지 신설
mkdir -p core/src/main/java/com/truthscope/web/adapter/datasource

# 본 가이드 §3.1, §3.2 코드를 그대로 복사
# 파일: DataSourceAdapter.java + AdapterQuery.java + RawResponse.java
#       + ExtractedClaim.java + HealthStatus.java + AdapterMetadata.java
```

### Step 3 — app 구현 (15분)

```bash
mkdir -p app/src/main/java/com/truthscope/web/adapter/datasource

# 본 가이드 §4 코드를 그대로 복사
# 파일: MockDataSourceAdapter.java
```

### Step 4 — 테스트 (30분)

```bash
mkdir -p app/src/test/java/com/truthscope/web/adapter/datasource

# 본 가이드 §5 코드를 그대로 복사
# 파일: MockDataSourceAdapterTest.java
```

### Step 5 — 로컬 검증 (15분)

```bash
./gradlew :core:build
./gradlew :app:spotlessApply
./gradlew :app:checkstyleMain
./gradlew :app:spotbugsMain
./gradlew :app:test --tests "*MockDataSourceAdapter*"
./gradlew :app:test
./gradlew :app:build
```

전부 PASS 후 commit:

```bash
git add core app
git commit -m "$(cat <<'EOF'
✨feat(adapter): DataSourceAdapter 인터페이스 + MockDataSourceAdapter (S2-01)

- core: DataSourceAdapter interface + 5 record (AdapterQuery / RawResponse /
  ExtractedClaim / HealthStatus / AdapterMetadata) — Spring 무관, OSS 배포 가능
- app: MockDataSourceAdapter @Component (fixture 5건) — 통합 테스트/로컬 dev 용도
- test: 9 cases 통과 (fetch happy/null + parse empty/null/fixtures + health + metadata + fixture invariant 2)

closes #22

Co-authored-by: gs07103 <gwonseok02@gmail.com>
EOF
)"
```

### Step 6 — PR + CodeRabbit (~30분 대기)

```bash
git push -u origin feat/S2-01-datasource-adapter
gh pr create --base dev --title "✨feat(adapter): DataSourceAdapter 인터페이스 + MockDataSourceAdapter (S2-01)" --body "..."
```

PR description에 학습 가치 1줄 명시 (이슈 수용 기준):

> Spring 무관 인터페이스(core) + Spring DI 구현(app) 분리로 ADR-006 OCP/OSS 비전을 첫 어댑터로 실증.

CodeRabbit Critical 0이면 PM에게 코멘트로 머지 승인 요청.

---

## 8. 수용 기준 매핑 (BE #22 issue 본문)

| 수용 기준 | 본 가이드 매핑 |
|---|---|
| DataSourceAdapter 인터페이스 메서드 5+ 정의 | §3.1 (5개: fetch/parse/health/metadata/fixture) |
| MockDataSourceAdapter 단위 테스트 PASS (5+ cases) | §5 (9 cases) |
| ArchUnit 룰 PASS (entity setter 금지) | §6 (기존 룰 영향 없음) |
| `./gradlew spotlessApply / checkstyleMain / spotbugsMain / test / build` 모두 PASS | §7 Step 5 |
| PR description에 학습 가치 1줄 명시 | §7 Step 6 (위 예시) |
| CodeRabbit 리뷰 통과 (Critical 0) | §7 Step 6 후 |

---

## 9. 후속 어댑터 작성 시 (S3a/b BigKinds/KOSIS, S7 GDELT)

본 인터페이스가 베이스. 구현체별로 다음 부분만 변경:

- `fetch`: 외부 API HTTP 호출 (Apache HttpClient 5 또는 Spring `RestClient`)
- `parse`: source format별 파서 (Jackson `ObjectMapper` for JSON / `Jsoup` for HTML)
- `metadata`: 어댑터별 식별 정보 (BigKinds는 `isPaid=true`, GDELT는 `isPaid=false`)
- `fixture`: 어댑터별 5+ 고정 응답
- API 키: `application-local.yml` (Git 금지) → `@ConfigurationProperties` 또는 `@Value`
- SsrfGuard 적용: 어댑터가 외부 URL을 fetch할 때 `SsrfGuard.validate(url)` 호출 의무 (Phase 20 룰)

> **신규 어댑터 PR 시 본 가이드를 reference로 인용**. 시그니처 변경은 본 가이드 patch + 모든 어댑터 일괄 갱신.

---

## 10. Reference

- 이슈: <https://github.com/truthscope-smu/truthscope-web-backend/issues/22>
- ADR: [ADR-006 core/app 분리](../../../context/decisions/ADR-006-core-app-separation.md)
- DDD/TDD 패턴: [backend-ddd-tdd-template.md](backend-ddd-tdd-template.md)
- 테스트 표준: [testing-standard.md](testing-standard.md)
- 코드 reference: `core/src/main/java/com/truthscope/web/entity/Article.java` (정적 팩토리 패턴)
- BE #23 Phase 1 머지: PR #41 (`64d019f`) — core/app 모듈 분리 직후 첫 어댑터
- 워크스페이스 spike: `truthscope-web/spike/` (PM 로컬, 데이터 소스 정합성 측정 결과 박제)
