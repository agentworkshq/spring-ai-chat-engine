package com.agentworkshq.chatengine.exception;

/** Raised for a request that is well-formed but violates a business limit. Maps to HTTP 400. */
public class InvalidRequestException extends RuntimeException {

	public InvalidRequestException(String message) {
		super(message);
	}
}
