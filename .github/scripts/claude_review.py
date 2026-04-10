import anthropic
import os
import subprocess
import sys


def load_review_context() -> str:
    """레포 루트의 .github/review-context.md를 읽어 반환. 없으면 빈 문자열."""
    context_path = os.path.join(os.path.dirname(__file__), "..", "review-context.md")
    try:
        with open(context_path, "r", encoding="utf-8") as f:
            return f.read()
    except FileNotFoundError:
        return ""


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

    review_context = load_review_context()
    context_section = (
        f"\n## 프로젝트 컨벤션 및 평가 기준\n{review_context}\n"
        if review_context
        else ""
    )

    prompt = f"""당신은 CheckMate Web 프로젝트의 시니어 개발자입니다. 아래 PR의 코드 리뷰를 해주세요.

## PR 정보
- 제목: {pr_title}
{context_section}
## 변경 코드 (diff)
```diff
{diff}
```
{f"⚠️ diff가 너무 길어 앞부분 {MAX_DIFF_CHARS}자만 검토했습니다." if truncated else ""}

---
위 평가 기준을 바탕으로 **한국어**로 리뷰해주세요. 없는 항목은 생략하세요.

## 전체 평가
한 줄 요약

## ✅ 잘 된 점
- 구체적인 항목

## ❌ 수정 필요
- `파일명` — 문제점 및 해결 방법 (코드 예시 포함)

## 💡 개선 제안 (선택)
- 필수는 아니지만 더 좋아질 수 있는 부분

---
*🤖 Claude AI 자동 리뷰 (claude-sonnet-4-6)*"""

    client = anthropic.Anthropic(api_key=api_key)

    message = client.messages.create(
        model="claude-sonnet-4-6",
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
