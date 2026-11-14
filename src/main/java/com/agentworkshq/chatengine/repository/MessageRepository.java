package com.agentworkshq.chatengine.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.agentworkshq.chatengine.entity.Message;

public interface MessageRepository extends JpaRepository<Message, UUID> {

	List<Message> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);

}
