package com.truthscope.web.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.truthscope.web.exception.BadRequestException;
import com.truthscope.web.exception.SsrfBlockedException;
import java.net.InetAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

@DisplayName("SsrfGuard - SSRF policy validation (TDD)")
class SsrfGuardTest {

  private SsrfGuard guard;

  @BeforeEach
  void setUp() {
    guard = new SsrfGuard();
  }

  // ── Scheme 검증 (7건) ──────────────────────────────────────────────────────
  @Nested
  @DisplayName("Scheme 검증 (7건)")
  class SchemeValidation {

    @Test
    @DisplayName("http allow → 정상 통과 (외부 IP)")
    void httpAllowed() {
      var result = guard.validateAndResolve("http://8.8.8.8/");
      assertThat(result.scheme()).isEqualTo("http");
    }

    @Test
    @DisplayName("https allow → 정상 통과 (외부 IP)")
    void httpsAllowed() {
      var result = guard.validateAndResolve("https://8.8.8.8/");
      assertThat(result.scheme()).isEqualTo("https");
    }

    @Test
    @DisplayName("ftp:// 거부 → BadRequestException")
    void ftpDenied() {
      assertThatThrownBy(() -> guard.validateAndResolve("ftp://example.com/file"))
          .isInstanceOf(BadRequestException.class)
          .hasMessage("유효하지 않은 URL 형식입니다");
    }

    @Test
    @DisplayName("file:// 거부 → BadRequestException")
    void fileDenied() {
      assertThatThrownBy(() -> guard.validateAndResolve("file:///etc/passwd"))
          .isInstanceOf(BadRequestException.class)
          .hasMessage("유효하지 않은 URL 형식입니다");
    }

    @Test
    @DisplayName("data: 거부 → BadRequestException")
    void dataDenied() {
      assertThatThrownBy(() -> guard.validateAndResolve("data:text/html,<script>alert(1)</script>"))
          .isInstanceOf(BadRequestException.class)
          .hasMessage("유효하지 않은 URL 형식입니다");
    }

    @Test
    @DisplayName("javascript: 거부 → BadRequestException")
    void javascriptDenied() {
      assertThatThrownBy(() -> guard.validateAndResolve("javascript:alert(1)"))
          .isInstanceOf(BadRequestException.class)
          .hasMessage("유효하지 않은 URL 형식입니다");
    }

    @Test
    @DisplayName("null scheme (relative URL //example.com) 거부 → BadRequestException")
    void nullSchemeDenied() {
      assertThatThrownBy(() -> guard.validateAndResolve("//example.com/path"))
          .isInstanceOf(BadRequestException.class)
          .hasMessage("유효하지 않은 URL 형식입니다");
    }
  }

  // ── URL 형식 (4건) ─────────────────────────────────────────────────────────
  @Nested
  @DisplayName("URL 형식 검증 (4건)")
  class UrlFormatValidation {

    @Test
    @DisplayName("null URL → BadRequestException")
    void nullUrl() {
      assertThatThrownBy(() -> guard.validateAndResolve(null))
          .isInstanceOf(BadRequestException.class)
          .hasMessage("URL은 필수입니다");
    }

    @Test
    @DisplayName("blank URL → BadRequestException")
    void blankUrl() {
      assertThatThrownBy(() -> guard.validateAndResolve("   "))
          .isInstanceOf(BadRequestException.class)
          .hasMessage("URL은 필수입니다");
    }

    @Test
    @DisplayName("malformed URI → BadRequestException")
    void malformedUri() {
      assertThatThrownBy(() -> guard.validateAndResolve("ht!tp://malformed||url"))
          .isInstanceOf(BadRequestException.class)
          .hasMessage("유효하지 않은 URL 형식입니다");
    }

    @Test
    @DisplayName("host null (http:/path 형식) → BadRequestException")
    void hostNull() {
      assertThatThrownBy(() -> guard.validateAndResolve("http:/no-authority"))
          .isInstanceOf(BadRequestException.class)
          .hasMessage("유효하지 않은 URL 형식입니다");
    }
  }

