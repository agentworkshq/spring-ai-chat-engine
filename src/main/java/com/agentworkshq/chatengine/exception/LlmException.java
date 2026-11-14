package com.agentworkshq.chatengine.exception;

/** Raised when the LLM provider call fails. The message is safe to show to API clients. */
public class LlmException extends RuntimeException {

	public LlmException(String message) {
		super(message);
	}
}
