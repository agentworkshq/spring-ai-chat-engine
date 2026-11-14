package com.agentworkshq.chatengine.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.agentworkshq.chatengine.entity.Conversation;

public record ConversationDetailResponse(
		UUID id,
		String title,
		String systemPrompt,
		String model,
		Double temperature,
		Instant createdAt,
		Instant updatedAt,
		List<MessageResponse> messages) {

	public static ConversationDetailResponse from(Conversation c, List<MessageResponse> messages) {
		return new ConversationDetailResponse(c.getId(), c.getTitle(), c.getSystemPrompt(), c.getModel(),
				c.getTemperature(), c.getCreatedAt(), c.getUpdatedAt(), messages);
	}
}
