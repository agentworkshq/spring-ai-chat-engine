package com.agentworkshq.chatengine.dto;

import java.time.Instant;
import java.util.UUID;

import com.agentworkshq.chatengine.entity.Conversation;

public record ConversationSummaryResponse(
		UUID id,
		String title,
		String model,
		Double temperature,
		Instant createdAt,
		Instant updatedAt) {

	public static ConversationSummaryResponse from(Conversation c) {
		return new ConversationSummaryResponse(c.getId(), c.getTitle(), c.getModel(), c.getTemperature(),
				c.getCreatedAt(), c.getUpdatedAt());
	}
}
