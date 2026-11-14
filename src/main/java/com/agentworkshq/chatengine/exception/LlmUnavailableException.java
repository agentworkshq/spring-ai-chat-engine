package com.agentworkshq.chatengine.exception;

import java.time.Duration;

/** The provider is down, overloaded or unreachable (5xx, timeout, connection error). Maps to 503. */
public class LlmUnavailableException extends LlmException {

	public static final String MESSAGE = "The language model is temporarily unavailable";

	public LlmUnavailableException(Integer upstreamStatus, Duration retryAfter) {
		super(MESSAGE, upstreamStatus, retryAfter);
	}
}