  // ── IPv4 deny CIDR (15건) ──────────────────────────────────────────────────
  @Nested
  @DisplayName("IPv4 deny CIDR (15건 - IANA non-global)")
  class IPv4DenyCidr {

    @ParameterizedTest(name = "{index}: {0}")
    @ValueSource(
        strings = {
          "127.0.0.1", // loopback
          "10.0.0.1", // RFC1918 private
          "172.16.0.1", // RFC1918 private
          "192.168.1.1", // RFC1918 private
          "169.254.169.254", // link-local (AWS/cloud metadata)
          "0.0.0.1", // current network
          "100.64.0.1", // CGN
          "198.18.0.1", // benchmark
          "224.0.0.1", // multicast
          "255.255.255.255", // broadcast
          "192.0.0.1", // IETF protocol assignments
          "192.0.2.1", // TEST-NET-1 documentation
          "198.51.100.1", // TEST-NET-2 documentation
          "203.0.113.1", // TEST-NET-3 documentation
          "240.0.0.1" // reserved for future use
        })
    void ipv4Denied(String ip) {
      assertThatThrownBy(() -> guard.validateAndResolve("http://" + ip + "/"))
          .isInstanceOf(SsrfBlockedException.class)
          .hasMessage("내부 네트워크 주소는 차단되었습니다");
    }
  }

  // ── IPv6 deny CIDR (8건) ───────────────────────────────────────────────────
  @Nested
  @DisplayName("IPv6 deny CIDR (8건)")
  class IPv6DenyCidr {

    @Test
    @DisplayName("::1 loopback")
    void ipv6Loopback() {
      assertThatThrownBy(() -> guard.validateAndResolve("http://[::1]/"))
          .isInstanceOf(SsrfBlockedException.class)
          .hasMessage("내부 네트워크 주소는 차단되었습니다");
    }

    @Test
    @DisplayName("fe80::1 link-local")
    void ipv6LinkLocal() {
      assertThatThrownBy(() -> guard.validateAndResolve("http://[fe80::1]/"))
          .isInstanceOf(SsrfBlockedException.class);
    }

    @Test
    @DisplayName("fc00::1 ULA")
    void ipv6Ula() {
      assertThatThrownBy(() -> guard.validateAndResolve("http://[fc00::1]/"))
          .isInstanceOf(SsrfBlockedException.class);
    }

    @Test
    @DisplayName("ff00::1 multicast")
    void ipv6Multicast() {
      assertThatThrownBy(() -> guard.validateAndResolve("http://[ff00::1]/"))
          .isInstanceOf(SsrfBlockedException.class);
    }

    @Test
    @DisplayName(":: unspecified")
    void ipv6Unspecified() {
      assertThatThrownBy(() -> guard.validateAndResolve("http://[::]/"))
          .isInstanceOf(SsrfBlockedException.class);
    }

    @Test
    @DisplayName("2001:db8::1 documentation (RFC 3849)")
    void ipv6Documentation() {
      assertThatThrownBy(() -> guard.validateAndResolve("http://[2001:db8::1]/"))
          .isInstanceOf(SsrfBlockedException.class);
    }

    @Test
    @DisplayName("3fff::1 documentation (RFC 9637)")
    void ipv6Documentation2() {
      assertThatThrownBy(() -> guard.validateAndResolve("http://[3fff::1]/"))
          .isInstanceOf(SsrfBlockedException.class);
    }

    @Test
    @DisplayName("IPv6 bracket form with port (R4-1: bracket strip 정합)")
    void ipv6BracketFormWithPort() {
      assertThatThrownBy(() -> guard.validateAndResolve("http://[::1]:8080/admin"))
          .isInstanceOf(SsrfBlockedException.class);
    }
  }

