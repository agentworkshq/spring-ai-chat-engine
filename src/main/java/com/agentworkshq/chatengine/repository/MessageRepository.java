package com.agentworkshq.chatengine.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.agentworkshq.chatengine.entity.Message;

public interface MessageRepository extends JpaRepository<Message, UUID> {

	List<Message> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);

	/** Newest first, so a {@code Pageable} of size N yields the last N messages. */
	List<Message> findByConversationIdOrderByCreatedAtDesc(UUID conversationId, Pageable pageable);

}
