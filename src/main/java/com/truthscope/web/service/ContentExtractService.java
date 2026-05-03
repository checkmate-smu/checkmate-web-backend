package com.truthscope.web.service;

import com.truthscope.web.dto.response.ExtractedArticle;
import com.truthscope.web.exception.ExtractionFailedException;
import com.truthscope.web.exception.SsrfBlockedException;
import com.truthscope.web.html.ArticleHtmlParser;
import com.truthscope.web.security.SsrfGuard;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import lombok.RequiredArgsConstructor;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Timeout;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 뉴스 URL에서 기사 본문을 추출하는 서비스. SSRF 방어를 위해 SsrfGuard로 사전 검증 + Apache HttpClient 5의 custom DnsResolver로
 * DNS rebinding 방어 + per-request build로 connection reuse 차단.
 */
@Service
@RequiredArgsConstructor
public class ContentExtractService {

  private static final Logger log = LoggerFactory.getLogger(ContentExtractService.class);
  private static final int MAX_FETCH_BYTES = 5 * 1024 * 1024; // 5MB
  private static final int MAX_REDIRECTS = 3;
  private static final Timeout CONNECT_TIMEOUT = Timeout.ofSeconds(5);
  private static final Timeout RESPONSE_TIMEOUT = Timeout.ofSeconds(5);
  private static final String USER_AGENT =
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
          + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

  private final SsrfGuard ssrfGuard;

  /**
   * 주어진 URL에서 기사 본문을 추출한다. SsrfGuard로 사전 검증 후 Apache HttpClient 5로 fetch + Jsoup 파싱.
   *
   * @param url 뉴스 기사 URL (http/https만 허용, 사설망/loopback/문서화 대역 차단)
   * @return 제목, 본문, 언어, 도메인이 담긴 ExtractedArticle
   */
  public ExtractedArticle extract(String url) {
    SsrfGuard.ValidatedTarget initialTarget = ssrfGuard.validateAndResolve(url);
    FetchOutcome outcome = fetchWithRedirectGuard(initialTarget);
    String domain = outcome.finalUri().getHost(); // R2-3 fix: redirect 후 final URI host

    Document doc = parseWithCharset(outcome.body(), outcome.charset(), outcome.finalUri());
    String title = ArticleHtmlParser.extractTitle(doc);
    String bodyText = ArticleHtmlParser.extractBody(doc);
    String lang = ArticleHtmlParser.extractLang(doc);

    if (bodyText.isBlank()) {
      throw new ExtractionFailedException("기사 본문을 추출할 수 없습니다");
    }
    return ExtractedArticle.builder().title(title).body(bodyText).lang(lang).domain(domain).build();
  }

  /** Per-extraction redirect loop with re-validation through SsrfGuard. */
  private FetchOutcome fetchWithRedirectGuard(SsrfGuard.ValidatedTarget initialTarget) {
    SsrfGuard.ValidatedTarget currentTarget = initialTarget;
    URI initialUri = initialTarget.uri();
    HttpClientContext sharedContext = HttpClientContext.create();
    sharedContext.setCookieStore(new BasicCookieStore()); // CX-7: per-extraction local
    int redirects = 0;

    while (true) {
      try (CloseableHttpClient client =
          buildClient(currentTarget.approvedAddresses(), currentTarget.host())) {
        HttpGet request = new HttpGet(currentTarget.uri());

        FetchResult result =
            client.execute(request, sharedContext, new FetchResultHandler(initialUri));

        if (result.isRedirect()) {
          if (++redirects > MAX_REDIRECTS) {
            throw new ExtractionFailedException("기사를 가져올 수 없습니다: " + initialUri);
          }
          URI nextUri = parseLocation(result.locationHeader(), currentTarget.uri(), initialUri);
          log.debug(
              "Redirect from {} to {} ({}/{})",
              currentTarget.uri(),
              nextUri,
              redirects,
              MAX_REDIRECTS);
          try {
            currentTarget = ssrfGuard.validateAndResolve(nextUri.toString());
          } catch (SsrfBlockedException e) {
            log.warn("SSRF block on redirect chain: initial={}, blocked={}", initialUri, nextUri);
            throw e;
          }
          continue;
        }
        return new FetchOutcome(result.body(), result.charset(), currentTarget.uri());
      } catch (SsrfBlockedException e) {
        throw e;
      } catch (IOException e) {
        throw new ExtractionFailedException("기사를 가져올 수 없습니다: " + initialUri);
      }
    }
  }

  /** CX-3: execute + responseHandler 패턴. CX-22: charset 추출. */
  private static final class FetchResultHandler implements HttpClientResponseHandler<FetchResult> {
    private final URI initialUri;

    FetchResultHandler(URI uri) {
      this.initialUri = uri;
    }

