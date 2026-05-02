# TruthScope Domain Model

> 최종 갱신: 2026-05-01 (DDD/TDD seed phase)
> 관련 ERD: `docs/md/10_erd.md` (스키마 source of truth — 수정 시 동기화)
> 관련 가이드: `docs/guides/ddd-tdd-seed-guide.md`, `docs/guides/entity-mapping-guide.md`
> 적용 범위: Sprint 1~3 (도메인 복잡도 증가 시 Modulith 도입 재검토)

이 문서는 TruthScope 백엔드의 도메인 모델을 DDD 어휘(Aggregate / Entity / Value Object / Domain Event / Bounded Context)로 정리한 것이다. 코드와 ERD가 source of truth이며, 본 문서는 **개념 분류 + invariant + 라이프사이클**을 박제한다.

---

## 1. Bounded Context (개념 경계)

TruthScope는 단일 모듈(`com.truthscope.web`)이지만 도메인 책임은 4개로 나뉜다. 향후 Modulith 도입 시 경계 후보.

| Context | 책임 | 핵심 Aggregate |
|---------|------|---------------|
| **Analysis** | 뉴스 URL 입력 → 본문 추출 → Claim 분리 | `AnalysisSession` (root) → `Article` → `Claim` |
| **Verification** | Claim → Tier 1/2/3 캐스케이드 검증 | `VerificationResult` (root) → `VerifySource`, `FactcheckCache` |
| **Account** | 사용자 / 인증 / 반응 | `Member` (root) → `UserReaction` |
| **Audit** | API 사용량 / 로깅 | `ApiUsageLog` (root, BaseTime 예외) |

> **현재 패키지 구조는 layer-based** (`controller/service/repository/entity`)이므로 위 경계는 개념적이며 코드 레벨로 강제되지 않는다. Sprint 3+ 도메인 분리 결정 시 `com.truthscope.web.{analysis,verification,account,audit}.*` 재구조화 후보.

---

## 2. Aggregate 카탈로그

각 Aggregate는 1개의 root entity + 자식 entity / VO로 구성. **외부 참조는 root ID로만** (DDD principle).

### 2.1 Analysis Context

#### `AnalysisSession` — Aggregate Root

- **책임**: 한 번의 뉴스 분석 요청의 전 라이프사이클 추적
- **상태**: `SessionStatus` enum — `PENDING / EXTRACTING / ANALYZING / COMPLETED / FAILED`
- **Invariant** (TODO — 다음 phase):
  - 상태 전이는 정해진 순서로만 (`PENDING → EXTRACTING → ANALYZING → COMPLETED`, 또는 어느 단계에서든 → `FAILED`)
  - `COMPLETED` 상태 진입 후에는 추가 상태 변경 금지
- **자식**:
  - `Article` (1:1, OneToOne)
  - `Claim` 컬렉션 (1:N — 미보강)

#### `Article` — Sub-entity (혹은 별도 Aggregate 후보)

- **책임**: 추출된 기사 원문 박제
- **Invariant** (✅ 시드 적용):
  - `url`은 http(s)로 시작 (`Article.extract` 정적 팩토리)
  - `url`은 null/blank 거부
- **TODO**:
  - `domain`은 `url`에서 자동 추출 (현재는 caller가 전달)
  - `extractedAt`은 immutable (현재 setter 없음 — ArchUnit 룰로 보장)
  - `body` 길이 상한 (최대 N MB) — Tier 2 검증 대비

#### `Claim` — Sub-entity

- **책임**: Article에서 Gemini로 추출된 단일 사실 주장
- **속성**: `text`, `importance` (`ClaimImportance` enum)
- **Invariant** (TODO):
  - `text`는 비어있을 수 없음
  - `importance`는 enum 값만 (Hibernate `@Enumerated(STRING)`로 강제)
- **관계**: 1 Claim → 0..1 `VerificationResult` (검증 시도 후 생성)

### 2.2 Verification Context

#### `VerificationResult` — Aggregate Root

- **책임**: 한 Claim에 대한 3-Tier Cascade 검증 결과
- **Invariant** (TruthScope 도메인 핵심):
  - **Tier 1**: `score` 0~100, `label` = 기관 검증
  - **Tier 2**: `score` 0~100, `label` + **disclaimer 필수** ("AI 분석이며 기관 검증이 아닙니다...")
  - **Tier 3**: `score` = **NULL**, "검증 불가" — 점수 부여 절대 금지 (`.claude/rules/domain-logic.md` 참조)
