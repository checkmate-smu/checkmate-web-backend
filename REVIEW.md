# CheckMate Backend — PR 리뷰 규칙

> Claude AI 리뷰어 전용. 한국어로 리뷰한다.

## Always check

### Entity 안티패턴
- `@Data` 사용 여부 → 양방향 관계에서 StackOverflowError
- `@ToString` 사용 여부 → 동일 위험
- `@Setter` 사용 여부 → 불변성 위반
- `@Enumerated(EnumType.ORDINAL)` 사용 여부 → 순서 변경 시 데이터 깨짐
- `fetch = FetchType.LAZY` 누락 여부 → N+1 쿼리 문제
- `extends BaseTimeEntity` 누락 여부 (ApiUsageLog 예외)

### 레이어 위반
- Controller에 비즈니스 로직 포함 여부
- Entity를 Controller/응답에 직접 노출하는지 (DTO 변환 필수)
- Repository → Service 역방향 의존 여부

### DI 규칙
- `@Autowired` 필드 주입 사용 여부 → `@RequiredArgsConstructor` + `private final`만 허용

### 엔드포인트 규칙
- `/api/v1/` 접두사 누락 여부
- URI에 동사 포함 여부
- 자원명 단수형 사용 여부 (복수형 필수)

### 환경 설정
- `.env` 파일 사용 여부 → `application.yml` 사용 필수
- `application-local.yml`이 커밋에 포함되었는지

### Gemini 모델
- `gemini-2.0-flash-lite` 사용 여부 → 이 프로젝트에서 사용 금지
- 모델 ID가 임의로 변경되었는지

## Style

- 요청 DTO는 `record` 타입 권장
- 응답 DTO는 `@Getter` + `@Builder` 패턴 권장
- Converter는 `static` 메서드 + `@NoArgsConstructor(access = PRIVATE)`
- `@Transactional(readOnly = true)` — 읽기 전용 Service 메서드

## Skip

- Spotless 포맷 관련 이슈 (CI에서 `spotlessCheck`가 자동 검사)
- import 순서 (Spotless가 자동 정리)
- 테스트 코드의 사소한 네이밍
- Javadoc 누락 (이 프로젝트는 Javadoc 강제하지 않음)
- Sprint 2 이후 기능 (Springdoc, VIA_DTO 페이지네이션) 관련 미구현 지적
