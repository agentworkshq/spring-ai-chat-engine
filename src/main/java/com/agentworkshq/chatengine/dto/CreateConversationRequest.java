package com.agentworkshq.chatengine.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateConversationRequest(
		@NotBlank @Size(max = 200) String title,
		String systemPrompt,
		@Size(max = 255) String model,
		@DecimalMin("0.0") @DecimalMax("2.0") Double temperature) {
}
