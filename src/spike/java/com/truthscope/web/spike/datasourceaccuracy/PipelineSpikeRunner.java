package com.truthscope.web.spike.datasourceaccuracy;

import com.truthscope.web.spike.datasourceaccuracy.SpikeSupport.Article;
import com.truthscope.web.spike.datasourceaccuracy.SpikeSupport.Env;
import com.truthscope.web.spike.datasourceaccuracy.SpikeSupport.GoldRow;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Executes the 2026-04-24 datasource-accuracy spike (rev.5) on every row in {@code gold-set.csv}.
 *
 * <p>Pipeline per row (min-cut, γ mode: Tier 2 single-track):
 *
 * <pre>
 *   URL → Jsoup body → Gemini claim extract
 *                    → Tier 1 GFC check (trace only, per 2026-04-11 ≈0%)
 *                    → Tier 2 Gemini verdict × 3 (pass@3 stability trace)
 *                    → Stage A evidence_band (Gemini-free rule-based)
 *                    → write pipeline-run.csv
 * </pre>
 *
 * <p>Not a JUnit test — single {@code main} entry to keep CI uncoupled. Run from backend root:
 *
 * <pre>
 *   ./gradlew test --tests "...PipelineSpikeRunner" -DskipSpike=false
 * </pre>
 *
 * or directly in the IDE. Expected runtime on N=6: ~3–5 minutes (Gemini 15 RPM throttle).
 */
public final class PipelineSpikeRunner {

  private static final long GEMINI_DELAY_MS = 4500; // free-tier 15 RPM
  private static final long FC_DELAY_MS = 500;

  /**
   * JUnit entry point so this can be driven via {@code gradle test --tests
   * "*PipelineSpikeRunner*"}. Tag "spike" + main() wrapper keeps the class CI-safe (disable when
   * not running). Takes 5~10min on N=9 due to Gemini throttle.
   */
  @Test
  @Tag("spike")
  void runPipelineOnGoldSet() throws Exception {
    main(new String[0]);
  }

  private static final Pattern PRIMARY_HOST =
      Pattern.compile(
          "(?i)(kosis|data\\.go\\.kr|open\\.go\\.kr|kma\\.go\\.kr|bok\\.or\\.kr|kostat\\.go\\.kr"
              + "|who\\.int|cdc\\.gov|nih\\.gov|worldbank\\.org|oecd\\.org|un\\.org|ifs\\.gov\\.uk"
              + "|statistics|stats|\\.go\\.kr|\\.gov)");

  public static void main(String[] args) throws Exception {
    Env env = SpikeSupport.loadEnv();
    List<GoldRow> gold = SpikeSupport.loadGoldSet(SpikeSupport.GOLD_SET_CSV);
    Files.createDirectories(SpikeSupport.RESULTS_DIR);
    Path out = SpikeSupport.RESULTS_DIR.resolve("pipeline-run.csv");

    List<String> header =
        List.of(
            "id",
            "url",
            "c1_url_ok",
            "c2_body_ok",
            "c3_claim_ok",
            "c4_verdict_ok",
            "executable",
            "ambiguous_flag",
            "claim_text",
            "tier1_fc_hits",
            "pass1_verdict",
            "pass2_verdict",
            "pass3_verdict",
            "pass3_stability",
            "pred_verdict",
            "s1_source_count",
            "s2_accessibility_pct",
            "s3_temporal_supportable",
            "s5_diversity",
            "evidence_band",
            "cosine_top",
            "cosine_median",
            "cosine_lowest",
            "error");
    StringBuilder csv = new StringBuilder(String.join(",", header)).append('\n');

    for (GoldRow row : gold) {
      Row r = new Row(row.id(), row.url());
      try {
        runOne(env, row, r);
      } catch (Exception e) {
        r.error = e.getClass().getSimpleName() + ": " + e.getMessage();
      }
      appendRow(csv, r);
    }

    Files.writeString(out, csv.toString());
    System.out.println("Spike complete. Output: " + out.toAbsolutePath());
  }

