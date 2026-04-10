# CheckMate Backend — PR 리뷰 기준

> 이 파일은 Claude AI PR 리뷰어가 참고하는 평가 기준입니다.
> 팀 컨벤션이 바뀌면 이 파일만 수정하면 됩니다.

---

## 기술 스택
- Spring Boot 3.x (Java 17+)
- JPA / Hibernate + Supabase PostgreSQL
- Lombok

---

## JPA Entity 규칙 (최우선)

### 클래스 레벨 필수 어노테이션
```java
@Entity
@Table(name = "snake_case_테이블명")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class ExampleEntity extends BaseTimeEntity { ... }
```

### 금지 사항
- `@Data` 사용 금지 — 양방향 연관관계에서 StackOverflowError 발생
- `@ToString` 사용 금지 — 동일 이유. `@ToString(exclude = "...")` 도 지양
- `@Setter` 사용 금지 — 불변성 보장
- `EnumType.ORDINAL` 사용 금지 — 순서 변경 시 DB 데이터 깨짐

### PK 규칙
- `Member` 엔티티: `@GeneratedValue` 없음 (Supabase Auth UUID를 외부에서 받음)
- 그 외 모든 엔티티: `@GeneratedValue(strategy = GenerationType.UUID)` 필수

### Enum 필드
```java
// ✅ 필수
@Enumerated(EnumType.STRING)
@Column(nullable = false, length = 20)
private SessionStatus status;
```

### 연관관계
- `@ManyToOne`, `@OneToOne` — 반드시 `fetch = FetchType.LAZY` 명시 (기본값 EAGER 방지)
- `@JoinColumn(name = "fk_컬럼명")` — FK 컬럼명 명시 필수
- 비소유 측: `mappedBy = "필드명"` 필수 (필드명이지 컬럼명이 아님)
- `@UniqueConstraint`의 `columnNames` — DB 컬럼명(snake_case), Java 필드명 아님

### BaseTimeEntity 상속
- 모든 엔티티 상속 필수
- **예외**: `ApiUsageLog` — `usage_date` 컬럼 사용으로 상속 불필요

### @Column 매핑
- camelCase 필드 → `@Column(name = "snake_case")` 명시 필수
- NOT NULL → `nullable = false`
- TEXT 타입 → `columnDefinition = "TEXT"`

---

## 패키지 구조 규칙
```
com.checkmate.web/
├── controller/     ← REST API 엔드포인트
├── service/        ← 비즈니스 로직
├── repository/     ← JPA Repository
├── entity/         ← DB Entity
│   └── enums/      ← Enum 타입
├── dto/            ← 요청/응답 DTO
├── config/         ← 설정 클래스
└── exception/      ← 전역 예외 처리
```

---

## Gemini 모델 정책 (절대 변경 금지)
- 기본 모델: `gemini-3.1-flash-lite-preview`
- 폴백 모델: `gemini-2.5-flash-lite`
- `gemini-2.0-flash-lite` 사용 금지

---

## 커밋 / PR 규칙
- 브랜치: `feat/{이슈번호}-{기능명}`, base: `dev`
- 커밋: `{gitmoji}{type}({scope}): {설명}`
- 태스크 1개 = 커밋 1개
