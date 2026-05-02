package com.truthscope.web.repository;

import com.truthscope.web.entity.VerifySource;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerifySourceRepository extends JpaRepository<VerifySource, UUID> {

  List<VerifySource> findByResultId(UUID resultId);
}
