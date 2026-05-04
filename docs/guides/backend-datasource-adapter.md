# Backend DataSourceAdapter Scaffold Reference

> **목적**: BE #22 Week 2 코드 진입(5/11~5/17) 시 본 가이드 §3-§7만 보고 그대로 작성 → 4시간 안에 PR 머지 가능한 self-contained reference. 한지민(BE 담당)용.
> **이슈**: <https://github.com/truthscope-smu/truthscope-web-backend/issues/22>
> **변경 필요 시**: 본 가이드 시그니처(§3) 또는 모듈 배치(§2)를 변경하려면 PM(권석)에게 코멘트 핑. S3a/b/S7 후속 어댑터 베이스라 수정 시 후속 영향 큼.

---

## 0. 용어 1분 정리

본 가이드/이슈에 등장하는 본 팀 약어. 처음 보면 본 섹션만 읽고 진행:

| 약어 | 풀이 |
|---|---|
| **BE #22 / S2-01** | 본 이슈. Sprint 2의 첫 번째 이슈 (S2-01 = Sprint 2 Issue 01) |
| **BE #23** | 직전 머지된 이슈 — Gradle `core` / `app` 모듈 분리 (PR #41/#42, 2026-05-04 main 반영) |
| **S3a / S3b / S7** | 후속 데이터 소스 어댑터 이슈 — S3a 정책브리핑, S3b KOSIS, S7 GDELT. 본 인터페이스를 베이스로 작성됨 |
| **ADR** | Architecture Decision Record. `truthscope-web/context/decisions/ADR-NNN-*.md`에 결정 + 근거 박제. **ADR-006** = core/app 모듈 분리 결정 |
| **D1 / D3** | ADR-006의 핵심 원칙. **D1** = "core 로직을 OSS로 공개해 외부에서 단독 사용 가능", **D3** = "같은 입력 → 같은 출력 재현성" |
| **OCP** | Open-Closed Principle. "새 어댑터 추가해도 기존 파이프라인 수정 최소화" — 인터페이스 추상화의 핵심 가치 |
| **OSS** | Open Source Software. core 모듈을 jar로 배포해 외부 개발자가 사용 가능하게 만드는 비전 |
| **core / app 모듈** | BE #23로 분리된 Gradle subproject. **core** = Spring DI/Web 어노테이션 없음(jar 단독 배포 가능), **app** = Spring Boot 웹앱(컨트롤러/DI/HTTP) |
| **Tier 1 cascade** | 3-Tier 검증 파이프라인의 1차 (외부 데이터소스 직접 매칭). 본 인터페이스가 Tier 1의 진입점 추상화 |
| **SsrfGuard** | Server-Side Request Forgery 차단기. 외부 URL을 fetch할 때 사설 IP/내부망으로의 요청을 막는다. Phase 20에서 도입(`.plans/20-ssrf-blocker/`). 후속 어댑터에서 외부 URL 호출 시 의무 호출 |
| **DDD aggregate** | Domain-Driven Design의 "aggregate root" 패턴. invariant(불변 조건)을 정적 팩토리에서 검증, setter 금지. Phase 21 도입(Article entity reference) |
| **fixture** | 테스트용 고정 데이터. 외부 API 호출 없이 동일 결과 재현 (D3 원칙) |

---

## 1. 본 어댑터의 비전 (왜 만드나)

`DataSourceAdapter`는 외부 검증 데이터 소스(BigKinds / KOSIS / 정책브리핑 / GDELT 등)를 추상화하는 핵심 인터페이스다.

| 비전 | 설명 |
|---|---|
| OCP | `DataSourceAdapter` 구현체를 추가/교체해도 `VerificationPipeline` 코드 수정 불필요 |
| OSS 단독 배포 | 인터페이스를 `core` 모듈에 두면 (Spring DI 없음) 외부에서 `truthscope-verification-core.jar` 단독 사용 가능 |
| 재현성 (D3) | 구현체별 `fixture()` 메서드 → 외부 API 없이 deterministic 결과 |
| 테스트 용이성 | `MockDataSourceAdapter`로 통합 테스트 + Spring Context 없이 단위 테스트 |

---

## 2. 모듈 배치

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

