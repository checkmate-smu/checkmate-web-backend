package com.checkmate.web.repository;

import com.checkmate.web.entity.Member;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, UUID> {}
