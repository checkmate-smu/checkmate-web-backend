package com.truthscope.web.spike.datasourceaccuracy;

import com.truthscope.web.spike.datasourceaccuracy.SpikeSupport.GoldRow;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Gold Agreement Report — 2026-04-24 spike rev.5.
 *
 * <p>Joins {@code gold-set.csv} with {@code pipeline-run.csv} (produced by {@link
 * PipelineSpikeRunner}) and writes {@code agreement-report.csv}. No pass/fail assertions — per
 * rev.5 Codex R4 guidance, N=6–10 is too small to defend a fixed threshold.
 *
 * <p>Primary metric: <strong>binary-collapse agreement</strong> on all V1 executable cases. Exact
 * match reported as secondary.
 *
 * @see "evaluation-rubric.md rev.5 §5"
 */
@Tag("spike")
@Disabled("spike — verified 2026-04-24 rev.6. Enable manually to re-run after pipeline update.")
class GoldAgreementReportTest {

  @Test
  void produceAgreementReport() throws Exception {
    Path goldPath = SpikeSupport.GOLD_SET_CSV;
    Path runPath = SpikeSupport.RESULTS_DIR.resolve("pipeline-run.csv");
    Path reportPath = SpikeSupport.RESULTS_DIR.resolve("agreement-report.csv");
    Path summaryPath = SpikeSupport.RESULTS_DIR.resolve("findings-summary.md");

    if (!Files.exists(runPath)) {
      throw new IllegalStateException(
          "pipeline-run.csv missing — run PipelineSpikeRunner.main() first: " + runPath);
    }

    List<GoldRow> gold = SpikeSupport.loadGoldSet(goldPath);
    Map<String, GoldRow> goldById = new HashMap<>();
    for (GoldRow g : gold) {
      goldById.put(g.id(), g);
    }

    List<String[]> runRows = loadCsv(runPath);
    if (runRows.isEmpty()) {
      throw new IllegalStateException("pipeline-run.csv has no rows");
    }
    String[] runHeader = runRows.get(0);
    Map<String, Integer> col = new HashMap<>();
    for (int i = 0; i < runHeader.length; i++) {
      col.put(runHeader[i], i);
    }

    StringBuilder report =
        new StringBuilder(
            "id,url,gold_scale,pred_scale,gold_collapse,pred_collapse,"
                + "exact_match,binary_collapse_match,mixed_case,hold_case,evidence_band,"
                + "pass3_stability,ambiguous_flag\n");

    Counters c = new Counters();
    for (int i = 1; i < runRows.size(); i++) {
      String[] row = runRows.get(i);
      if (row.length < runHeader.length) {
        continue;
      }
      String id = row[col.get("id")];
      boolean executable = "true".equalsIgnoreCase(row[col.get("executable")]);
      GoldRow g = goldById.get(id);
      if (g == null) {
        continue;
      }

      int goldScale = SpikeSupport.normalizeToScale(g.goldVerdict());
      String goldCollapse = SpikeSupport.collapseClass(goldScale);

      int predScale = -1;
      String predCollapse = "hold";
      if (executable) {
        predScale = SpikeSupport.normalizeToScale(row[col.get("pred_verdict")]);
        predCollapse = SpikeSupport.collapseClass(predScale);
      }

      boolean mixedCase = "mixed".equals(goldCollapse) || "mixed".equals(predCollapse);
      boolean holdCase = "hold".equals(goldCollapse) || "hold".equals(predCollapse);

      boolean exactMatch = executable && predScale == goldScale;
      boolean binaryMatch =
          executable && !mixedCase && !holdCase && goldCollapse.equals(predCollapse);

      // Aggregate
      c.total++;
      if (executable) {
        c.executable++;
        String band = row[col.get("evidence_band")];
        if ("strong".equals(band)) {
          c.evidenceStrong++;
        } else if ("weak".equals(band)) {
          c.evidenceWeak++;
        } else if ("insufficient".equals(band)) {
          c.insufficient++;
        }

        if (!mixedCase && !holdCase) {
          c.binaryEligible++;
          if (binaryMatch) {
            c.binaryMatch++;
          }
          if (exactMatch) {
            c.exactMatch++;
          }
        }
        if (mixedCase) {
          c.mixedCases++;
        }
        if (holdCase) {
          c.holdCases++;
        }
      }

      appendReportRow(
          report,
          id,
          row[col.get("url")],
          goldScale,
          predScale,
          goldCollapse,
          predCollapse,
          exactMatch,
          binaryMatch,
          mixedCase,
          holdCase,
          row[col.get("evidence_band")],
          row[col.get("pass3_stability")],
          row[col.get("ambiguous_flag")]);
    }

    Files.createDirectories(SpikeSupport.RESULTS_DIR);
    Files.writeString(reportPath, report.toString());
    Files.writeString(summaryPath, renderSummary(c));

    System.out.println("Agreement report: " + reportPath.toAbsolutePath());
    System.out.println("Findings summary: " + summaryPath.toAbsolutePath());
    System.out.println(renderSummary(c));
  }

