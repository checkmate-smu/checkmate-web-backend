package com.truthscope.web;

import com.truthscope.web.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * Application context smoke test.
 *
 * <p>Testcontainers PostgreSQL이 {@link AbstractIntegrationTest}에서 자동 기동·주입되므로 외부 Supabase 의존 없이
 * Spring context 로딩이 검증된다. Docker가 없는 환경에서는 testcontainers가 자체 스킵.
 */
class TruthScopeWebBackendApplicationTests extends AbstractIntegrationTest {

  @Test
  void contextLoads() {
    // Spring context가 Testcontainers Postgres에 ddl-auto=validate로 연결되는지만 검증.
  }
}
