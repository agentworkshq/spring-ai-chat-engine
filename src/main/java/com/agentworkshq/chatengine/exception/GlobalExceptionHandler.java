package com.agentworkshq.chatengine.exception;

import java.time.Instant;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
		ApiError body = new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message,
				request.getRequestURI());
		// Explicit JSON so errors still render on endpoints that only produce text/event-stream.
		return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
	}
}
