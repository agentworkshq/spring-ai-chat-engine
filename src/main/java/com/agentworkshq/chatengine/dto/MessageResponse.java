package com.agentworkshq.chatengine.dto;

import java.time.Instant;
import java.util.UUID;

import com.agentworkshq.chatengine.entity.Message;
import com.agentworkshq.chatengine.entity.MessageRole;

public record MessageResponse(UUID id, MessageRole role, String content, Instant createdAt) {

	public static MessageResponse from(Message m) {
		return new MessageResponse(m.getId(), m.getRole(), m.getContent(), m.getCreatedAt());
	}
}