  // ── IPv4-mapped IPv6 unmap (3건) ──────────────────────────────────────────
  @Nested
  @DisplayName("IPv4-mapped IPv6 unmap (3건 - RFC 4291 §2.5.5.2)")
  class IPv4MappedIPv6 {

    @Test
    @DisplayName("::ffff:127.0.0.1 → loopback deny (unmap 후 IPv4 deny CIDR 매칭)")
    void mappedLoopbackDeny() {
      assertThatThrownBy(() -> guard.validateAndResolve("http://[::ffff:127.0.0.1]/"))
          .isInstanceOf(SsrfBlockedException.class);
    }

    @Test
    @DisplayName("::ffff:8.8.8.8 → 외부 IP allow (unmap 후 정상 외부)")
    void mappedExternalAllow() {
      var result = guard.validateAndResolve("http://[::ffff:8.8.8.8]/");
      assertThat(result.host()).isNotNull();
      assertThat(result.approvedAddresses()).isNotEmpty();
    }

    @Test
    @DisplayName("Java InetAddress 자동 변환 (::ffff:10.0.0.1) → 사설망 deny")
    void javaAutoConvertPrivate() {
      assertThatThrownBy(() -> guard.validateAndResolve("http://[::ffff:10.0.0.1]/"))
          .isInstanceOf(SsrfBlockedException.class);
    }
  }

  // ── Allow 외부 정상 IP (2건) ───────────────────────────────────────────────
  @Nested
  @DisplayName("Allow 외부 정상 IP (2건)")
  class AllowedExternal {

    @Test
    @DisplayName("정상 외부 IPv4 (8.8.8.8) allow")
    void externalIpv4Allowed() {
      var result = guard.validateAndResolve("http://8.8.8.8/");
      assertThat(result.scheme()).isEqualTo("http");
      assertThat(result.host()).isEqualTo("8.8.8.8");
      assertThat(result.approvedAddresses()).isNotEmpty();
    }

    @Test
    @DisplayName("정상 외부 IPv6 (2606:4700:4700::1111 Cloudflare) allow")
    void externalIpv6Allowed() {
      var result = guard.validateAndResolve("http://[2606:4700:4700::1111]/");
      assertThat(result.scheme()).isEqualTo("http");
      assertThat(result.host()).isEqualTo("2606:4700:4700::1111");
    }
  }

  // ── DNS rebinding 방어 (2건) ──────────────────────────────────────────────
  @Nested
  @DisplayName("DNS rebinding 방어 (2건)")
  class DnsRebinding {

    @Test
    @DisplayName("resolveHost 첫 호출 결과를 ValidatedTarget.approvedAddresses에 pin")
    void firstResolutionPinned() throws Exception {
      SsrfGuard spyGuard = Mockito.spy(new SsrfGuard());
      InetAddress[] firstResolution = new InetAddress[] {InetAddress.getByName("8.8.8.8")};
      Mockito.doReturn(firstResolution).when(spyGuard).resolveHost("news.example.com");

      var result = spyGuard.validateAndResolve("http://news.example.com/article");

      assertThat(result.approvedAddresses()).containsExactly(firstResolution);
      Mockito.verify(spyGuard, Mockito.times(1)).resolveHost("news.example.com");
    }

    @Test
    @DisplayName("resolveHost 첫 호출에서 blocked IP 반환 시 즉시 SsrfBlockedException")
    void firstResolutionBlocked() throws Exception {
      SsrfGuard spyGuard = Mockito.spy(new SsrfGuard());
      InetAddress[] blockedResolution = new InetAddress[] {InetAddress.getByName("127.0.0.1")};
      Mockito.doReturn(blockedResolution).when(spyGuard).resolveHost("evil.example.com");

      assertThatThrownBy(() -> spyGuard.validateAndResolve("http://evil.example.com/"))
          .isInstanceOf(SsrfBlockedException.class)
          .hasMessage("내부 네트워크 주소는 차단되었습니다");
    }
  }
}
