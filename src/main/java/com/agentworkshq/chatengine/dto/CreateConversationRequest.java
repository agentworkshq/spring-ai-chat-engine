package com.agentworkshq.chatengine.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Settings for a new conversation. Only the title is required.")
public record CreateConversationRequest(
		@Schema(description = "Display title of the conversation", example = "Trip planning", maxLength = 200)
		@NotBlank @Size(max = 200) String title,
		@Schema(description = "System prompt for this conversation. When omitted the server default prompt is used.",
				example = "You are a concise travel assistant.", nullable = true)
		String systemPrompt,
		@Schema(description = "Model to use for this conversation. When omitted the server default model is used.",
				example = "openai/gpt-oss-120b", maxLength = 255, nullable = true)
		@Size(max = 255) String model,
		@Schema(description = "Sampling temperature for this conversation. When omitted the server default is used.",
				example = "0.4", minimum = "0.0", maximum = "2.0", nullable = true)
		@DecimalMin("0.0") @DecimalMax("2.0") Double temperature) {
}
