package com.truthscope.web.security;

import com.truthscope.web.exception.BadRequestException;
import com.truthscope.web.exception.SsrfBlockedException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

/**
 * Validates user-controlled outbound HTTP(S) fetch targets against SSRF policy.
 *
 * <p>Resolves the host to InetAddress[] and rejects any address matching IANA non-global
 * special-purpose CIDRs (private, loopback, link-local, multicast, broadcast, CGN, benchmark, ULA,
 * documentation, reserved). Use the returned approvedAddresses as the pinned set for a
 * request-scoped DnsResolver to prevent DNS rebinding.
 */
@Component
public class SsrfGuard {

  private static final Logger log = LoggerFactory.getLogger(SsrfGuard.class);

  // IPv4 deny CIDRs (IANA non-global SSRF deny list, 15건)
  private static final List<String> IPV4_DENY_CIDRS =
      List.of(
          "127.0.0.0/8", // loopback
          "10.0.0.0/8", // RFC1918 private
          "172.16.0.0/12", // RFC1918 private
          "192.168.0.0/16", // RFC1918 private
          "169.254.0.0/16", // link-local incl. cloud metadata 169.254.169.254
          "0.0.0.0/8", // current network
          "100.64.0.0/10", // CGN
          "198.18.0.0/15", // benchmark
          "224.0.0.0/4", // multicast
          "255.255.255.255/32", // broadcast
          "192.0.0.0/24", // IETF protocol assignments
          "192.0.2.0/24", // TEST-NET-1 documentation
          "198.51.100.0/24", // TEST-NET-2 documentation
          "203.0.113.0/24", // TEST-NET-3 documentation
          "240.0.0.0/4"); // reserved for future use

  // IPv6 deny CIDRs (7건 - IPv4-mapped는 별도 unmap 처리)
  private static final List<String> IPV6_DENY_CIDRS =
      List.of(
          "::1/128", // loopback
          "fe80::/10", // link-local
          "fc00::/7", // ULA
          "ff00::/8", // multicast
          "::/128", // unspecified
          "2001:db8::/32", // documentation (RFC 3849)
          "3fff::/20"); // documentation (RFC 9637)

  private final List<IpAddressMatcher> denyMatchers;

  public SsrfGuard() {
    this.denyMatchers =
        Stream.concat(IPV4_DENY_CIDRS.stream(), IPV6_DENY_CIDRS.stream())
            .map(IpAddressMatcher::new)
            .toList();
  }

  /**
   * URL을 검증하고 host를 InetAddress[]로 resolve한다. 모든 주소가 IANA non-global SSRF deny CIDR 외부일 때만 통과.
   *
   * @throws BadRequestException URL이 null/blank/malformed/scheme not http(s)/host null
   * @throws SsrfBlockedException 주소가 deny CIDR에 매칭됨
   */
  public ValidatedTarget validateAndResolve(String url) {
    if (url == null || url.isBlank()) {
      throw new BadRequestException("URL은 필수입니다");
    }
    URI uri;
    try {
      uri = new URI(url);
    } catch (URISyntaxException e) {
      throw new BadRequestException("유효하지 않은 URL 형식입니다");
    }
    String scheme = uri.getScheme();
    if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
      throw new BadRequestException("유효하지 않은 URL 형식입니다");
    }
    String host = uri.getHost();
    if (host == null) {
      throw new BadRequestException("유효하지 않은 URL 형식입니다");
    }

    // R4-1 fix: IPv6 literal URL의 URI.getHost()는 brackets 포함 ("[::1]"). Apache HttpHost는 brackets
    // 제외.
    // PinnedDnsResolver의 host 비교 정합 + IpAddressMatcher 매칭 정합을 위해 normalize.
    String normalizedHost =
        (host.startsWith("[") && host.endsWith("]")) ? host.substring(1, host.length() - 1) : host;

    InetAddress[] addrs;
    try {
      addrs = resolveHost(normalizedHost);
    } catch (UnknownHostException e) {
      throw new BadRequestException("유효하지 않은 URL 형식입니다");
    }

