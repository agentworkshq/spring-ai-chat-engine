package com.agentworkshq.chatengine.dto;

import java.time.Instant;
import java.util.UUID;

import com.agentworkshq.chatengine.entity.Message;
import com.agentworkshq.chatengine.entity.MessageRole;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "One message in a conversation")
public record MessageResponse(
		@Schema(description = "Message id", example = "e8b7f50f-7a69-48dc-adde-d41202b0792a") UUID id,
		@Schema(description = "Who wrote the message", example = "ASSISTANT") MessageRole role,
		@Schema(description = "Message text", example = "Ride tram 28, eat a pastel de nata in Belem.")
		String content,
		@Schema(description = "When the message was saved", example = "2026-11-14T10:29:04.512771Z")
		Instant createdAt) {

	public static MessageResponse from(Message m) {
		return new MessageResponse(m.getId(), m.getRole(), m.getContent(), m.getCreatedAt());
	}
}
