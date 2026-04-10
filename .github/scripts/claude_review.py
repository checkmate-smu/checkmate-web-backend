import anthropic
import os
import subprocess
import sys


def main():
    api_key = os.environ.get("ANTHROPIC_API_KEY")
    if not api_key:
        print("ANTHROPIC_API_KEY not set — skipping review")
        sys.exit(0)

    pr_number = os.environ.get("PR_NUMBER", "")
    pr_title = os.environ.get("PR_TITLE", "")
    repo = os.environ.get("REPO", "")

    try:
        with open("pr_diff.txt", "r", encoding="utf-8", errors="replace") as f:
            diff = f.read()
    except FileNotFoundError:
        print("pr_diff.txt not found")
        sys.exit(1)

    if not diff.strip():
        print("Empty diff — skipping review")
        sys.exit(0)

    MAX_DIFF_CHARS = 15000
    truncated = False
    if len(diff) > MAX_DIFF_CHARS:
        diff = diff[:MAX_DIFF_CHARS] + "\n... (diff truncated)"
        truncated = True

    is_java = any(
        x in diff
        for x in [".java", "build.gradle", "@Entity", "@Service", "@Controller", "import com.checkmate"]
    )

    if is_java:
        stack_context = "Spring Boot 3.x (Java 17), JPA/Hibernate, Supabase PostgreSQL, Lombok"
        review_focus = """
- JPA Entity: @Entity, @Table, @Column 매핑 정확성
- 어노테이션: @Enumerated(EnumType.STRING) 필수, FetchType.LAZY 명시
- Lombok: @Data/@ToString 금지, @Getter만 사용 (양방향 연관관계 순환 참조 방지)
- 연관관계: mappedBy, nullable, @JoinColumn 정확성
- BaseTimeEntity 상속 여부 (ApiUsageLog 제외)
- UUID PK: Member 외 @GeneratedValue(strategy = GenerationType.UUID) 필수"""
    else:
        stack_context = "Next.js 15 (App Router), TypeScript, Feature-Sliced Design 아키텍처"
        review_focus = """
- FSD import 방향: 높은 번호 레이어에서만 낮은 번호로 import (예: 05-features → 07-shared)
- 디자인 토큰: 하드코딩 색상/spacing 금지, var(--*) 사용
- TypeScript: any 타입 사용 금지, 명시적 타입 선언
- React: 훅 규칙, 불필요한 re-render 방지
- Next.js App Router: 서버/클라이언트 컴포넌트 구분 ('use client' 위치)"""

    prompt = f"""당신은 CheckMate Web 프로젝트의 시니어 개발자입니다. 아래 PR의 코드 리뷰를 해주세요.

## PR 정보
- 제목: {pr_title}
- 기술 스택: {stack_context}

## 리뷰 중점 항목
{review_focus}

## 변경 코드 (diff)
```diff
{diff}
```
{f"⚠️ diff가 너무 길어 앞부분 {MAX_DIFF_CHARS}자만 검토했습니다." if truncated else ""}

---
아래 형식으로 **한국어**로 리뷰해주세요. 없는 항목은 생략하세요.

## 전체 평가
한 줄 요약

## ✅ 잘 된 점
- 구체적인 항목

## ❌ 수정 필요
- `파일명` — 문제점 및 해결 방법 (코드 예시 포함)

## 💡 개선 제안 (선택)
- 필수는 아니지만 더 좋아질 수 있는 부분

---
*🤖 Claude AI 자동 리뷰 (claude-haiku-4-5)*"""

    client = anthropic.Anthropic(api_key=api_key)

    message = client.messages.create(
        model="claude-haiku-4-5",
        max_tokens=2048,
        messages=[{"role": "user", "content": prompt}],
    )

    review_body = message.content[0].text

    result = subprocess.run(
        ["gh", "pr", "comment", pr_number, "--repo", repo, "--body", review_body],
        capture_output=True,
        text=True,
    )

    if result.returncode != 0:
        print(f"Failed to post comment: {result.stderr}", file=sys.stderr)
        sys.exit(1)

    print("✅ Claude review posted successfully!")


if __name__ == "__main__":
    main()
