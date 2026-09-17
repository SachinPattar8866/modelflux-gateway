package com.modelflux.repository;

import com.modelflux.model.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    // Fetches the chat history in chronological order to send to the LLM
    List<Message> findByConversationIdOrderByCreatedAtAsc(Long conversationId);
}