**왜 이렇게**:
- core 모듈은 `spring-data-jpa` API 의존만 가지고 있고 Spring DI/Web 어노테이션은 없음 (BE #23 Phase 1 결정 — 메모리 `project_phase23_be_done.md` 참조). `@Component` 같은 DI 어노테이션을 쓰는 코드는 core에 못 둠 → app으로
- record + interface는 Spring 무관이라 core에 그대로 둘 수 있음

**디렉토리 신설**: 패키지 디렉토리는 미존재 — 신규 작성 시 직접 생성.
- `core/src/main/java/com/truthscope/web/adapter/datasource/`
- `app/src/main/java/com/truthscope/web/adapter/datasource/`
- `app/src/test/java/com/truthscope/web/adapter/datasource/`

---

## 3. 확정 시그니처 (Week 2 구현 기준)

> 본 시그니처는 PM이 BE #22 이슈 본문 + ADR-006 비전 + 후속 어댑터 (S3a/b/S7) 호환성을 검토해 finalize한 결정. **변경 필요 시 PM 핑** (체크리스트 §3.3 참조).

### 3.1 인터페이스

`core/src/main/java/com/truthscope/web/adapter/datasource/DataSourceAdapter.java`

```java
package com.truthscope.web.adapter.datasource;

import java.io.IOException;
import java.util.List;

/**
 * 외부 검증 데이터 소스 추상화 (Tier 1 cascade의 1차 진입점).
 *
 * <p>구현 예: MockDataSourceAdapter (app, 테스트/로컬 dev 용도). 향후 BigKindsAdapter /
 * KosisAdapter / PolicyBriefingAdapter / GdeltAdapter (모두 app 모듈).
 *
 * <p>본 인터페이스 + 의존 record는 Spring 무관 (core 모듈에 위치). HTTP/DI/API 키
 * 의존은 구현체에서 처리한다.
 */
public interface DataSourceAdapter {

  /**
   * 외부 데이터 소스 호출.
   *
   * @param query 검색 쿼리 (keyword/lang/dateRange 등)
   * @return raw 응답 (body string + status + format)
   * @throws IllegalArgumentException query가 null
   * @throws IOException 외부 네트워크 오류
   */
  RawResponse fetch(AdapterQuery query) throws IOException;

  /**
   * raw 응답을 ExtractedClaim 리스트로 정규화.
   *
   * <p>구현체는 JSON/XML/HTML 등 source format에 구애받지 않고 ExtractedClaim record로 정규화한다.
   *
   * @param rawResponse fetch 반환값
   * @return ExtractedClaim 리스트 (응답이 비어있으면 빈 리스트)
   * @throws IllegalArgumentException rawResponse가 null
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
   * 테스트용 고정 fixture (외부 API 호출 없이 결과 재현 — D3 재현성).
   *
   * @return 고정 ExtractedClaim 리스트 (구현체별 5건 이상)
   */
  List<ExtractedClaim> fixture();
}
```

### 3.2 의존 record (모두 core 모듈)

`core/src/main/java/com/truthscope/web/adapter/datasource/AdapterQuery.java`

```java
package com.truthscope.web.adapter.datasource;

import java.time.LocalDate;

/**
 * 검색 쿼리 값 객체.
 *
 * <p>MVP 검증 범위: keyword/limit. lang/fromDate/toDate는 구현체별 의미가 다를 수 있어 invariant 강제하지
 * 않고 nullable 허용 (ISO 639-1 권장 / null=제한 없음).
 *
 * @param keyword 검색 키워드 (null/blank 금지)
 * @param lang ISO 639-1 권장 (예: "ko", "en"), nullable 허용
 * @param fromDate 검색 시작일 (inclusive, nullable=전체)
 * @param toDate 검색 종료일 (inclusive, nullable=전체)
 * @param limit 최대 결과 건수 (1-100)
 */
public record AdapterQuery(
    String keyword, String lang, LocalDate fromDate, LocalDate toDate, int limit) {
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

`core/src/main/java/com/truthscope/web/adapter/datasource/RawResponse.java`

```java
package com.truthscope.web.adapter.datasource;

/**
 * 외부 API raw 응답 값 객체.
 *
 * @param body 응답 본문 (JSON/XML/HTML 문자열, 빈 문자열 허용)
 * @param statusCode HTTP status
 * @param format "JSON" / "XML" / "HTML" (대소문자 구별, 후속에서 enum 승격 검토)
 */
public record RawResponse(String body, int statusCode, String format) {
  public RawResponse {
    if (body == null) {
      throw new IllegalArgumentException("body는 null 금지 (빈 문자열은 허용)");
    }
    if (format == null || format.isBlank()) {
      throw new IllegalArgumentException("format 필수");
    }
  }
}
```

`core/src/main/java/com/truthscope/web/adapter/datasource/ExtractedClaim.java`

```java
package com.truthscope.web.adapter.datasource;

import java.time.Instant;

/**
 * 정규화된 Claim 값 객체.
 *
 * <p>MVP 검증 범위: claimText/extractedAt 필수. sourceUrl은 nullable 허용 (외부 fixture에서 출처
 * 부재할 수 있음), lang은 ISO 639-1 권장이지만 invariant 미강제.
 *
 * @param claimText Claim 본문 (null/blank 금지)
 * @param sourceUrl 출처 URL (http(s) 권장, nullable)
 * @param lang ISO 639-1 (예: "ko", "en")
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

`core/src/main/java/com/truthscope/web/adapter/datasource/HealthStatus.java`

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
    if (checkedAt == null) {
      throw new IllegalArgumentException("checkedAt 필수");
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

`core/src/main/java/com/truthscope/web/adapter/datasource/AdapterMetadata.java`

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

### 3.3 PM 결정값 (변경 시 PM 핑)

| 결정 사항 | 값 | 변경 영향 |
|---|---|---|
| `fetch` 예외 | `IOException` throw + `IllegalArgumentException`(null query) | 후속 어댑터가 같은 시그니처 따라야 함 |
| `AdapterQuery.lang` 타입 | `String` (ISO 639-1 권장, invariant 미강제) | enum 승격 시 모든 어댑터 호출부 수정 |
| `RawResponse.format` 타입 | `String` ("JSON"/"XML"/"HTML") | 동상 |
| `ExtractedClaim` 필드 | text/sourceUrl/lang/extractedAt 4개 (MVP) | 추가 필드 시 fixture 5건 모두 갱신 |
| `health` 동기/async | **동기** (Mock은 즉시, 외부 어댑터는 timeout 짧게) | async 전환 시 cascade orchestrator 영향 |
| `fixture` 최소 건수 | **5건** (수용 기준 정합) | 변경 시 본 가이드 + 이슈 본문 동시 수정 |

---

## 4. MockDataSourceAdapter 구현 코드

`app/src/main/java/com/truthscope/web/adapter/datasource/MockDataSourceAdapter.java`

```java
package com.truthscope.web.adapter.datasource;

import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 테스트/로컬 환경용 목 어댑터.
 *
 * <p>외부 API 호출 없이 fixture 5건 반환. 통합 테스트 + 로컬 개발 시 사용.
 *
 * <p>{@code @Component}는 Spring DI 어노테이션이라 본 클래스는 app 모듈에만 위치 가능 (core에는
 * spring-context 없음).
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

---

## 5. 테스트 코드 (10 cases)

위치: `app/src/test/java/com/truthscope/web/adapter/datasource/MockDataSourceAdapterTest.java`

수용 기준 "5+ cases" 초과 — happy/null query/empty response/parse null + health UP/DOWN factory + metadata/fixture invariant 등 10건.

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
    @DisplayName("Mock adapter는 UP")
    void mockAdapterUp() {
      HealthStatus status = adapter.health();

      assertThat(status.status()).isEqualTo("UP");
      assertThat(status.latencyMs()).isGreaterThanOrEqualTo(0);
      assertThat(status.checkedAt()).isNotNull();
    }

    @Test
    @DisplayName("HealthStatus.down() factory → DOWN/-1ms")
    void downFactory() {
      HealthStatus down = HealthStatus.down();

      assertThat(down.status()).isEqualTo("DOWN");
      assertThat(down.latencyMs()).isEqualTo(-1);
    }
  }

  @Nested
  @DisplayName("metadata")
  class Metadata {

    @Test
    @DisplayName("name=Mock, isPaid=false, provider=internal")
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
    @DisplayName("모든 fixture가 invariant 만족 (claimText non-blank + extractedAt non-null)")
    void allInvariantSatisfied() {
      List<ExtractedClaim> claims = adapter.fixture();

      assertThat(claims).allMatch(c -> !c.claimText().isBlank() && c.extractedAt() != null);
    }
  }
}
```

> **테스트 패턴**: `@Nested`로 메서드별 그룹화 + AssertJ + 한국어 `@DisplayName`. **`AbstractIntegrationTest` 상속 불필요** — Spring Context 안 띄우는 순수 단위 테스트.

---

## 6. ArchUnit 룰

기존 `app/src/test/java/com/truthscope/web/architecture/ArchitectureTest.java`의 `entityShouldNotExposeSetters` 룰은 entity 패키지에만 적용. 본 어댑터는 record(immutable) + Mock(setter 없음)이라 별도 룰 추가 불필요.

> 참고: core 모듈 자체에 ArchUnit 룰(`com.truthscope.web.entity..` → `com.truthscope.web.controller..` 의존 금지 등)은 별도 30분 작업으로 deferred (HANDOFF F9). Week 2 진입 시 영향 없음.

---

## 7. 한지민 Week 2 follow (6단계, ~2.5h)

> **Week 1 (5/4~5/10) 선행**: API key 신청 (S3a 정책뉴스 + S3a' 보도자료 + S3b KOSIS) — 이슈 본문 "Week 1 할 일" 참조. **본 단계는 Week 2 코드 진입 후 따라 한다.**

### Step 1 — 브랜치 생성 (5분)

PowerShell 또는 Git Bash에서:

```bash
cd truthscope-web-backend
git checkout dev
git pull origin dev
git checkout -b feat/S2-01-datasource-adapter
```

### Step 2 — core 인터페이스 + record 6개 (45분)

```bash
mkdir -p core/src/main/java/com/truthscope/web/adapter/datasource
```

본 가이드 §3.1, §3.2 코드를 그대로 복사:
- `DataSourceAdapter.java`
- `AdapterQuery.java`
- `RawResponse.java`
- `ExtractedClaim.java`
- `HealthStatus.java`
- `AdapterMetadata.java`

### Step 3 — app Mock 구현 (15분)

```bash
mkdir -p app/src/main/java/com/truthscope/web/adapter/datasource
```

본 가이드 §4 코드를 `MockDataSourceAdapter.java`로 그대로 복사.

### Step 4 — 테스트 (30분)

```bash
mkdir -p app/src/test/java/com/truthscope/web/adapter/datasource
```

본 가이드 §5 코드를 `MockDataSourceAdapterTest.java`로 그대로 복사.

### Step 5 — 로컬 검증 (15분)

CLAUDE.md "PR 전 필수 로컬 검사" 순서 그대로:

```bash
./gradlew spotlessApply
./gradlew checkstyleMain
./gradlew spotbugsMain
./gradlew test --tests "*MockDataSourceAdapter*"
./gradlew test
./gradlew build
```

전부 PASS여야 commit 단계로 진입.

> **순서 중요**: spotlessApply가 가장 먼저 (Google Java Format 자동 적용). 코드를 직접 수정한 적 없는 record/interface도 spotless가 한 줄 띄어쓰기/import order 등을 정리할 수 있다.

### Step 6 — Commit + Push + PR (~1h, CodeRabbit 대기 포함)

CONVENTIONS.md commit 규칙 정합 (Gitmoji + Co-authored-by 본인):

```bash
git add core app
git commit -m "✨feat(adapter): DataSourceAdapter 인터페이스 + MockDataSourceAdapter (S2-01)" -m "- core: DataSourceAdapter interface + 5 record (AdapterQuery / RawResponse / ExtractedClaim / HealthStatus / AdapterMetadata) — Spring DI 무관, OSS 배포 가능" -m "- app: MockDataSourceAdapter @Component (fixture 5건) — 통합 테스트/로컬 dev 용도" -m "- test: 10 cases 통과 (fetch happy/null + parse empty/null/fixtures + health UP/DOWN factory + metadata + fixture invariant 2)" -m "" -m "closes #22" -m "" -m "Co-authored-by: 한지민 <한지민-이메일>"
git push -u origin feat/S2-01-datasource-adapter
```

> **Windows PowerShell 사용 시**: `&&` 대신 줄을 분리해 한 줄씩 실행. `git commit -m "..." -m "..."`는 PowerShell에서도 동일하게 작동.

PR 생성 (gh CLI):

```bash
gh pr create --base dev --title "✨feat(adapter): DataSourceAdapter 인터페이스 + MockDataSourceAdapter (S2-01)"
```

`--body` 생략하면 editor 열림. 다음 템플릿을 본문으로 입력:

```markdown
## Summary

closes #22. ADR-006 OCP/D3 비전을 첫 어댑터로 실증 — Spring 무관 인터페이스(core) + Spring DI 구현(app) 분리 패턴 reference.

## 변경

- `core/src/main/java/com/truthscope/web/adapter/datasource/`:
  - `DataSourceAdapter` interface (5 메서드)
  - `AdapterQuery` / `RawResponse` / `ExtractedClaim` / `HealthStatus` / `AdapterMetadata` record
- `app/src/main/java/com/truthscope/web/adapter/datasource/MockDataSourceAdapter.java` (@Component, fixture 5건)
- `app/src/test/java/com/truthscope/web/adapter/datasource/MockDataSourceAdapterTest.java` (10 cases)

## 검증

- `./gradlew spotlessApply` PASS
- `./gradlew checkstyleMain` PASS
- `./gradlew spotbugsMain` PASS
- `./gradlew test --tests "*MockDataSourceAdapter*"` PASS (10 cases)
- `./gradlew test` 전체 회귀 PASS
- `./gradlew build` PASS

## Reference

- 본 가이드: `docs/guides/backend-datasource-adapter.md` (PM scaffold reference)
- ADR-006: core/app 분리 비전
- 후속 어댑터(S3a/b BigKinds/KOSIS, S7 GDELT)가 본 인터페이스를 베이스로 작성됨

## 학습 가치

Spring 무관 인터페이스(core 모듈) + Spring DI 구현(app 모듈) 분리로 ADR-006 OCP/OSS 비전을 첫 어댑터로 실증. 후속 어댑터(BigKinds/KOSIS/GDELT)가 본 패턴을 그대로 따라간다.
```

PR 생성 후 ~5분 대기 → CI(`build-and-test`) + CodeRabbit 동시 진행. CodeRabbit이 Critical 0이면 PM에게 코멘트로 머지 승인 요청. 머지는 `--merge` 패턴 (PR #41/#42/#43 정합).

---

## 8. 수용 기준 매핑 (BE #22 issue 본문)

| 수용 기준 | 본 가이드 위치 |
|---|---|
| DataSourceAdapter 인터페이스 메서드 5+ 정의 | §3.1 (5개: fetch/parse/health/metadata/fixture) |
| MockDataSourceAdapter 단위 테스트 PASS (5+ cases) | §5 (10 cases) |
| ArchUnit 룰 PASS (entity setter 금지) | §6 (entity 룰 영향 없음) |
| `./gradlew spotlessApply / checkstyleMain / spotbugsMain / test / build` 모두 PASS | §7 Step 5 |
| PR description에 학습 가치 1줄 명시 | §7 Step 6 PR body 마지막 섹션 |
| CodeRabbit 리뷰 통과 (Critical 0) | §7 Step 6 후 |

---

## 9. 후속 어댑터 작성 시 (S3a/b BigKinds/KOSIS, S7 GDELT)

본 인터페이스가 베이스. 구현체별로 다음 부분만 변경:

- `fetch`: 외부 API HTTP 호출 (Apache HttpClient 5 또는 Spring `RestClient`)
- `parse`: source format별 파서 (Jackson `ObjectMapper` for JSON / `Jsoup` for HTML / SAX/StAX for XML)
- `metadata`: 어댑터별 식별 정보 (BigKinds는 `isPaid=true`, GDELT는 `isPaid=false`)
- `fixture`: 어댑터별 5+ 고정 응답
- API 키: `application-local.yml` (Git 금지 — `.gitignore` 정합) → `@ConfigurationProperties` 또는 `@Value`
- **SsrfGuard 의무 호출**: 어댑터가 외부 URL을 fetch할 때 `SsrfGuard.validateAndResolve(url)` 호출. Phase 20에서 도입 (`.plans/20-ssrf-blocker/HANDOFF.md` + PR #37/#38/#39 → #40 머지). 사설 IP/내부망 fetch 차단

> **신규 어댑터 PR 시 본 가이드를 reference로 인용**. 시그니처 변경은 본 가이드 patch + 모든 어댑터 일괄 갱신.

---

## 10. Reference

- 이슈: <https://github.com/truthscope-smu/truthscope-web-backend/issues/22>
- ADR-006 (core/app 분리 비전): `truthscope-web/context/decisions/ADR-006-core-app-separation.md`
- BE #23 머지 (core/app 분리 결과): PR #41 (`64d019f`) → PR #42 (`0b3958c`)
- BE 컨벤션: `CLAUDE.md` (이 repo 루트) + `CONVENTIONS.md`
- 테스트 표준: `docs/guides/testing-standard.md`
- (선택) DDD/TDD 패턴: `docs/guides/backend-ddd-tdd-template.md` + `core/src/main/java/com/truthscope/web/entity/Article.java` — 본 어댑터는 entity가 아니라 직접 적용 안 되지만, 정적 팩토리 + invariant 검증 패턴 학습용
