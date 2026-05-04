package com.truthscope.web.html;

import java.net.URI;
import java.net.URISyntaxException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * 기사 HTML Document에서 제목/본문/언어/도메인을 추출하는 정적 헬퍼. ArchUnit serviceNaming 룰 회피 위해 service 패키지 외부 분리
 * (R2-6 SsrfGuard 패턴 동일).
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ArticleHtmlParser {

  public static final int MAX_BODY_LENGTH = 8000;

  private static final String[] BODY_SELECTORS = {
    "#newsct_article", // 네이버 뉴스 (현재)
    "#articeBody", // 네이버 뉴스 (구버전)
    "#harmonyContainer .article_view", // 다음 뉴스
    "article",
    ".article-body",
    ".article_body",
    ".post-content",
    "#content",
    ".content",
    ".entry-content",
    ".news-body",
    ".news_body",
  };

  public static String extractDomain(String url) {
    try {
      return new URI(url).getHost();
    } catch (URISyntaxException e) {
      return "unknown";
    }
  }

  public static String extractTitle(Document doc) {
    Element ogTitle = doc.selectFirst("meta[property=og:title]");
    if (ogTitle != null && !ogTitle.attr("content").isBlank()) {
      return ogTitle.attr("content").trim();
    }
    Element h1 = doc.selectFirst("h1");
    if (h1 != null && !h1.text().isBlank()) {
      return h1.text().trim();
    }
    String title = doc.title();
    if (!title.isBlank()) {
      return title.trim();
    }
    return "";
  }

  public static String extractBody(Document doc) {
    for (String selector : BODY_SELECTORS) {
      Elements elements = doc.select(selector);
      if (!elements.isEmpty()) {
        String text = elements.text().trim();
        if (!text.isBlank()) {
          return truncate(text);
        }
      }
    }
    String bodyText = doc.body() != null ? doc.body().text().trim() : "";
    return truncate(bodyText);
  }

  public static String extractLang(Document doc) {
    Element html = doc.selectFirst("html");
    if (html != null) {
      String lang = html.attr("lang");
      if (!lang.isBlank()) {
        return lang.split("-")[0].trim();
      }
    }
    return "unknown";
  }

  public static String truncate(String text) {
    if (text.length() <= MAX_BODY_LENGTH) {
      return text;
    }
    int end = MAX_BODY_LENGTH;
    // CodeRabbit PR#40: surrogate pair 경계 보정 (이모지/보조평면 문자가 절단되지 않도록).
    if (Character.isHighSurrogate(text.charAt(end - 1))
        && end < text.length()
        && Character.isLowSurrogate(text.charAt(end))) {
      end--;
    }
    return text.substring(0, end);
  }
}
