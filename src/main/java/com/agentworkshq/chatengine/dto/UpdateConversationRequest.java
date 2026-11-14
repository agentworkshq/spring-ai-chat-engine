package com.agentworkshq.chatengine.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Partial update: a null field is left unchanged. A blank {@code systemPrompt} or {@code model}
 * clears that override so the configured default applies again.
 */
public record UpdateConversationRequest(
		@Size(max = 200) @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank") String title,
		String systemPrompt,
		@Size(max = 255) String model,
		@DecimalMin("0.0") @DecimalMax("2.0") Double temperature) {
}
