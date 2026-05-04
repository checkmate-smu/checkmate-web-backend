package com.truthscope.web.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import com.truthscope.web.exception.SsrfBlockedException;
import com.truthscope.web.security.SsrfGuard;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ContentExtractService Real-socket 테스트 — 127.0.0.1 직접 차단 시나리오.
 *
 * <p>네이밍 정책: {@code *IntegrationTest}는 {@code AbstractIntegrationTest} 상속 + Spring 컨텍스트 의무
 * (CodeRabbit #5). 본 테스트는 Spring 컨텍스트 + DB 미필요로 직접 construction 사용 → {@code *RealSocketTest} 접미사로
 * 분리.
 */
@DisplayName("ContentExtractService Real-socket 테스트 (127.0.0.1 차단)")
class ContentExtractServiceRealSocketTest {

  private static HttpServer localServer;

  private ContentExtractService service;

  @BeforeAll
  static void startServer() throws IOException {
    localServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    localServer.createContext(
        "/",
        exchange -> {
          byte[] body =
              "<html><body><article>Local server content</article></body></html>"
                  .getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    localServer.start();
  }

  @AfterAll
  static void stopServer() {
    if (localServer != null) {
      localServer.stop(0);
    }
  }

  @BeforeEach
  void setUp() {
    service = new ContentExtractService(new SsrfGuard());
  }

  @Test
  @DisplayName("127.0.0.1 직접 요청 → SsrfBlockedException")
  void block127LoopbackDirectRequest() {
    int port = localServer.getAddress().getPort();
    String url = "http://127.0.0.1:" + port + "/";
    assertThatThrownBy(() -> service.extract(url))
        .isInstanceOf(SsrfBlockedException.class)
        .hasMessage("내부 네트워크 주소는 차단되었습니다");
  }
}
