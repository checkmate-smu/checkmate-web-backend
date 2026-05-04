package com.truthscope.web.repository;

import com.truthscope.web.entity.UserReaction;
import com.truthscope.web.entity.enums.ReactionType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserReactionRepository extends JpaRepository<UserReaction, UUID> {

  Optional<UserReaction> findByArticleIdAndMemberIdAndType(
      UUID articleId, UUID memberId, ReactionType type);
}
