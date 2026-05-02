# TruthScope Backend — PR 리뷰 규칙

> Claude AI 리뷰어 전용. 한국어로 리뷰한다.
> 포맷/네이밍/레이어 위반은 CI(Spotless + Checkstyle + ArchUnit)가 자동 차단하므로 여기서 다루지 않는다.
> Claude는 CI가 잡지 못하는 **비즈니스 로직, 성능, 보안**에 집중한다.

## Always check

### 비즈니스 로직 결함
- 분기 조건의 논리적 오류 (off-by-one, 경계값 누락)
- 트랜잭션 범위가 적절한지 (너무 넓거나 누락)
- 예외 발생 시 데이터 일관성이 깨지는 경로 존재 여부

### 성능
- JPA N+1 쿼리 패턴 (연관 엔티티 반복 조회)
- 불필요한 DB 조회 (이미 가진 데이터를 다시 조회)
- 루프 안에서 외부 API 호출 여부

### 보안
- 사용자 입력이 검증 없이 쿼리/명령에 전달되는지
- API 키나 비밀번호가 하드코딩되었는지
- `application-local.yml`이 커밋에 포함되었는지

### Entity 설계
- `@Data`, `@ToString`, `@Setter` 사용 여부 (ArchUnit이 못 잡는 어노테이션)
- `@Enumerated(EnumType.ORDINAL)` 사용 여부
- `fetch = FetchType.LAZY` 누락 여부
- `extends BaseTimeEntity` 누락 여부 (ApiUsageLog 예외)

### Gemini 모델
- `gemini-2.0-flash-lite` 사용 여부 → 이 프로젝트에서 사용 금지
- 모델 ID가 임의로 변경되었는지

## Style

- 요청 DTO는 `record` 타입 권장
- Converter는 `static` 메서드 + `@NoArgsConstructor(access = PRIVATE)`
- `@Transactional(readOnly = true)` — 읽기 전용 Service 메서드

## Skip — CI가 자동으로 검증하는 항목

- 코드 포맷 (Spotless가 검증)
- import 순서 (Spotless가 정리)
- 네이밍 규칙 (Checkstyle이 검증)
- 파일/메서드 길이 (Checkstyle이 검증)
- NPE, resource leak (SpotBugs가 탐지)
- 레이어 의존성 위반 (ArchUnit이 테스트)
- Controller→Repository 직접 접근 (ArchUnit이 차단)
- 클래스 접미사 누락 (ArchUnit이 검증)
- Javadoc 누락 (이 프로젝트는 강제하지 않음)
