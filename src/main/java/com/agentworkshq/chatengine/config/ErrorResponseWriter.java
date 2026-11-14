package com.agentworkshq.chatengine.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.agentworkshq.chatengine.dto.ApiError;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes the standard error body from servlet filters, which run before Spring MVC and so cannot
 * use the {@code @RestControllerAdvice}.
 */
@Component
public class ErrorResponseWriter {

	private final JsonMapper jsonMapper;

	public ErrorResponseWriter(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	public void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String message,
			Long retryAfterSeconds) throws IOException {
		ApiError body = new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message,
				request.getRequestURI());
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		if (retryAfterSeconds != null) {
			response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
		}
		response.getWriter().write(jsonMapper.writeValueAsString(body));
	}

}
