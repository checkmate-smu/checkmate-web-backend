package com.checkmate.web.repository;

import com.checkmate.web.entity.Claim;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClaimRepository extends JpaRepository<Claim, UUID> {

  List<Claim> findByArticleId(UUID articleId);
}
