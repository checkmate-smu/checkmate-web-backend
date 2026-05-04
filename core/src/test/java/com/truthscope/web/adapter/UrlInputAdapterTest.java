package com.truthscope.web.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class UrlInputAdapterTest {

  private final UrlInputAdapter urlInputAdapter = new UrlInputAdapter();

  @Test
  void 정상_URL은_앞뒤_공백을_제거하고_반환한다() {
    String result = urlInputAdapter.normalize(" https://example.com/news/123 ");

    assertThat(result).isEqualTo("https://example.com/news/123");
  }

  @Test
  void 빈_URL이면_예외가_발생한다() {
    assertThatThrownBy(() -> urlInputAdapter.normalize(" "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void http_https가_아닌_URL이면_예외가_발생한다() {
    assertThatThrownBy(() -> urlInputAdapter.normalize("ftp://example.com/news/123"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void host가_없는_URL이면_예외가_발생한다() {
    assertThatThrownBy(() -> urlInputAdapter.normalize("https:///news/123"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
