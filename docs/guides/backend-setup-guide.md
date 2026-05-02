# TruthScope 백엔드 — 로컬 개발 환경 세팅 가이드

> 대상: 이정훈, 한지민, 정세린 (백엔드 Entity 작업자)
> 마지막 갱신: 2026-04-10

이 가이드를 따라하면 **Spring Boot를 로컬에서 실행하고, Entity가 DB 테이블로 생성되는 것까지 확인**할 수 있습니다.

---

## 1단계: 레포 클론 + IDE 설정

```bash
git clone https://github.com/truthscope-smu/truthscope-web-backend.git
cd truthscope-web-backend
```

**IntelliJ IDEA** (권장):
1. IntelliJ 열기 → Open → `truthscope-web-backend` 폴더 선택
2. Gradle 자동 import 완료까지 대기 (우하단 프로그레스 바)
3. Project SDK가 Java 17+인지 확인: File → Project Structure → SDK

**VS Code**:
1. Extension Pack for Java 설치
2. `truthscope-web-backend` 폴더 열기

---

## 2단계: application-local.yml 파일 받기

**권석에게 `application-local.yml` 파일을 받으세요.** 디스코드 DM으로 전달받습니다.

이 파일에 Supabase DB 연결 정보(Host, Port, User, Password)가 모두 들어 있습니다. 직접 Supabase 대시보드에 접속할 필요 없습니다.

---

## 3단계: application-local.yml 배치

권석에게 받은 `application-local.yml` 파일을 아래 경로에 넣습니다:

```
src/main/resources/application-local.yml
```

> **절대 이 파일을 Git에 올리지 마세요!** `.gitignore`에 이미 등록되어 있지만, 실수로 `git add .`하면 비밀번호가 노출됩니다.

---

## 4단계: 실행

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

**성공하면** 아래 로그가 나옵니다:
```
Started TruthScopeWebBackendApplication in X.XXX seconds
```

**흔한 에러와 해결**:

| 에러 | 원인 | 해결 |
|------|------|------|
| `Connection refused` | Supabase Host/Port 오류 | 대시보드에서 Session pooler 정보 재확인 |
| `password authentication failed` | 비밀번호 틀림 | 권석에게 확인 |
| `Tenant not found` | Host가 `aws-0`인데 실제는 `aws-1` | 대시보드에서 정확한 Host 복사 |
| `FATAL: no pg_hba.conf entry` | Direct connection 사용 중 | Session pooler로 변경 (Port: 5432) |
| `Could not resolve host` | IPv6 환경 문제 | Session pooler 사용 확인 |

---

## 5단계: Entity가 제대로 매핑됐는지 확인

현재 `ddl-auto: none`이므로 **Spring Boot가 테이블을 자동 생성하지 않습니다**. Supabase에 이미 테이블이 있고, Entity가 그 테이블과 매핑됩니다.

### 빌드만 확인 (DB 연결 없이)

Entity 코드가 문법적으로 맞는지만 확인하려면:

```bash
./gradlew build
```

`BUILD SUCCESSFUL`이 나오면 Entity 클래스 문법은 정상입니다.

### DB 연결까지 확인

서버를 띄운 후 (`./gradlew bootRun ...`), 아래 로그에서 Hibernate가 Entity를 인식했는지 확인:

```
org.hibernate.SQL : select ... from members ...
```

에러 없이 서버가 뜨면 Entity ↔ DB 매핑 성공입니다.

---

## ddl-auto 설정 참고

| 값 | 동작 | 용도 |
|----|------|------|
| `none` | 아무것도 안 함 (현재 설정) | 운영/Supabase — DDL은 Supabase에서 직접 관리 |
| `validate` | 테이블 구조가 Entity와 다르면 에러 | 매핑 확인용으로 유용 |
| `update` | Entity 기반으로 테이블 자동 수정 | 위험 — Supabase 환경에서 비권장 |
| `create` | 매번 테이블 삭제 후 재생성 | 로컬 테스트 DB에서만 |

> **현재 프로젝트는 `none`**. Supabase에서 DDL(테이블 생성/수정)을 직접 관리합니다. Entity 코드를 바꿔도 DB 테이블은 자동으로 변하지 않습니다.

---

## 정리 — Entity 작업 흐름

```
1. Entity 코드 작성 (entity-mapping-guide.md 참고)
        ↓
2. ./gradlew build  → BUILD SUCCESSFUL 확인
        ↓
3. ./gradlew bootRun --args='--spring.profiles.active=local'
        ↓
4. 서버 정상 기동 확인 (에러 없음)
        ↓
5. 디스코드에 진행 상황 공유
        ↓
6. Git commit + PR (dev 브랜치로)
```

**막히면 30분 안에 디스코드에 공유하세요.** 혼자 붙잡고 있는 것보다 빠르게 물어보는 게 팀 프로젝트에서 더 중요합니다.

---

## 참고

- Entity 작성 가이드: `docs/guides/entity-mapping-guide.md`
- ERD 상세: `docs/md/10_erd.md`
- `.env.example`: 전체 환경변수 목록 + 안내
- Spring Boot 문제 해결: 옵시디언 `Guide/Spring Boot Troubleshooting and Resources Guide.md`
