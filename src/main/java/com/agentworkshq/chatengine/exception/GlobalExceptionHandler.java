package com.agentworkshq.chatengine.exception;

import java.time.Duration;
import java.time.Instant;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.agentworkshq.chatengine.dto.ApiError;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(NotFoundException.class)
	ResponseEntity<ApiError> handleNotFound(NotFoundException ex, HttpServletRequest request) {
		return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
	}

	/** The client went away mid-response (for example during an SSE stream); there is nobody to answer. */
	@ExceptionHandler(AsyncRequestNotUsableException.class)
	void handleClientDisconnect(AsyncRequestNotUsableException ex, HttpServletRequest request) {
		log.debug("Client disconnected from {} {}", request.getMethod(), request.getRequestURI());
	}

	@ExceptionHandler(InvalidRequestException.class)
	ResponseEntity<ApiError> handleInvalidRequest(InvalidRequestException ex, HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
	}

	/** Provider rate limit: 429, with Retry-After when the provider told us. */
	@ExceptionHandler(LlmRateLimitedException.class)
	ResponseEntity<ApiError> handleLlmRateLimited(LlmRateLimitedException ex, HttpServletRequest request) {
		return build(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), request, ex.getRetryAfter());
	}

	/** Provider down, overloaded or unreachable: 503, with Retry-After when known. */
	@ExceptionHandler(LlmUnavailableException.class)
	ResponseEntity<ApiError> handleLlmUnavailable(LlmUnavailableException ex, HttpServletRequest request) {
		return build(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), request, ex.getRetryAfter());
	}

	/** Anything else wrong with the provider response (invalid payload, rejected request): 502. */
	@ExceptionHandler(LlmException.class)
	ResponseEntity<ApiError> handleLlm(LlmException ex, HttpServletRequest request) {
		return build(HttpStatus.BAD_GATEWAY, ex.getMessage(), request);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
		String details = ex.getBindingResult().getFieldErrors().stream()
				.map(e -> e.getField() + ": " + e.getDefaultMessage())
				.sorted()
				.collect(Collectors.joining("; "));
		return build(HttpStatus.BAD_REQUEST, "Validation failed: " + details, request);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, "Malformed or unreadable request body", request);
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
		return build(HttpStatus.BAD_REQUEST, "Invalid value for parameter " + ex.getName(), request);
	}

	/** Framework exceptions (405, 404 for unknown routes, 415, ...) keep their 4xx status. */
	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest request) {
		if (ex instanceof ErrorResponse er) {
			HttpStatus status = HttpStatus.resolve(er.getStatusCode().value());
			if (status != null && status.is4xxClientError()) {
				return build(status, status.getReasonPhrase(), request);
			}
		}
		log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
		return build(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", request);
	}

	private ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest request) {
		return build(status, message, request, null);
	}

	private ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest request,
			Duration retryAfter) {
		ApiError body = new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message,
				request.getRequestURI());
		// Explicit JSON so errors still render on endpoints that only produce text/event-stream.
		ResponseEntity.BodyBuilder response = ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON);
		if (retryAfter != null) {
			// Retry-After is whole seconds; round up and never advertise 0.
			long seconds = Math.max(1, (retryAfter.toMillis() + 999) / 1000);
			response.header(HttpHeaders.RETRY_AFTER, Long.toString(seconds));
		}
		return response.body(body);
	}
}
