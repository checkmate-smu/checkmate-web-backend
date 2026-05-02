package com.truthscope.web.repository;

import com.truthscope.web.entity.VerificationResult;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerificationResultRepository extends JpaRepository<VerificationResult, UUID> {

  Optional<VerificationResult> findByClaimId(UUID claimId);
}