    InetAddress[] approvedAddrs = checkAndPinAddresses(host, addrs);
    // R4-1 fix: ValidatedTarget.host는 normalized form (brackets 제외) - PinnedDnsResolver 정합
    return new ValidatedTarget(uri, normalizedHost, scheme, uri.getPort(), approvedAddrs);
  }

  /**
   * 각 해석 주소에 deny CIDR 매칭을 적용하고, pinning에 쓸 정규화된(unmapped) 주소 배열을 반환한다.
   *
   * <p>CodeRabbit PR#40: 매칭은 unmapped 주소로 하면서 pinning은 원본을 저장하면 ::ffff:x.x.x.x 케이스에서 승인 판정과
   * PinnedDnsResolver 비교 기준이 어긋나 정상 요청도 rebinding으로 오인된다. 두 기준을 unmapped로 통일.
   */
  private InetAddress[] checkAndPinAddresses(String host, InetAddress[] addrs) {
    InetAddress[] approvedAddrs = new InetAddress[addrs.length];
    for (int i = 0; i < addrs.length; i++) {
      InetAddress effectiveAddr = unmapIpv4MappedIpv6(addrs[i]);
      approvedAddrs[i] = effectiveAddr;
      String ipString = effectiveAddr.getHostAddress();
      for (IpAddressMatcher matcher : denyMatchers) {
        if (matcher.matches(ipString)) {
          // CodeRabbit #2: raw URL 로그 회피 (userinfo/query 토큰 유출 방지) - host/IP/CIDR만 기록
          log.warn("SSRF block: host={} resolved={} matched_cidr={}", host, ipString, matcher);
          throw new SsrfBlockedException("내부 네트워크 주소는 차단되었습니다");
        }
      }
    }
    return approvedAddrs;
  }

  /** Test seam - Mockito @Spy로 override 가능 (DNS rebinding 시나리오 등). */
  protected InetAddress[] resolveHost(String host) throws UnknownHostException {
    return InetAddress.getAllByName(host);
  }

  /**
   * 로그 안전을 위해 URI를 {@code scheme://host[:port][/path]}로 redact.
   *
   * <p>userinfo와 query string은 제거 — query에 담긴 토큰/식별자나 userinfo의 자격 증명이 로그에 유출되는 것을 방지. SSRF block /
   * redirect chain 등 로그에서 사용.
   *
   * @param uri 원본 URI (null 허용)
   * @return redacted 형식 문자열, null 입력 시 {@code "<null>"}
   */
  public static String redactUri(URI uri) {
    if (uri == null) {
      return "<null>";
    }
    StringBuilder sb = new StringBuilder();
    if (uri.getScheme() != null) {
      sb.append(uri.getScheme()).append("://");
    }
    if (uri.getHost() != null) {
      sb.append(uri.getHost());
    }
    if (uri.getPort() != -1) {
      sb.append(':').append(uri.getPort());
    }
    if (uri.getPath() != null) {
      sb.append(uri.getPath());
    }
    return sb.toString();
  }

  /** RFC 4291 §2.5.5.2 IPv4-mapped IPv6 (::ffff:0:0/96)를 IPv4로 unmap. */
  private InetAddress unmapIpv4MappedIpv6(InetAddress addr) {
    if (!(addr instanceof java.net.Inet6Address)) {
      return addr;
    }
    byte[] bytes = addr.getAddress();
    boolean isMapped = true;
    for (int i = 0; i < 10; i++) {
      if (bytes[i] != 0) {
        isMapped = false;
        break;
      }
    }
    if (isMapped && bytes[10] == (byte) 0xff && bytes[11] == (byte) 0xff) {
      try {
        return InetAddress.getByAddress(new byte[] {bytes[12], bytes[13], bytes[14], bytes[15]});
      } catch (UnknownHostException e) {
        return addr;
      }
    }
    return addr;
  }

  /** SsrfGuard 검증 결과 - 정규화된 host + scheme/port + pinned addresses (DNS rebinding 방어용). */
  public record ValidatedTarget(
      URI uri, String host, String scheme, int port, InetAddress[] approvedAddresses) {

    /** Compact constructor: defensive copy로 caller mutation 방지. */
    public ValidatedTarget {
      approvedAddresses = approvedAddresses.clone();
    }

    /** Defensive accessor: clone 반환 (SpotBugs EI_EXPOSE_REP 회피). */
    @Override
    public InetAddress[] approvedAddresses() {
      return approvedAddresses.clone();
    }
  }
}
