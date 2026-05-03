package com.truthscope.web.spike.datasourceaccuracy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * Utilities for the 2026-04-24 datasource-accuracy spike (rev.5 protocol).
 *
 * <p>Intentionally plain Java — no Spring context. Reads {@code .env.local} at backend root, wraps
 * Gemini + Google Fact Check HTTP calls, extracts article bodies via Jsoup, and helps the three
 * spike classes produce CSV artifacts.
 *
 * <p>Non-goals: production use, thread safety, retry policies. Throws on failure — caller decides.
 */
final class SpikeSupport {

  private SpikeSupport() {}

  // ── Paths ──────────────────────────────────────────────────────────────
  static final Path BACKEND_ROOT = Path.of("").toAbsolutePath();
  static final Path ENV_LOCAL = BACKEND_ROOT.resolve(".env.local");
  static final Path SPIKE_RESOURCES = Path.of("src/spike/resources/spike/datasource-accuracy");
  static final Path GOLD_SET_CSV = SPIKE_RESOURCES.resolve("gold-set.csv");
  static final Path RESULTS_DIR = SPIKE_RESOURCES.resolve("results");

  // ── HTTP / JSON ────────────────────────────────────────────────────────
  private static final HttpClient HTTP =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  static final ObjectMapper JSON = new ObjectMapper();