- **자식**: `VerifySource` 컬렉션 (1:N)

#### `VerifySource` — Sub-entity

- 검증에 사용된 출처 1건 (URL + 출판사 + 신뢰도)
- Tier 2는 최소 3건 출처 필수

#### `FactcheckCache` — 독립 Aggregate

- Tier 1 로컬 캐시 (외부 Google FC API 호출 비용 절감)
- 정합성 invariant: 캐시 만료 시간 / 동일 URL 중복 금지

### 2.3 Account Context

#### `Member` — Aggregate Root

- Supabase Auth UUID를 PK로 사용 (`@GeneratedValue` 없음, 외부 발급)
- `MemberRole` enum: `USER / ADMIN`
- **Invariant** (TODO): role 변경은 ADMIN 권한 필요 (도메인 메서드 vs 권한 체크)

#### `UserReaction` — Sub-entity

- Member + AnalysisSession 조합당 1건 (unique constraint)
- `ReactionType` enum: `HELPFUL / NOT_HELPFUL` 등

### 2.4 Audit Context

#### `ApiUsageLog` — 독립 Aggregate

- BaseTimeEntity 상속하지 **않음** (audit 전용 timestamp)
- 외부 API (Gemini, FC, DeepL) 호출 메타: 호출 수, 토큰, 응답 시간

---

## 3. Value Object 후보 (현재는 primitive, 점진적 추출)

| VO 이름 | 현재 표현 | 추출 트리거 | 검증 invariant |
|--------|---------|-----------|---------------|
| `ArticleUrl` | `String url` | url 검증 로직이 2곳 이상에 중복 시 | http(s) 스킴 + 길이 상한 + 도메인 추출 메서드 |
| `Score` | `Integer/Float` | Tier별 score 산출 로직 분기 시 | 0~100 + Tier 메타 (1=기관, 2=AI+disclaimer, 3=NULL) |
| `LangCode` | `String lang` (length 10) | 다국어 매핑 로직 추가 시 | ISO 639-1 (2자) 또는 BCP47 |
| `DomainName` | `String domain` (length 255) | 도메인 신뢰도 평가 추가 시 | 정규화된 hostname |

> VO 추출은 **사용처가 2곳 이상으로 증가했을 때만**. premature abstraction 회피.

---

## 4. Domain Event 후보 (현재 미도입, deferred)

도메인 이벤트는 트랜잭션 경계 + 비동기 처리가 필요할 때 도입. 후보:

| Event | 발생 시점 | Listener 후보 |
|-------|----------|--------------|
| `ArticleExtractedEvent` | `Article.extract(...)` 성공 직후 | ClaimAnalysisService (Gemini 호출) |
| `VerificationCompletedEvent` | `VerificationResult.markCompleted()` | UserNotification, ApiUsageLog |
| `MemberRoleChangedEvent` | `Member.changeRole(...)` | Audit log, Cache invalidation |

도입 시 **반드시 `@TransactionalEventListener(phase = AFTER_COMMIT)`** — rollback 시 stale event 누출 방지 (Phase E0 학습).

---

## 5. 라이프사이클 다이어그램 (Analysis Context)

```text
[USER]
  │ POST /api/v1/news (url)
  ▼
[NewsController]
  │
  ▼
[ContentExtractService]  ← 1. AnalysisSession 생성 (PENDING)
  │                       2. status → EXTRACTING
  │                       3. Jsoup 본문 추출
  │                       4. Article.extract(url, ...)  ← invariant 검증
  ▼
[ClaimAnalysisService]   ← 5. status → ANALYZING
  │                       6. Gemini Claim 추출
  │                       7. Claim N건 생성
  ▼
[CrossVerifyService]     ← 8. Tier 1 (Google FC + cache) → VerificationResult
  │
  ▼
[PerspectiveService]     ← 9. Tier 2 (AI 교차검증) — Tier 1 miss 시
  │                      10. Tier 3 (NULL) — Tier 2도 miss 시
  ▼
                         11. status → COMPLETED
                         12. ArticleExtractedEvent 발행 (TODO)
[USER에 응답]
```

---

## 6. 변경 이력

| 날짜 | 변경 | 트리거 |
|------|------|------|
| 2026-05-01 | 초안 박제. Aggregate 9건 + Article invariant 1건 시드 | DDD/TDD seed phase |

차후 Sprint 종료마다 본 문서 갱신. ERD (`docs/md/10_erd.md`) + 본 문서 + 코드 3자가 항상 정합해야 함.
