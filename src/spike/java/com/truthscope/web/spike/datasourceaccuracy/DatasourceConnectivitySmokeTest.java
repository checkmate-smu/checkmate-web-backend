package com.truthscope.web.spike.datasourceaccuracy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Connectivity smoke for the 2026-04-24 datasource-accuracy spike (rev.5).
 *
 * <p>Verifies Gemini {@code generateContent} + Google Fact Check {@code claims:search} are
 * reachable with current {@code .env.local} credentials. CI-disabled. Enable manually via IDE or
 * {@code gradle test --tests ...SmokeTest -DskipSpike=false} after setting
 * {@code @Disabled(false)}.
 *
 * <p>Pass criterion: both calls return 2xx and parseable JSON. Fact-check {@code claims} empty is
 * <em>not</em> a failure — per 2026-04-11 prior spike, Korean FC coverage ≈ 0%. This test only
 * asserts the endpoint responds and the response shape is valid.
 *
 * @see "precedent-rubrics.md" FEVER / NewsGuard citations
 * @see ".plans/19-plan-v1.2/pm-spike/2026-04-24-datasource-accuracy/evaluation-rubric.md"
 */
@Tag("spike")
@Disabled("spike — verified 2026-04-24 with key-rotation logic. Enable manually to re-run.")
class DatasourceConnectivitySmokeTest {

  @Test
  void gemini_respondsWithParseableText() throws Exception {
    SpikeSupport.Env env = SpikeSupport.loadEnv();
    String body = SpikeSupport.callGeminiGenerate(env, "한 문장으로 답해주세요: 대한민국의 수도는?");
    String text = SpikeSupport.extractGeminiText(body);
    assertThat(text).as("Gemini candidates[0] text not empty").isNotBlank();
    assertThat(text.toLowerCase()).contains("서울");
  }

  @Test
  void googleFactCheck_respondsEvenWhenEmpty() throws Exception {
    SpikeSupport.Env env = SpikeSupport.loadEnv();
    String body = SpikeSupport.callFactCheck(env, "대한민국 수도는 서울이다");
    int n = SpikeSupport.factCheckClaimCount(body);
    // Empty is OK — 2026-04-11 prior spike showed Korean FC coverage ≈ 0%.
    assertThat(n).as("claim count is a valid integer (0 allowed)").isGreaterThanOrEqualTo(0);
  }
}
