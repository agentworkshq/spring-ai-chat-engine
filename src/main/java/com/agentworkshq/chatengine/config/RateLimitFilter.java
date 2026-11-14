package com.agentworkshq.chatengine.config;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Applies the per-IP rate limit to the two message endpoints ({@code POST
 * /api/v1/conversations/{id}/messages} and {@code .../messages/stream}), which share one bucket
 * per client.
 *
 * <p>The client is {@code request.getRemoteAddr()}. Behind a reverse proxy, set
 * {@code server.forward-headers-strategy=native} so the container resolves the real client address
 * from the proxy headers; headers are deliberately not read here, because trusting them blindly
 * would let any client dodge the limit by spoofing them.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RateLimitFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

	private static final AntPathMatcher PATHS = new AntPathMatcher();
	private static final String MESSAGES = "/api/v1/conversations/*/messages";
	private static final String MESSAGES_STREAM = "/api/v1/conversations/*/messages/stream";

	private final RateLimitProperties properties;
	private final RateLimiter rateLimiter;
	private final ErrorResponseWriter errors;

	public RateLimitFilter(RateLimitProperties properties, RateLimiter rateLimiter, ErrorResponseWriter errors) {
		this.properties = properties;
		this.rateLimiter = rateLimiter;
		this.errors = errors;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		if (!properties.enabled() || !HttpMethod.POST.matches(request.getMethod())) {
			return true;
		}
		String path = requestPath(request);
		return !(PATHS.match(MESSAGES, path) || PATHS.match(MESSAGES_STREAM, path));
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String client = request.getRemoteAddr();
		RateLimiter.Decision decision = rateLimiter.tryAcquire(client);
		if (!decision.allowed()) {
			log.info("Rate limit exceeded for {} on {} (retry in {}s)", client, requestPath(request),
					decision.retryAfterSeconds());
			errors.write(request, response, HttpStatus.TOO_MANY_REQUESTS,
					"Rate limit exceeded, please retry later", decision.retryAfterSeconds());
			return;
		}
		chain.doFilter(request, response);
	}

	/** The container-normalised path (no {@code ..} tricks, no path parameters). */
	static String requestPath(HttpServletRequest request) {
		String servletPath = request.getServletPath();
		String pathInfo = request.getPathInfo();
		return (servletPath == null ? "" : servletPath) + (pathInfo == null ? "" : pathInfo);
	}

}
