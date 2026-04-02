# CheckMate Web Backend

> AI 뉴스 신뢰도 분석 서비스 — 백엔드 서버

---

## 프로젝트 개요

**CheckMate Web**은 AI를 활용하여 뉴스 기사의 신뢰도를 분석하는 서비스입니다.

### Tech Stack

| 분류 | 기술 |
|------|------|
| Language | Java 17+ |
| Framework | Spring Boot 3.x |
| Database | Supabase (PostgreSQL) |
| AI / API | Gemini API, Google Fact Check API, DeepL API |

---

## 커밋 메시지 컨벤션

### Gitmoji + 태그 방식

| Gitmoji | Tag | 설명 |
|:---:|:---:|---|
| ✨ | feat | 새로운 기능 추가 |
| 🐛 | fix | 버그 수정 |
| 📝 | docs | 문서 추가, 수정, 삭제 |
| ✅ | test | 테스트 코드 |
| 💄 | style | 코드 형식 변경 |
| ♻️ | refactor | 코드 리팩토링 |
| ⚡️ | perf | 성능 개선 |
| 💚 | ci | CI 관련 설정 |
| 🚀 | chore | 기타 변경사항 |
| 🔥 | remove | 코드 및 파일 제거 |

### 예시

```
✨feat: order 패키지 구현
🐛fix: 로그인 토큰 만료 오류 수정
📝docs: API 명세서 업데이트
```

---

## 이슈 생성 규칙

1. **GitHub Issues** → **New Issue** → 템플릿 선택
2. 제목 형식: `✨ feat: 작업 내용`

---

## 브랜치 네이밍

```
타입/#이슈번호-작업내용
```

예시:
- `feature/#2-login`
- `fix/#5-token-expiry`
- `docs/#8-readme-update`

---

## PR 템플릿

> **base 브랜치: 본인 이름 브랜치 (main 아님!)**

```markdown
# ☝️Issue Number
- #이슈번호

## 📌 개요

## 🔁 변경 사항

## 📸 스크린샷

## 👀 기타
```

---

## 개발 워크플로우

```
Issue 생성 → 브랜치 생성 → 개발 → PR → 코드리뷰 → 머지 → Issue 닫기
```

1. **Issue 생성**: 작업할 내용을 이슈로 등록
2. **브랜치 생성**: `타입/#이슈번호-작업내용` 형식으로 생성
3. **개발**: 기능 구현
4. **PR**: 본인 이름 브랜치를 base로 PR 생성
5. **코드리뷰**: 팀원 리뷰 후 승인
6. **머지**: 승인 완료 후 머지
7. **Issue 닫기**: 머지 후 해당 이슈 close
