package com.agentworkshq.chatengine.exception;

import java.time.Duration;

/**
 * Raised when the LLM provider call fails. The plain type means the provider answered with
 * something unusable (maps to HTTP 502); see the subclasses for transient failures.
 *
 * <p>Messages are generic by design: they must never contain keys, request data or raw provider
 * bodies, because they are returned to API clients.
 */
public class LlmException extends RuntimeException {

	private final Integer upstreamStatus;
	private final Duration retryAfter;

	public LlmException(String message) {
		this(message, null, null);
	}

	public LlmException(String message, Integer upstreamStatus, Duration retryAfter) {
		super(message);
		this.upstreamStatus = upstreamStatus;
		this.retryAfter = retryAfter;
	}

	/** HTTP status the provider returned, or null if there was no response (timeout, connection). */
	public Integer getUpstreamStatus() {
		return upstreamStatus;
	}

	/** Delay the provider asked for, or null if unknown. */
	public Duration getRetryAfter() {
		return retryAfter;
	}
}
