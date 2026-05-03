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
    if (scheme == null || (!scheme.equals("http") && !scheme.equals("https"))) {
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

    for (InetAddress addr : addrs) {
      InetAddress effectiveAddr = unmapIpv4MappedIpv6(addr);
      String ipString = effectiveAddr.getHostAddress();
      for (IpAddressMatcher matcher : denyMatchers) {
        if (matcher.matches(ipString)) {
          log.warn(
              "SSRF block: url={} host={} resolved={} matched_cidr={}",
              url,
              host,
              ipString,
              matcher);
          throw new SsrfBlockedException("내부 네트워크 주소는 차단되었습니다");
        }
      }
    }
    // R4-1 fix: ValidatedTarget.host는 normalized form (brackets 제외) - PinnedDnsResolver 정합
    return new ValidatedTarget(uri, normalizedHost, scheme, uri.getPort(), addrs);
  }

  /** Test seam - Mockito @Spy로 override 가능 (DNS rebinding 시나리오 등). */
  protected InetAddress[] resolveHost(String host) throws UnknownHostException {
    return InetAddress.getAllByName(host);
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