    @Override
    public FetchResult handleResponse(ClassicHttpResponse response) throws IOException {
      int status = response.getCode();

      // CX-5: 301/302/303/307/308만 redirect로 처리
      boolean isRedirect =
          status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
      if (isRedirect) {
        var locationHeader = response.getFirstHeader("Location");
        if (locationHeader == null) {
          throw new ExtractionFailedException("기사를 가져올 수 없습니다: " + initialUri);
        }
        // R2-4: defensive consume entity before continuing redirect loop
        EntityUtils.consume(response.getEntity());
        return FetchResult.redirect(locationHeader.getValue());
      }
      if (status >= 300) {
        EntityUtils.consume(response.getEntity());
        throw new ExtractionFailedException("기사를 가져올 수 없습니다: " + initialUri);
      }

      // R1-2: null entity check
      HttpEntity entity = response.getEntity();
      if (entity == null) {
        throw new ExtractionFailedException("기사를 가져올 수 없습니다: " + initialUri);
      }
      // CX-22: charset 추출 (Content-Type header)
      Charset charset = extractCharset(entity);
      try {
        // CX-12 + CX-14: bounded read on decompressed stream
        byte[] body = readBoundedBytes(entity.getContent(), MAX_FETCH_BYTES, initialUri);
        return FetchResult.body(body, charset);
      } catch (ExtractionFailedException e) {
        // R2-4: ensure entity is consumed even when bounded read aborts
        try {
          EntityUtils.consume(entity);
        } catch (IOException ignored) {
          // best-effort cleanup
        }
        throw e;
      }
    }

    private static Charset extractCharset(HttpEntity entity) {
      try {
        ContentType ct = ContentType.parse(entity.getContentType());
        if (ct != null && ct.getCharset() != null) {
          return ct.getCharset();
        }
      } catch (Exception ignored) {
        // fallback to Jsoup charset sniffing (META + content)
      }
      return null;
    }
  }

  private record FetchResult(byte[] body, String locationHeader, Charset charset) {
    static FetchResult body(byte[] b, Charset c) {
      return new FetchResult(b, null, c);
    }

    static FetchResult redirect(String loc) {
      return new FetchResult(null, loc, null);
    }

    boolean isRedirect() {
      return locationHeader != null;
    }
  }

  private record FetchOutcome(byte[] body, Charset charset, URI finalUri) {}

  /** CX-6: Location parsing - invalid URI 시 ExtractionFailedException. */
  private URI parseLocation(String location, URI base, URI initial) {
    try {
      URI loc = URI.create(location);
      return loc.isAbsolute() ? loc : base.resolve(loc);
    } catch (IllegalArgumentException e) {
      throw new ExtractionFailedException("기사를 가져올 수 없습니다: " + initial);
    }
  }

  /**
   * CX-1 + R2-1/CX-16: per-request HttpClient with PinnedDnsResolver class. Test seam — Mockito
   * spy로 override 가능.
   */
  protected CloseableHttpClient buildClient(InetAddress[] pinnedAddresses, String approvedHost) {
    var connManager =
        PoolingHttpClientConnectionManagerBuilder.create()
            .setDnsResolver(new PinnedDnsResolver(pinnedAddresses, approvedHost))
            .setDefaultConnectionConfig(
                ConnectionConfig.custom().setConnectTimeout(CONNECT_TIMEOUT).build())
            .build();

    var requestConfig =
        RequestConfig.custom()
            .setCircularRedirectsAllowed(false)
            .setResponseTimeout(RESPONSE_TIMEOUT)
            .build();

    return HttpClients.custom()
        .setConnectionManager(connManager)
        .setDefaultRequestConfig(requestConfig)
        .setUserAgent(USER_AGENT)
        .disableRedirectHandling()
        .setConnectionReuseStrategy((request, response, context) -> false)
        .build();
  }

  /**
   * R2-1/CX-16: DnsResolver는 functional interface 아님 (resolve + resolveCanonicalHostname). 정적 클래스 +
   * defensive copy.
   */
  static final class PinnedDnsResolver implements DnsResolver {
    private final InetAddress[] pinned;
    private final String approvedHost;

    PinnedDnsResolver(InetAddress[] pinned, String approvedHost) {
      // SpotBugs EI_EXPOSE_REP2 회피 + 외부 mutation 방어
      this.pinned = pinned.clone();
      this.approvedHost = approvedHost;
    }

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
      if (host.equalsIgnoreCase(approvedHost)) {
        return pinned.clone(); // SpotBugs EI_EXPOSE_REP 회피
      }
      throw new UnknownHostException("Unauthorized host (SSRF guard): " + host);
    }

    @Override
    public String resolveCanonicalHostname(String host) throws UnknownHostException {
      if (host.equalsIgnoreCase(approvedHost)) {
        return approvedHost;
      }
      throw new UnknownHostException("Unauthorized host (SSRF guard): " + host);
    }
  }

  /** CX-22: Jsoup의 charset 자동 감지 활용 (META + content sniffing). */
  private Document parseWithCharset(byte[] body, Charset charset, URI baseUri) {
    String charsetName = charset == null ? null : charset.name();
    try {
      return Jsoup.parse(new ByteArrayInputStream(body), charsetName, baseUri.toString());
    } catch (IOException e) {
      throw new ExtractionFailedException("기사 본문을 추출할 수 없습니다");
    }
  }

  /** CX-14: check before write로 peak memory cap 정합. */
  private static byte[] readBoundedBytes(InputStream stream, int maxBytes, URI uri)
      throws IOException {
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      byte[] buf = new byte[8192];
      int total = 0;
      int read;
      while ((read = stream.read(buf)) != -1) {
        if (total + read > maxBytes) {
          throw new ExtractionFailedException("기사를 가져올 수 없습니다: " + uri);
        }
        baos.write(buf, 0, read);
        total += read;
      }
      return baos.toByteArray();
    }
  }
}
