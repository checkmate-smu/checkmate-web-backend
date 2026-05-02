package com.truthscope.web.service;

import com.truthscope.web.dto.response.ExtractedArticle;
import com.truthscope.web.exception.BadRequestException;
import com.truthscope.web.exception.ExtractionFailedException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

/** 뉴스 URL에서 기사 본문을 추출하는 서비스 */
@Service
public class ContentExtractService {

  private static final int MAX_BODY_LENGTH = 8000;
  private static final int TIMEOUT_MS = 10_000;
  private static final String USER_AGENT =
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
          + "AppleWebKit/537.36 (KHTML, like Gecko) "
          + "Chrome/124.0.0.0 Safari/537.36";

  /**
   * 주어진 URL에서 기사 본문을 추출한다.
   *
   * @param url 뉴스 기사 URL (http/https만 허용)
   * @return 제목, 본문, 언어, 도메인이 담긴 ExtractedArticle
   * @throws BadRequestException URL이 null이거나 형식이 유효하지 않을 때
   * @throws ExtractionFailedException Jsoup 연결 실패 또는 본문이 비어 있을 때
   */
  public ExtractedArticle extract(String url) {
    validateUrl(url);

    String domain = extractDomain(url);

    Document doc;
    try {
      doc = Jsoup.connect(url).userAgent(USER_AGENT).timeout(TIMEOUT_MS).get();
    } catch (IOException e) {
      throw new ExtractionFailedException("기사를 가져올 수 없습니다: " + url);
    }

    String title = extractTitle(doc);
    String body = extractBody(doc);
    String lang = extractLang(doc);

    if (body.isBlank()) {
      throw new ExtractionFailedException("기사 본문을 추출할 수 없습니다");
    }

    return ExtractedArticle.builder().title(title).body(body).lang(lang).domain(domain).build();
  }

  // ── URL 유효성 검증 ─────────────────────────────────────────────────────────

  void validateUrl(String url) {
    if (url == null || url.isBlank()) {
      throw new BadRequestException("URL은 필수입니다");
    }

    try {
      URI uri = new URI(url);
      String scheme = uri.getScheme();
      if (scheme == null || (!scheme.equals("http") && !scheme.equals("https"))) {
        throw new BadRequestException("유효하지 않은 URL 형식입니다");
      }
      if (uri.getHost() == null) {
        throw new BadRequestException("유효하지 않은 URL 형식입니다");
      }
    } catch (URISyntaxException e) {
      throw new BadRequestException("유효하지 않은 URL 형식입니다");
    }
  }

  // ── 도메인 추출 ─────────────────────────────────────────────────────────────

  String extractDomain(String url) {
    try {
      return new URI(url).getHost();
    } catch (URISyntaxException e) {
      return "unknown";
    }
  }

  // ── 제목 추출 ────────────────────────────────────────────────────────────────

  private String extractTitle(Document doc) {
    // 1순위: og:title 메타 태그
    Element ogTitle = doc.selectFirst("meta[property=og:title]");
    if (ogTitle != null && !ogTitle.attr("content").isBlank()) {
      return ogTitle.attr("content").trim();
    }

    // 2순위: h1 태그
    Element h1 = doc.selectFirst("h1");
    if (h1 != null && !h1.text().isBlank()) {
      return h1.text().trim();
    }

    // 3순위: title 태그
    String title = doc.title();
    if (!title.isBlank()) {
      return title.trim();
    }

    return "";
  }

  // ── 본문 추출 ────────────────────────────────────────────────────────────────

  private String extractBody(Document doc) {
    // 선택자 우선순위: 주요 국내 뉴스 사이트 → 일반 선택자
    String[] selectors = {
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

    for (String selector : selectors) {
      Elements elements = doc.select(selector);
      if (!elements.isEmpty()) {
        String text = elements.text().trim();
        if (!text.isBlank()) {
          return truncate(text);
        }
      }
    }

    // 폴백: body 전체 텍스트
    String bodyText = doc.body() != null ? doc.body().text().trim() : "";
    return truncate(bodyText);
  }

  // ── 언어 감지 ────────────────────────────────────────────────────────────────

  private String extractLang(Document doc) {
    Element html = doc.selectFirst("html");
    if (html != null) {
      String lang = html.attr("lang");
      if (!lang.isBlank()) {
        // "ko-KR" → "ko" 정규화
        return lang.split("-")[0].trim();
      }
    }
    return "unknown";
  }

  // ── 유틸리티 ─────────────────────────────────────────────────────────────────

  private String truncate(String text) {
    if (text.length() <= MAX_BODY_LENGTH) {
      return text;
    }
    return text.substring(0, MAX_BODY_LENGTH);
  }
}
