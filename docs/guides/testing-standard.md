# Testing Standard — TruthScope Backend

> Sprint 2+ 모든 BE PR이 통과해야 할 표준 테스트 절차.
> 멤버는 본 가이드를 그대로 따라하면 PR 통과 가능 (AI 도구 무관).

## 1. 사전 환경 점검 (한 번만)

```bash
# Java 17+
java -version

# Docker (Testcontainers 의존)
docker version

# Gradle wrapper (저장소 내 ./gradlew 사용 — 별도 설치 X)
./gradlew --version
```

Docker 미설치 또는 미실행 시 통합 테스트(Testcontainers) 5건 모두 실패 → **반드시 Docker Desktop 실행 후 진행**.

## 2. PR 전 필수 명령어 (순서 중요)

```bash
./gradlew spotlessApply      # 1. 포맷 자동 수정 (Google Java Format)
./gradlew checkstyleMain     # 2. 린트
./gradlew spotbugsMain       # 3. 정적 분석
./gradlew test               # 4. 전체 테스트 (단위 + 통합)
./gradlew build              # 5. 최종 빌드
```

한 단계라도 실패하면 수정 후 재실행. 전부 통과한 뒤에만 commit/push.

## 3. 테스트 종류 구분

| 종류 | 위치 | 실행 명령 | 환경 |
|---|---|---|---|
| 단위 테스트 | `src/test/java/.../service/*Test.java` | `./gradlew test --tests "*ServiceTest"` | Spring 컨텍스트 X, 빠름 |
| 통합 테스트 | `src/test/java/.../controller/*IntegrationTest.java` | `./gradlew test --tests "*IntegrationTest"` | Testcontainers PostgreSQL, 느림 |
| 아키텍처 테스트 | `src/test/java/.../architecture/ArchitectureTest.java` | `./gradlew test --tests "ArchitectureTest"` | ArchUnit 룰 검증 (setter 금지 등) |

## 4. 통합 테스트 작성 규칙 (Phase 21 학습 정합)

### 베이스 클래스 상속 의무

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ArticleControllerIntegrationTest extends AbstractIntegrationTest {
    @Autowired
    private TestRestTemplate restTemplate;

    // ... test cases
}
```

`AbstractIntegrationTest`는 Singleton container 패턴으로 PostgreSQL 컨테이너를 JVM-singleton으로 관리 (클래스 단위 lifecycle 회피, stale port "Connection refused" 방지).

### 5+ test cases per controller (의무)

| 시나리오 | 필수 |
|---|---|
| Happy path (200/201) | 의무 |
| 404 Not Found | 의무 |
| 400 Bad Request (invalid input) | 의무 |
| 409 Conflict (도메인 invariant 위반) | 의무 (해당하는 경우) |
| 415/422 등 type mismatch / message not readable | 의무 (해당하는 경우) |

### Mock vs Testcontainers 선택

- **Mock**: Service layer 단위 테스트 (`@Mock` Repository, `@InjectMocks` Service)
- **Testcontainers**: Controller 통합 테스트 (실제 DB + JPA 매핑 검증)

→ Service에 비즈니스 로직 / Repository에 쿼리 / Controller에 매핑이 있다면 모두 통합 테스트로 검증.

## 5. 테스트 실패 디버깅 절차

### "Connection refused" (Testcontainers)

1. Docker Desktop 실행 확인
2. `./gradlew test --tests "ArticleControllerIntegrationTest" --info` 로 컨테이너 시작 로그 확인
3. PostgreSQL 컨테이너 image pull 진행도 확인 (첫 실행 시 30~60초 소요)

### "NoSuchBeanDefinitionException: TestRestTemplate"

1. `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)` 명시 확인 (기본값 MOCK은 TestRestTemplate 미주입)

### "ArchUnit rule violated"

1. Entity 클래스에 `@Setter` / `@Data` 사용 여부 확인 — 모두 제거
2. `@Getter` + 비즈니스 메서드(`attachTo()` 등)로 변경

### Spotless format 실패

```bash
./gradlew spotlessApply  # 자동 수정 후 재커밋
```

## 6. CI 파이프라인 (GitHub Actions)

PR 생성 시 자동 실행:

1. `format:check` (Spotless)
2. `checkstyleMain`
3. `spotbugsMain`
4. `test` (Testcontainers 포함)
5. `build`

**1건이라도 실패 시 PR merge 불가**. 로컬에서 동일 명령어로 미리 통과 의무.

## 7. PR 체크리스트 (PR 본문에 복사)

```markdown
- [ ] `./gradlew spotlessApply / checkstyleMain / spotbugsMain` PASS
- [ ] `./gradlew test` PASS (회귀 0, 신규 테스트 N건 추가)
- [ ] `./gradlew build` PASS
- [ ] ArchUnit 룰 PASS
- [ ] Springdoc 어노테이션 추가 (controller 신규 시)
- [ ] PR 본문에 spec 이슈 번호 명시
```
