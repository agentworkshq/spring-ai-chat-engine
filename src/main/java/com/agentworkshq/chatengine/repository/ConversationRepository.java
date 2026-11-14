package com.agentworkshq.chatengine.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.agentworkshq.chatengine.entity.Conversation;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

}
