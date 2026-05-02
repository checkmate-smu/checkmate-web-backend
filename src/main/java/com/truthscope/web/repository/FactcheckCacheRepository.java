package com.truthscope.web.repository;

import com.truthscope.web.entity.FactcheckCache;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FactcheckCacheRepository extends JpaRepository<FactcheckCache, UUID> {

  @Query(
      value =
          "SELECT * FROM factcheck_cache WHERE search_vector @@ plainto_tsquery('simple', :query)",
      nativeQuery = true)
  List<FactcheckCache> searchByText(@Param("query") String query);
}