  private static List<String[]> loadCsv(Path path) throws java.io.IOException {
    List<String[]> out = new ArrayList<>();
    for (String line : Files.readAllLines(path)) {
      if (line.isBlank()) {
        continue;
      }
      out.add(splitCsv(line));
    }
    return out;
  }

  private static String[] splitCsv(String line) {
    // Same tolerant split as SpikeSupport but inlined
    List<String> out = new ArrayList<>();
    StringBuilder cur = new StringBuilder();
    boolean inQuote = false;
    for (int i = 0; i < line.length(); i++) {
      char ch = line.charAt(i);
      if (ch == '"') {
        inQuote = !inQuote;
      } else if (ch == ',' && !inQuote) {
        out.add(cur.toString());
        cur.setLength(0);
      } else {
        cur.append(ch);
      }
    }
    out.add(cur.toString());
    return out.toArray(new String[0]);
  }

  private static void appendReportRow(
      StringBuilder report,
      String id,
      String url,
      int goldScale,
      int predScale,
      String goldCollapse,
      String predCollapse,
      boolean exactMatch,
      boolean binaryMatch,
      boolean mixedCase,
      boolean holdCase,
      String evidenceBand,
      String stability,
      String ambiguousFlag) {
    List<Object> cells =
        List.of(
            id,
            url,
            goldScale,
            predScale,
            goldCollapse,
            predCollapse,
            exactMatch,
            binaryMatch,
            mixedCase,
            holdCase,
            evidenceBand == null ? "" : evidenceBand,
            stability == null ? "" : stability,
            ambiguousFlag == null ? "" : ambiguousFlag);
    report.append(
        cells.stream().map(SpikeSupport::csvCell).reduce((a, b) -> a + "," + b).orElse(""));
    report.append('\n');
  }

  private static String renderSummary(Counters c) {
    double execRate = safeDiv(c.executable, c.total);
    double strongRate = safeDiv(c.evidenceStrong, c.executable);
    double binary = safeDiv(c.binaryMatch, c.binaryEligible);
    double exact = safeDiv(c.exactMatch, c.binaryEligible);

    return """
        # Spike rev.5 Findings (auto-generated)

        > Feasibility snapshot — NOT a statistical generalization. N = %d.

        ## Stage A — Execution / Evidence

        | metric | value |
        |---|---|
        | total samples | %d |
        | executable (V1) | %d (%.0f%%) |
        | evidence-strong | %d (%.0f%% of V1) |
        | evidence-weak | %d |
        | insufficient | %d |

        ## Stage B — Verdict Agreement (binary-collapse eligible only)

        | metric | value | note |
        |---|---|---|
        | binary-collapse agreement | %d / %d (%.0f%%) | ⭐ primary |
        | exact match (5-scale) | %d / %d (%.0f%%) | secondary |
        | mixed cases (excluded) | %d | separate table |
        | hold cases (excluded) | %d | separate table |

        ## Defense stance

        This is a **protocol feasibility run**, not an accuracy claim. IFCN cited only as reference;
        primary grounding: FEVER evidence-needed lesson, FACT5 small-sample ordinal caution,
        NewsGuard source-gathering logic. Next iteration: wider N + 2-annotator manual review.
        """
        .formatted(
            c.total,
            c.total,
            c.executable,
            100 * execRate,
            c.evidenceStrong,
            100 * strongRate,
            c.evidenceWeak,
            c.insufficient,
            c.binaryMatch,
            c.binaryEligible,
            100 * binary,
            c.exactMatch,
            c.binaryEligible,
            100 * exact,
            c.mixedCases,
            c.holdCases);
  }

  private static double safeDiv(int num, int denom) {
    return denom == 0 ? 0.0 : (double) num / denom;
  }

  private static final class Counters {
    int total;
    int executable;
    int evidenceStrong;
    int evidenceWeak;
    int insufficient;
    int binaryEligible;
    int binaryMatch;
    int exactMatch;
    int mixedCases;
    int holdCases;
  }
}
