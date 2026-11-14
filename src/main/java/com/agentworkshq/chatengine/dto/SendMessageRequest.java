package com.agentworkshq.chatengine.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** The maximum length is enforced against {@code chat.max-input-chars} in the service. */
@Schema(description = "A user message to send to the model")
public record SendMessageRequest(
		@Schema(description = "Message text. Must not be blank; at most 8000 characters by default "
				+ "(configurable with chat.max-input-chars).", example = "What are three things to do in Lisbon?")
		@NotBlank String content) {
}
