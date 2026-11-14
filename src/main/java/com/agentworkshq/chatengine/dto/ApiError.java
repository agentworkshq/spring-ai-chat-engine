package com.agentworkshq.chatengine.dto;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ApiError", description = "The body of every error response")
public record ApiError(
		@Schema(description = "When the error happened", example = "2026-11-14T10:29:03.283345Z")
		Instant timestamp,
		@Schema(description = "HTTP status code", example = "400") int status,
		@Schema(description = "HTTP status text", example = "Bad Request") String error,
		@Schema(description = "What went wrong. Generic by design: never contains keys or provider details.",
				example = "Validation failed: title: must not be blank") String message,
		@Schema(description = "Request path", example = "/api/v1/conversations") String path) {
}
