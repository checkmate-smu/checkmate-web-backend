package com.checkmate.web.repository;

import com.checkmate.web.entity.Article;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArticleRepository extends JpaRepository<Article, UUID> {

  Optional<Article> findBySessionId(UUID sessionId);

  Optional<Article> findByUrl(String url);
}
