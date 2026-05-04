package com.truthscope.web.adapter;

import java.net.URI;
import java.net.URISyntaxException;
import org.springframework.stereotype.Component;

@Component
public class UrlInputAdapter {

  public String normalize(String url) {
    validate(url);
    return url.trim();
  }

  private void validate(String url) {
    if (url == null || url.isBlank()) {
      throw new IllegalArgumentException("URL은 비어 있을 수 없습니다.");
    }

    String trimmedUrl = url.trim();

    if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
      throw new IllegalArgumentException("URL은 http:// 또는 https://로 시작해야 합니다.");
    }

    try {
      URI uri = new URI(trimmedUrl);

      if (uri.getHost() == null || uri.getHost().isBlank()) {
        throw new IllegalArgumentException("유효하지 않은 URL입니다.");
      }
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("유효하지 않은 URL입니다.", e);
    }
  }
}