  private static void runOne(Env env, GoldRow gold, Row r) throws Exception {
    // C1: URL accessibility / article extraction
    Article article;
    try {
      article = SpikeSupport.extractArticle(gold.url());
      r.c1 = true;
    } catch (Exception e) {
      r.error = "extract: " + e.getMessage();
      return;
    }
    // C2: body length gate (≥200 chars per rev.5 — lowered from 300 after N=9 pilot)
    if (article.body().length() < 200) {
      r.c1 = true;
      r.c2 = false;
      r.error = "body too short: " + article.body().length();
      return;
    }
    r.c2 = true;

    // C3: claim input — rev.6 change.
    //
    // Earlier rev.5 had Gemini extract a claim from article body. Pilot run (N=9, 2026-04-24)
    // showed Gemini systematically extracts an already-verified factual sentence from the
    // fact-check article body, biasing Tier 2 verdict toward "사실". That measures
    // claim-extraction more than Tier 2 AI verdict accuracy.
    //
    // Rev.6 fix: use the gold claim directly as verdict input. This isolates Tier 2 verdict
    // accuracy, matching the rev.5 γ-mode framing ("Tier 2 AI 교차검증 단독 measurement").
    // Claim extraction remains a separate concern for future spikes.
    String claim = gold.claimKo();
    if (claim == null || claim.isBlank()) {
      r.error = "gold claim empty";
      return;
    }
    r.claim = claim;
    r.c3 = true;

    // Tier 1 trace: Google FC (expected 0 for Korean per 2026-04-11)
    try {
      r.tier1Hits = SpikeSupport.factCheckClaimCount(SpikeSupport.callFactCheck(env, claim));
    } catch (Exception ignored) {
      r.tier1Hits = -1;
    }
    Thread.sleep(FC_DELAY_MS);

    // Tier 2: Gemini verdict × 3 (pass@3 stability trace).
    // Verdict prompt uses gold claim + article body as context (not Gemini-extracted claim).
    String verdictPrompt = SpikeSupport.verdictPrompt(claim, article.body());
    List<String> verdicts = new ArrayList<>();
    List<Map<String, Object>> verdictObjs = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      try {
        String raw = SpikeSupport.callGeminiGenerate(env, verdictPrompt);
        Map<String, Object> obj = SpikeSupport.parseJsonObject(SpikeSupport.extractGeminiText(raw));
        verdicts.add(String.valueOf(obj.getOrDefault("verdict", "")));
        verdictObjs.add(obj);
      } catch (Exception e) {
        verdicts.add("ERROR:" + e.getClass().getSimpleName());
        verdictObjs.add(Map.of());
      }
      if (i < 2) {
        Thread.sleep(GEMINI_DELAY_MS);
      }
    }
    r.v1 = verdicts.get(0);
    r.v2 = verdicts.get(1);
    r.v3 = verdicts.get(2);
    r.c4 = verdicts.stream().noneMatch(v -> v.startsWith("ERROR:"));
    if (!r.c4) {
      r.error =
          "verdict call failed ×" + verdicts.stream().filter(v -> v.startsWith("ERROR:")).count();
      return;
    }
    r.pass3Stability = classifyStability(r.v1, r.v2, r.v3);
    r.pred = pickPred(r.pass3Stability, r.v1, r.v2, r.v3);

    // Collect union of source URLs across 3 passes
    Set<String> sources = new HashSet<>();
    for (Map<String, Object> obj : verdictObjs) {
      Object src = obj.get("sources");
      if (src instanceof List<?> list) {
        for (Object u : list) {
          if (u != null && !u.toString().isBlank()) {
            sources.add(u.toString());
          }
        }
      }
    }

    // Stage A (Gemini-free rule-based) — S1/S2/S3/S5
    r.s1 = scoreSourceCount(sources.size());
    r.s2Pct = scoreAccessibility(sources);
    r.s2 = s2Band(r.s2Pct);
    r.s3 = scoreTemporalSupportability(article.publishedAt(), sources);
    r.s5 = scoreDiversity(sources);
    r.evidenceBand = SpikeSupport.evidenceBand(r.s1, r.s2, r.s3, r.s5);

