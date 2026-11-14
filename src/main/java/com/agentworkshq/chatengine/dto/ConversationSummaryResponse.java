package com.agentworkshq.chatengine.dto;

import java.time.Instant;
import java.util.UUID;

import com.agentworkshq.chatengine.entity.Conversation;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A conversation without its messages")
public record ConversationSummaryResponse(
		@Schema(description = "Conversation id", example = "3f6c1b2e-8a4d-4c7e-9b1a-2d5e7f9a0c11") UUID id,
		@Schema(description = "Title", example = "Trip planning") String title,
		@Schema(description = "Model override, or null when the server default applies",
				example = "openai/gpt-oss-120b", nullable = true) String model,
		@Schema(description = "Temperature override, or null when the server default applies", example = "0.4",
				nullable = true) Double temperature,
		@Schema(description = "When the conversation was created", example = "2026-11-14T10:29:00.226087Z")
		Instant createdAt,
		@Schema(description = "When the conversation was last changed", example = "2026-11-14T10:29:00.226087Z")
		Instant updatedAt) {

	public static ConversationSummaryResponse from(Conversation c) {
		return new ConversationSummaryResponse(c.getId(), c.getTitle(), c.getModel(), c.getTemperature(),
				c.getCreatedAt(), c.getUpdatedAt());
	}
}
