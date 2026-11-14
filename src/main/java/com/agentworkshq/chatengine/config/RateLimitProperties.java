package com.agentworkshq.chatengine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;

/**
 * Per-IP rate limit for the message endpoints, bound to {@code rate-limit.*}.
 *
 * @param enabled whether the limit is applied
 * @param requestsPerMinute sustained requests per minute per client IP; also the burst size
 */
@Validated
@ConfigurationProperties(prefix = "rate-limit")
public record RateLimitProperties(
		@DefaultValue("true") boolean enabled,
		@Min(1) @DefaultValue("20") int requestsPerMinute) {
}