    // Cosine trace (NOT part of Stage A score — pure diagnostic per rev.5)
    traceCosine(env, claim, sources, r);
  }

  // ── Stage A scoring (Gemini-free) ──────────────────────────────────────
  private static int scoreSourceCount(int n) {
    if (n >= 3) {
      return 2;
    }
    if (n == 2) {
      return 1;
    }
    return 0;
  }

  private static int scoreAccessibility(Set<String> urls) {
    if (urls.isEmpty()) {
      return 0;
    }
    int ok = 0;
    for (String u : urls) {
      int code = SpikeSupport.checkAccessibility(u);
      if (code / 100 == 2) {
        ok++;
      }
    }
    return (int) Math.round(100.0 * ok / urls.size());
  }

  private static int s2Band(int pct) {
    if (pct >= 80) {
      return 2;
    }
    if (pct >= 50) {
      return 1;
    }
    return 0;
  }

  private static int scoreTemporalSupportability(String articlePublishedIso, Set<String> sources) {
    if (sources.isEmpty()) {
      return 0;
    }
    OffsetDateTime articleAt = tryParseIso(articlePublishedIso);
    boolean hasPrimary = sources.stream().anyMatch(u -> PRIMARY_HOST.matcher(u).find());
    // Rev.5 S3 Temporal Supportability:
    //   2 = article-이전 출처 ≥ 1 OR 원자료(primary) 포함
    //   1 = 대부분 article 이후 + 원자료 없음
    //   0 = 날짜 파싱 불가 또는 출처 0건
    if (articleAt == null) {
      // No article timestamp — fall back to primary-host signal only
      return hasPrimary ? 2 : 0;
    }
    if (hasPrimary) {
      return 2;
    }
    // Without source-level dates we can't prove "before article" — conservative 1
    return 1;
  }

  private static OffsetDateTime tryParseIso(String iso) {
    if (iso == null || iso.isBlank()) {
      return null;
    }
    try {
      return OffsetDateTime.parse(iso);
    } catch (Exception e) {
      return null;
    }
  }

  private static int scoreDiversity(Set<String> sources) {
    Set<String> hosts = new HashSet<>();
    for (String u : sources) {
      String h = SpikeSupport.eTldPlusOne(u);
      if (!h.isBlank()) {
        hosts.add(h);
      }
    }
    if (hosts.size() >= 3) {
      return 2;
    }
    if (hosts.size() == 2) {
      return 1;
    }
    return 0;
  }

  // ── pass@3 stability classifier (rev.5 R4: trace only, not majority vote for pred) ───
  private static String classifyStability(String v1, String v2, String v3) {
    String n1 = normalize(v1);
    String n2 = normalize(v2);
    String n3 = normalize(v3);
    if (n1.equals(n2) && n2.equals(n3)) {
      return "stable-consensus";
    }
    if (n1.equals(n2) || n1.equals(n3) || n2.equals(n3)) {
      return "unstable-majority";
    }
    return "no-consensus";
  }

  private static String normalize(String s) {
    if (s == null) {
      return "";
    }
    // Strip whitespace, trailing punctuation, and collapse internal spaces/underscores
    return s.trim().replaceAll("[\\s_.,;:!?\"']+", "").toLowerCase();
  }

  /**
   * For rev.5 the pred is reported per run but aggregation uses "all executable cases". We pick the
   * first non-empty verdict as the canonical pred; pass@3 is a separate stability trace.
   */
  private static String pickPred(String stability, String v1, String v2, String v3) {
    for (String v : new String[] {v1, v2, v3}) {
      if (v != null && !v.isBlank() && !v.startsWith("ERROR:")) {
        return v;
      }
    }
    return "";
  }

  private static void traceCosine(Env env, String claim, Set<String> sources, Row r) {
    if (sources.isEmpty()) {
      return;
    }
    try {
      double[] claimEmb = SpikeSupport.callGeminiEmbed(env, claim);
      List<Double> cos = new ArrayList<>();
      for (String u : sources) {
        // For speed: we don't re-fetch each source body. Use the URL text as a weak proxy.
        // This is a diagnostic trace, not a scoring input — per rev.5 rubric.
        try {
          double[] e = SpikeSupport.callGeminiEmbed(env, u);
          cos.add(SpikeSupport.cosine(claimEmb, e));
          Thread.sleep(GEMINI_DELAY_MS);
        } catch (Exception ignored) {
          // skip on per-URL failure
        }
      }
      if (cos.isEmpty()) {
        return;
      }
      cos.sort(Double::compareTo);
      r.cosineLowest = cos.get(0);
      r.cosineTop = cos.get(cos.size() - 1);
      r.cosineMedian = cos.get(cos.size() / 2);
    } catch (Exception ignored) {
      // Embedding is optional diagnostic — ignore failures
    }
  }

  // ── Row writer ─────────────────────────────────────────────────────────
  private static void appendRow(StringBuilder csv, Row r) {
    List<Object> cells =
        List.of(
            r.id,
            r.url,
            r.c1,
            r.c2,
            r.c3,
            r.c4,
            r.executable(),
            r.ambiguousFlag,
            orEmpty(r.claim),
            r.tier1Hits,
            orEmpty(r.v1),
            orEmpty(r.v2),
            orEmpty(r.v3),
            orEmpty(r.pass3Stability),
            orEmpty(r.pred),
            r.s1,
            r.s2Pct,
            r.s3,
            r.s5,
            orEmpty(r.evidenceBand),
            r.cosineTop,
            r.cosineMedian,
            r.cosineLowest,
            orEmpty(r.error));
    csv.append(cells.stream().map(SpikeSupport::csvCell).reduce((a, b) -> a + "," + b).orElse(""));
    csv.append('\n');
  }

  private static Object orEmpty(String s) {
    return s == null ? "" : s;
  }

  // ── Per-row state ──────────────────────────────────────────────────────
  private static final class Row {
    final String id;
    final String url;
    boolean c1;
    boolean c2;
    boolean c3;
    boolean c4;
    boolean ambiguousFlag;
    String claim;
    int tier1Hits = -1;
    String v1;
    String v2;
    String v3;
    String pass3Stability;
    String pred;
    int s1;
    int s2;
    int s2Pct;
    int s3;
    int s5;
    String evidenceBand;
    double cosineTop = Double.NaN;
    double cosineMedian = Double.NaN;
    double cosineLowest = Double.NaN;
    String error = "";

    Row(String id, String url) {
      this.id = id;
      this.url = url;
    }

    boolean executable() {
      return c1 && c2 && c3 && c4;
    }
  }
}
