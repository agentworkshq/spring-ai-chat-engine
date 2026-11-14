package com.agentworkshq.chatengine.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Partial update: a null field is left unchanged. A blank {@code systemPrompt} or {@code model}
 * clears that override so the configured default applies again.
 */
@Schema(description = "Partial update. Fields you leave out stay unchanged. "
		+ "A blank systemPrompt or model clears that override so the server default applies again. "
		+ "The temperature cannot be cleared once set, only changed.")
public record UpdateConversationRequest(
		@Schema(description = "New title", example = "Lisbon trip", maxLength = 200, nullable = true)
		@Size(max = 200) @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank") String title,
		@Schema(description = "New system prompt; blank clears it", example = "Answer in Portuguese.",
				nullable = true)
		String systemPrompt,
		@Schema(description = "New model; blank clears it", example = "openai/gpt-oss-20b", maxLength = 255,
				nullable = true)
		@Size(max = 255) String model,
		@Schema(description = "New temperature", example = "0.2", minimum = "0.0", maximum = "2.0", nullable = true)
		@DecimalMin("0.0") @DecimalMax("2.0") Double temperature) {
}