  // ── Env loader ─────────────────────────────────────────────────────────
  static Env loadEnv() {
    Properties p = new Properties();
    try {
      if (!Files.exists(ENV_LOCAL)) {
        throw new IllegalStateException(
            ".env.local not found at " + ENV_LOCAL + " — spike cannot run");
      }
      for (String line : Files.readAllLines(ENV_LOCAL)) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
          continue;
        }
        int eq = trimmed.indexOf('=');
        if (eq <= 0) {
          continue;
        }
        p.setProperty(trimmed.substring(0, eq).trim(), trimmed.substring(eq + 1).trim());
      }
    } catch (IOException e) {
      throw new RuntimeException("Failed to read .env.local", e);
    }
    List<String> geminiKeys = new ArrayList<>();
    geminiKeys.add(required(p, "GEMINI_API_KEY"));
    String fb1 = p.getProperty("GEMINI_API_KEY_FALLBACK_1");
    if (fb1 != null && !fb1.isBlank()) {
      geminiKeys.add(fb1);
    }
    String fb2 = p.getProperty("GEMINI_API_KEY_FALLBACK_2");
    if (fb2 != null && !fb2.isBlank()) {
      geminiKeys.add(fb2);
    }
    return new Env(
        List.copyOf(geminiKeys),
        p.getProperty("GEMINI_MODEL", "gemini-3.1-flash-lite-preview"),
        p.getProperty("GEMINI_MODEL_FALLBACK", "gemini-2.5-flash-lite"),
        required(p, "FACTCHECK_API_KEY"),
        p.getProperty("FACTCHECK_LANGUAGE", "ko"));
  }

  private static String required(Properties p, String key) {
    String v = p.getProperty(key);
    if (v == null || v.isBlank()) {
      throw new IllegalStateException("Missing env: " + key);
    }
    return v;
  }

  /**
   * Spike environment. {@code geminiKeys} is an ordered list (main first, then fallbacks). {@code
   * geminiFallback} model is the last-resort after all key rotations fail — note that {@code
   * gemini-2.5-flash-lite} is slated for end-of-service 2026-06.
   */
  record Env(
      List<String> geminiKeys,
      String geminiModel,
      String geminiFallback,
      String factcheckKey,
      String factcheckLang) {
    String primaryKey() {
      return geminiKeys.get(0);
    }
  }

  // ── Gemini generateContent ──────────────────────────────────────────────
  //
  // Failover order (2026-04-24 policy — 2.5-flash-lite deprecation in 2026-06):
  //   1. primary key  × primary model
  //   2. fallback key 1 × primary model
  //   3. fallback key 2 × primary model
  //   4. primary key  × fallback model (last resort, deprecating)
  //
  // Keys rotated BEFORE model downgrade to stay on gemini-3.1-flash-lite-preview.
  static String callGeminiGenerate(Env env, String prompt)
      throws IOException, InterruptedException {
    IOException lastOverload = null;
    // Step 1–3: rotate through all keys with primary model
    for (int i = 0; i < env.geminiKeys().size(); i++) {
      String key = env.geminiKeys().get(i);
      try {
        return callGeminiOnce(key, env.geminiModel(), prompt);
      } catch (IOException e) {
        if (!isOverload(e)) {
          throw e;
        }
        lastOverload = e;
        System.err.println(
            "[spike] Gemini key#" + i + " × " + env.geminiModel() + " overloaded → rotating");
      }
    }
    // Step 4: last resort — primary key × fallback model (2.5-flash-lite, deprecating 2026-06)
    System.err.println(
        "[spike] all keys exhausted on primary model → fallback to "
            + env.geminiFallback()
            + " (⚠ 2026-06 EOL)");
    try {
      return callGeminiOnce(env.primaryKey(), env.geminiFallback(), prompt);
    } catch (IOException e) {
      if (isOverload(e) && lastOverload != null) {
        throw new IOException(
            "All Gemini key × model combinations overloaded. Last: " + e.getMessage(), e);
      }
      throw e;
    }
  }

  private static boolean isOverload(IOException e) {
    String msg = e.getMessage() == null ? "" : e.getMessage();
    return msg.contains("503")
        || msg.contains("429")
        || msg.contains("UNAVAILABLE")
        || msg.contains("RESOURCE_EXHAUSTED");
  }

  private static String callGeminiOnce(String key, String model, String prompt)
      throws IOException, InterruptedException {
    ObjectNode root = JSON.createObjectNode();
    ObjectNode content = root.putArray("contents").addObject();
    content.putArray("parts").addObject().put("text", prompt);
    ObjectNode gen = root.putObject("generationConfig");
    gen.put("temperature", 0.1);
    gen.put("maxOutputTokens", 1024);

    String url =
        "https://generativelanguage.googleapis.com/v1beta/models/"
            + model
            + ":generateContent?key="
            + key;
    HttpRequest req =
        HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(root)))
            .build();
    HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
    if (res.statusCode() / 100 != 2) {
      throw new IOException("Gemini " + res.statusCode() + ": " + res.body());
    }
    return res.body();
  }

  /** Extract Gemini's first candidate text (free-form). */
  static String extractGeminiText(String responseJson) throws IOException {
    JsonNode root = JSON.readTree(responseJson);
    JsonNode text = root.at("/candidates/0/content/parts/0/text");
    return text.isMissingNode() ? "" : text.asText();
  }

  // ── Gemini embedContent (for cosine trace only, not Stage A score) ─────
  static double[] callGeminiEmbed(Env env, String text) throws IOException, InterruptedException {
    ObjectNode root = JSON.createObjectNode();
    root.put("model", "models/embedding-001");
    root.putObject("content").putArray("parts").addObject().put("text", text);

    String url =
        "https://generativelanguage.googleapis.com/v1beta/models/embedding-001:embedContent?key="
            + env.primaryKey();
    HttpRequest req =
        HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(20))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(root)))
            .build();
    HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
    if (res.statusCode() / 100 != 2) {
      throw new IOException("Gemini embed " + res.statusCode() + ": " + res.body());
    }
    JsonNode values = JSON.readTree(res.body()).at("/embedding/values");
    double[] out = new double[values.size()];
    for (int i = 0; i < values.size(); i++) {
      out[i] = values.get(i).asDouble();
    }
    return out;
  }

  static double cosine(double[] a, double[] b) {
    double dot = 0;
    double na = 0;
    double nb = 0;
    int n = Math.min(a.length, b.length);
    for (int i = 0; i < n; i++) {
      dot += a[i] * b[i];
      na += a[i] * a[i];
      nb += b[i] * b[i];
    }
    return (na == 0 || nb == 0) ? 0 : dot / (Math.sqrt(na) * Math.sqrt(nb));
  }

  // ── Google Fact Check Tools claims:search ──────────────────────────────
  static String callFactCheck(Env env, String query) throws IOException, InterruptedException {
    String qs =
        "query="
            + URLEncoder.encode(query, StandardCharsets.UTF_8)
            + "&languageCode="
            + env.factcheckLang()
            + "&pageSize=5&key="
            + env.factcheckKey();
    HttpRequest req =
        HttpRequest.newBuilder(
                URI.create("https://factchecktools.googleapis.com/v1alpha1/claims:search?" + qs))
            .timeout(Duration.ofSeconds(15))
            .header("User-Agent", "TruthScope-Spike/2026-04-24")
            .GET()
            .build();
    HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
    if (res.statusCode() / 100 != 2) {
      throw new IOException("FactCheck " + res.statusCode() + ": " + res.body());
    }
    return res.body();
  }

  static int factCheckClaimCount(String responseJson) throws IOException {
    JsonNode claims = JSON.readTree(responseJson).path("claims");
    return claims.isArray() ? claims.size() : 0;
  }

  // ── Article body extraction via Jsoup ──────────────────────────────────
  static Article extractArticle(String url) throws IOException {
    Document doc =
        Jsoup.connect(url)
            .userAgent("Mozilla/5.0 TruthScope-Spike/2026-04-24")
            .timeout(15_000)
            .followRedirects(true)
            .get();
    String title =
        firstNonBlank(
            doc.selectFirst("meta[property=og:title]") != null
                ? doc.selectFirst("meta[property=og:title]").attr("content")
                : null,
            doc.title(),
            "");

    // Try Korean-news-CMS-specific selectors first, then generic.
    String[] selectors = {
      "#articleViewCon", // 뉴스톱 / UCMSnet
      "article.article-body",
      ".article-body",
      "div.text_area", // SBS
      "div.article_cont_area", // SBS alt
      ".news_content",
      ".article_view",
      "#newsBodyArea",
      "article",
      "div[id*=article]",
      "div[class*=article]",
      "div[class*=content]",
      "div[class*=body]"
    };
    Element container = null;
    for (String sel : selectors) {
      Element hit = doc.selectFirst(sel);
      if (hit != null) {
        container = hit;
        break;
      }
    }
    if (container == null) {
      container = doc.body();
    }
    if (container == null) {
      return new Article(url, title, "", null);
    }

    StringBuilder body = new StringBuilder();
    for (Element p : container.select("p")) {
      String t = p.text().strip();
      if (t.length() >= 15) {
        body.append(t).append('\n');
      }
    }
    // Fallback: if <p>-based collection yields too little, use container full text.
    if (body.length() < 200) {
      String raw = container.text().strip();
      if (raw.length() >= 200) {
        body.setLength(0);
        body.append(raw);
      }
    }
    // Last-resort fallback: all <p> anywhere in document
    if (body.length() < 200) {
      StringBuilder all = new StringBuilder();
      for (Element p : doc.select("p")) {
        String t = p.text().strip();
        if (t.length() >= 15) {
          all.append(t).append('\n');
        }
      }
      if (all.length() > body.length()) {
        body.setLength(0);
        body.append(all);
      }
    }
    // Try to extract published_at from og:article:published_time or time[datetime]
    String publishedAt = null;
    Element meta = doc.selectFirst("meta[property=article:published_time]");
    if (meta != null) {
      publishedAt = meta.attr("content");
    }
    if (publishedAt == null || publishedAt.isBlank()) {
      Element time = doc.selectFirst("time[datetime]");
      if (time != null) {
        publishedAt = time.attr("datetime");
      }
    }
    return new Article(url, title, body.toString().strip(), publishedAt);
  }

  record Article(String url, String title, String body, String publishedAt) {}

  private static String firstNonBlank(String... vals) {
    for (String v : vals) {
      if (v != null && !v.isBlank()) {
        return v;
      }
    }
    return "";
  }

  // ── Gold set CSV loader ────────────────────────────────────────────────
  static List<GoldRow> loadGoldSet(Path path) throws IOException {
    List<GoldRow> rows = new ArrayList<>();
    List<String> lines = Files.readAllLines(path);
    if (lines.isEmpty()) {
      return rows;
    }
    // skip header + comment lines
    for (int i = 1; i < lines.size(); i++) {
      String line = lines.get(i);
      if (line.isBlank() || line.startsWith("#")) {
        continue;
      }
      String[] parts = splitCsvLine(line, 8);
      if (parts.length < 8) {
        continue;
      }
      rows.add(
          new GoldRow(
              parts[0],
              parts[1],
              parts[2],
              unquote(parts[3]),
              parts[4],
              unquote(parts[5]),
              parts[6],
              parts[7]));
    }
    return rows;
  }

  private static String[] splitCsvLine(String line, int expected) {
    // Minimal CSV split respecting "quoted,commas"
    List<String> out = new ArrayList<>();
    StringBuilder cur = new StringBuilder();
    boolean inQuote = false;
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (c == '"') {
        inQuote = !inQuote;
        cur.append(c);
      } else if (c == ',' && !inQuote) {
        out.add(cur.toString());
        cur.setLength(0);
      } else {
        cur.append(c);
      }
    }
    out.add(cur.toString());
    return out.toArray(new String[0]);
  }

  private static String unquote(String s) {
    if (s == null) {
      return "";
    }
    String t = s.trim();
    if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
      return t.substring(1, t.length() - 1);
    }
    return t;
  }

  record GoldRow(
      String id,
      String source,
      String url,
      String claimKo,
      String goldVerdict,
      String verdictSourceQuote,
      String collectedAt,
      String notes) {}

  // ── URL accessibility + host extraction ────────────────────────────────
  static int checkAccessibility(String url) {
    try {
      HttpRequest headReq =
          HttpRequest.newBuilder(URI.create(url))
              .timeout(Duration.ofSeconds(10))
              .method("HEAD", HttpRequest.BodyPublishers.noBody())
              .header("User-Agent", "Mozilla/5.0 TruthScope-Spike")
              .build();
      int code = HTTP.send(headReq, HttpResponse.BodyHandlers.discarding()).statusCode();
      if (code / 100 == 2) {
        return code;
      }
      // HEAD blocked — try GET once
      HttpRequest getReq =
          HttpRequest.newBuilder(URI.create(url))
              .timeout(Duration.ofSeconds(15))
              .GET()
              .header("User-Agent", "Mozilla/5.0 TruthScope-Spike")
              .build();
      return HTTP.send(getReq, HttpResponse.BodyHandlers.discarding()).statusCode();
    } catch (Exception e) {
      return -1;
    }
  }

  /** eTLD+1 extraction (best-effort). {@code news.sbs.co.kr → sbs.co.kr}. */
  static String eTldPlusOne(String url) {
    try {
      String host = URI.create(url).getHost();
      if (host == null) {
        return "";
      }
      String[] parts = host.split("\\.");
      if (parts.length <= 2) {
        return host;
      }
      // Handle common 2nd-level TLDs: co.kr, or.kr, go.kr, ac.kr, com.cn, ...
      String penultimate = parts[parts.length - 2];
      if (penultimate.matches("co|or|go|ac|ne|com|org|net")) {
        return parts[parts.length - 3]
            + "."
            + parts[parts.length - 2]
            + "."
            + parts[parts.length - 1];
      }
      return parts[parts.length - 2] + "." + parts[parts.length - 1];
    } catch (Exception e) {
      return "";
    }
  }

  // ── Verdict normalization (6 Korean labels → 5-scale + collapse class) ──
  static int normalizeToScale(String label) {
    if (label == null) {
      return -1;
    }
    String l = label.trim().toLowerCase();
    if (l.contains("판단_보류") || l.contains("판단 보류") || l.contains("hold")) {
      return -1;
    }
    if (l.contains("대체로_거짓") || l.contains("대체로 거짓") || l.contains("mostly false")) {
      return 1;
    }
    if (l.contains("거짓") || l.equals("false")) {
      return 0;
    }
    if (l.contains("절반의_사실") || l.contains("절반의 사실") || l.contains("mixed") || l.contains("half")) {
      return 2;
    }
    if (l.contains("대체로_사실") || l.contains("대체로 사실") || l.contains("mostly true")) {
      return 3;
    }
    if (l.contains("사실") || l.equals("true")) {
      return 4;
    }
    return -1;
  }

  /** Binary-collapse class per rev.5. */
  static String collapseClass(int scale) {
    return switch (scale) {
      case 3, 4 -> "supportive";
      case 0, 1 -> "non-supportive";
      case 2 -> "mixed";
      default -> "hold";
    };
  }

  /** Evidence band per rev.5 Stage A: 4축 × 2점 = 0..8. */
  static String evidenceBand(int s1, int s2, int s3, int s5) {
    int total = s1 + s2 + s3 + s5;
    if (total >= 6) {
      return "strong";
    }
    if (total >= 3) {
      return "weak";
    }
    return "insufficient";
  }

  // ── URL extraction from Gemini free-form text ──────────────────────────
  private static final Pattern URL_PATTERN = Pattern.compile("https?://[\\w./?&=%#\\-]+");

  static List<String> extractUrls(String text) {
    List<String> out = new ArrayList<>();
    if (text == null) {
      return out;
    }
    Matcher m = URL_PATTERN.matcher(text);
    while (m.find()) {
      String raw = m.group();
      // trim trailing punctuation
      while (!raw.isEmpty() && ".,);]'\"".indexOf(raw.charAt(raw.length() - 1)) >= 0) {
        raw = raw.substring(0, raw.length() - 1);
      }
      if (!out.contains(raw)) {
        out.add(raw);
      }
    }
    return out;
  }

  // ── CSV append helper ──────────────────────────────────────────────────
  static String csvCell(Object v) {
    if (v == null) {
      return "";
    }
    String s = v.toString();
    if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
      return "\"" + s.replace("\"", "\"\"") + "\"";
    }
    return s;
  }

  // ── Prompt templates ───────────────────────────────────────────────────
  static String claimPrompt(String title, String body) {
    return """
        다음 뉴스 기사에서 검증 가능한 핵심 사실 주장(claim) 1건을 한 문장으로 추출해주세요.
        숫자·인명·사건·날짜가 포함된 검증 가능한 단일 문장만 반환. 의견·예측 제외.
        출력 형식 (JSON만):
        {"claim": "주장 내용"}

        제목: %s
        본문: %s
        """
        .formatted(truncate(title, 200), truncate(body, 4000));
  }

  static String verdictPrompt(String claim, String articleBody) {
    return """
        다음 주장을 5단계로 판정하고, 판정 근거가 되는 독립 출처 URL을 최소 3건 제시해주세요.

        판정 5단계 중 정확히 하나만 선택: 사실 / 대체로_사실 / 절반의_사실 / 대체로_거짓 / 거짓
        확신할 수 없으면 "판단_보류" 반환.

        출력 형식 (JSON만):
        {
          "verdict": "거짓",
          "reason": "한 문장 근거",
          "sources": ["https://...", "https://...", "https://..."]
        }

        주장: %s
        원 기사 본문 (참고만): %s
        """
        .formatted(claim, truncate(articleBody, 3000));
  }

  private static String truncate(String s, int n) {
    if (s == null) {
      return "";
    }
    return s.length() <= n ? s : s.substring(0, n);
  }

  static Map<String, Object> parseJsonObject(String text) throws IOException {
    // Strip markdown code fence if present
    String cleaned = text.trim();
    if (cleaned.startsWith("```")) {
      int nl = cleaned.indexOf('\n');
      cleaned = nl > 0 ? cleaned.substring(nl + 1) : cleaned;
      if (cleaned.endsWith("```")) {
        cleaned = cleaned.substring(0, cleaned.length() - 3);
      }
    }
    // Extract first {...} block
    int start = cleaned.indexOf('{');
    int end = cleaned.lastIndexOf('}');
    if (start < 0 || end < 0 || end <= start) {
      return Map.of();
    }
    return JSON.readValue(cleaned.substring(start, end + 1), Map.class);
  }
}
