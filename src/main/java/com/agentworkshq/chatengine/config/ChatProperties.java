package com.agentworkshq.chatengine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Prompt controls bound to {@code chat.*}.
 *
 * @param systemPrompt default system prompt, used when a conversation has none of its own
 * @param historyWindow how many of the most recent messages are sent to the model
 * @param maxInputChars maximum length of a single user message
 */
@Validated
@ConfigurationProperties(prefix = "chat")
public record ChatProperties(
		@NotBlank @DefaultValue(DEFAULT_SYSTEM_PROMPT) String systemPrompt,
		@Min(1) @DefaultValue("20") int historyWindow,
		@Min(1) @DefaultValue("8000") int maxInputChars) {

	public static final String DEFAULT_SYSTEM_PROMPT = "You are a concise, helpful assistant. "
			+ "Answer directly and accurately, keep replies brief unless the user asks for detail, "
			+ "and say so plainly when you do not know something.";

}
