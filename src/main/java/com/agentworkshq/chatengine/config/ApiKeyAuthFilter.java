package com.agentworkshq.chatengine.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Optional API key authentication: when {@code security.api-key.enabled} is true, every request
 * must carry an {@code X-API-Key} header equal to {@code security.api-key.value}. Health checks and
 * the OpenAPI documentation stay open.
 *
 * <p>This is a shared-secret check, not a user system, so full Spring Security is not needed. It
 * runs before the rate limiter, so unauthenticated traffic cannot use up a legitimate client's
 * allowance.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@ConditionalOnProperty(prefix = "security.api-key", name = "enabled", havingValue = "true")
public class ApiKeyAuthFilter extends OncePerRequestFilter {

	public static final String HEADER = "X-API-Key";

	static final List<String> OPEN_PATHS = List.of(
			"/actuator/health", "/actuator/health/**",
			"/v3/api-docs", "/v3/api-docs/**",
			"/swagger-ui.html", "/swagger-ui/**");

	private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthFilter.class);
	private static final AntPathMatcher PATHS = new AntPathMatcher();

	private final byte[] expectedDigest;
	private final ErrorResponseWriter errors;

	public ApiKeyAuthFilter(ApiKeyProperties properties, ErrorResponseWriter errors) {
		this.expectedDigest = sha256(properties.value());
		this.errors = errors;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = RateLimitFilter.requestPath(request);
		return OPEN_PATHS.stream().anyMatch(pattern -> PATHS.match(pattern, path));
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String provided = request.getHeader(HEADER);
		if (provided == null || !matches(provided)) {
			// Never log the supplied key. Missing and wrong keys get the same answer.
			log.warn("Rejected request without a valid API key: {} {} from {}", request.getMethod(),
					RateLimitFilter.requestPath(request), request.getRemoteAddr());
			errors.write(request, response, HttpStatus.UNAUTHORIZED, "Missing or invalid API key", null);
			return;
		}
		chain.doFilter(request, response);
	}

	/** Constant-time comparison; hashing first also hides the length of the real key. */
	private boolean matches(String provided) {
		return MessageDigest.isEqual(expectedDigest, sha256(provided));
	}

	private static byte[] sha256(String value) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is not available", ex);
		}
	}

}
