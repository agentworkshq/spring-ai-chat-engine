package com.agentworkshq.chatengine.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Resilience settings for calls to the LLM provider, bound to {@code chat.llm.*}.
 *
 * @param connectTimeout maximum time to establish a connection
 * @param readTimeout maximum time to wait for data from the provider (also applies between
 * streamed chunks)
 * @param maxAttempts total attempts per call, including the first one; 1 disables retries.
 * Only transient failures are retried (408, 409, 429, 5xx, timeouts and connection errors) with
 * exponential backoff, honouring Retry-After
 */
@Validated
@ConfigurationProperties(prefix = "chat.llm")
public record LlmProperties(
		@NotNull @DefaultValue("5s") Duration connectTimeout,
		@NotNull @DefaultValue("60s") Duration readTimeout,
		@Min(1) @DefaultValue("3") int maxAttempts) {

	public static final String MAX_ATTEMPTS_PROPERTY = "chat.llm.max-attempts";
	public static final int DEFAULT_MAX_ATTEMPTS = 3;

}
