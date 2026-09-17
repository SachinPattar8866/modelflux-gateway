package com.modelflux.repository;

import com.modelflux.model.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    // For listing history in the sidebar, most recent first
    List<Conversation> findByUserIdOrderByUpdatedAtDesc(Long userId);

    // Ensures a user can only access their own conversation
    Optional<Conversation> findByIdAndUserId(Long id, Long userId);
}