package com.agentworkshq.chatengine.exception;

import java.time.Duration;

/** The provider rejected the call because of rate limiting (429). Maps to 429. */
public class LlmRateLimitedException extends LlmException {

	public static final String MESSAGE = "The language model is rate limited, please retry later";

	public LlmRateLimitedException(Duration retryAfter) {
		super(MESSAGE, 429, retryAfter);
	}
}
