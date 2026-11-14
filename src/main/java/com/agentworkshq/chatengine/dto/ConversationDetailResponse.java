package com.agentworkshq.chatengine.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.agentworkshq.chatengine.entity.Conversation;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A conversation with its full message history, oldest first")
public record ConversationDetailResponse(
		@Schema(description = "Conversation id", example = "3f6c1b2e-8a4d-4c7e-9b1a-2d5e7f9a0c11") UUID id,
		@Schema(description = "Title", example = "Trip planning") String title,
		@Schema(description = "System prompt override, or null when the server default applies",
				example = "You are a concise travel assistant.", nullable = true) String systemPrompt,
		@Schema(description = "Model override, or null when the server default applies",
				example = "openai/gpt-oss-120b", nullable = true) String model,
		@Schema(description = "Temperature override, or null when the server default applies", example = "0.4",
				nullable = true) Double temperature,
		@Schema(description = "When the conversation was created", example = "2026-11-14T10:29:00.226087Z")
		Instant createdAt,
		@Schema(description = "When the conversation was last changed", example = "2026-11-14T10:29:00.226087Z")
		Instant updatedAt,
		@Schema(description = "All messages in the conversation, oldest first") List<MessageResponse> messages) {

	public static ConversationDetailResponse from(Conversation c, List<MessageResponse> messages) {
		return new ConversationDetailResponse(c.getId(), c.getTitle(), c.getSystemPrompt(), c.getModel(),
				c.getTemperature(), c.getCreatedAt(), c.getUpdatedAt(), messages);
	}
}
