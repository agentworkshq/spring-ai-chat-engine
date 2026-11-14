package com.agentworkshq.chatengine.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.agentworkshq.chatengine.config.ApiKeyAuthFilter;
import com.agentworkshq.chatengine.config.ErrorResponseWriter;
import com.agentworkshq.chatengine.config.ProtectionConfig;
import com.agentworkshq.chatengine.config.RateLimitFilter;
import com.agentworkshq.chatengine.config.RateLimiter;
import com.agentworkshq.chatengine.config.RequestIdFilter;
import com.agentworkshq.chatengine.dto.MessageResponse;
import com.agentworkshq.chatengine.entity.MessageRole;
import com.agentworkshq.chatengine.service.ChatService;

/** The real filters, wired the way the application wires them, in front of the real controller. */
@WebMvcTest(controllers = MessageController.class, properties = {
		"security.api-key.enabled=true",
		"security.api-key.value=test-secret-key",
		"rate-limit.enabled=true",
		"rate-limit.requests-per-minute=3" })
@Import({ ProtectionConfig.class, RateLimiter.class, ErrorResponseWriter.class, RateLimitFilter.class,
		ApiKeyAuthFilter.class, RequestIdFilter.class })
class ProtectionWebTest {

	private static final String KEY = "test-secret-key";

	@Autowired
	MockMvc mvc;

	@MockitoBean
	ChatService chatService;

	private final UUID id = UUID.randomUUID();

	private static RequestPostProcessor fromIp(String ip) {
		return request -> {
			request.setRemoteAddr(ip);
			return request;
		};
	}

	private ResultActions send(String path, String apiKey, String ip) throws Exception {
		var request = post(path, id).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"hi\"}")
				.with(fromIp(ip));
		if (apiKey != null) {
			request.header("X-API-Key", apiKey);
		}
		return mvc.perform(request);
	}

	private ResultActions sendMessage(String ip) throws Exception {
		return send("/api/v1/conversations/{id}/messages", KEY, ip);
	}

	private void stubReply() {
		when(chatService.sendMessage(any(), any()))
				.thenReturn(new MessageResponse(UUID.randomUUID(), MessageRole.ASSISTANT, "ok", Instant.now()));
	}

	// ---- API key ----

	@Test
	void missingKeyIs401WithTheStandardErrorBody() throws Exception {
		send("/api/v1/conversations/{id}/messages", null, "10.0.0.1")
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.length()").value(5))
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.error").value("Unauthorized"))
				.andExpect(jsonPath("$.message").value("Missing or invalid API key"))
				.andExpect(jsonPath("$.path").value("/api/v1/conversations/" + id + "/messages"))
				.andExpect(jsonPath("$.timestamp").exists())
				.andExpect(header().exists("X-Request-Id"));
	}

	@Test
	void wrongKeyIs401AndDoesNotEchoAnyKey() throws Exception {
		send("/api/v1/conversations/{id}/messages", "not-the-key", "10.0.0.2")
				.andExpect(status().isUnauthorized())
				.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("not-the-key"))))
				.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(KEY))));
	}

	@Test
	void keyThatMerelyStartsWithTheRealOneIsRejected() throws Exception {
		send("/api/v1/conversations/{id}/messages", KEY + "x", "10.0.0.3").andExpect(status().isUnauthorized());
		send("/api/v1/conversations/{id}/messages", "test-secret-ke", "10.0.0.3").andExpect(status().isUnauthorized());
	}

	@Test
	void correctKeyPassesThrough() throws Exception {
		stubReply();

		sendMessage("10.0.0.4").andExpect(status().isCreated()).andExpect(jsonPath("$.content").value("ok"));
	}

	@Test
	void authAppliesToEveryEndpointNotJustMessages() throws Exception {
		mvc.perform(get("/api/v1/conversations").with(fromIp("10.0.0.5"))).andExpect(status().isUnauthorized());
		mvc.perform(get("/actuator/info").with(fromIp("10.0.0.5"))).andExpect(status().isUnauthorized());
	}

	@Test
	void healthAndOpenApiDocsNeedNoKey() throws Exception {
		// No handler exists for these in the slice, so a 404 proves the filter let them through.
		for (String path : new String[] { "/actuator/health", "/actuator/health/liveness", "/v3/api-docs",
				"/v3/api-docs/swagger-config", "/swagger-ui.html", "/swagger-ui/index.html" }) {
			mvc.perform(get(path).with(fromIp("10.0.0.6"))).andExpect(status().isNotFound());
		}
	}

	@Test
	void lookalikePathsAreNotExempt() throws Exception {
		for (String path : new String[] { "/actuator/healthy", "/actuator/health-secrets", "/v3/api-docs-private",
				"/api/v1/conversations/actuator/health" }) {
			mvc.perform(get(path).with(fromIp("10.0.0.7"))).andExpect(status().isUnauthorized());
		}
	}

	// ---- rate limit ----

	@Test
	void fourthRequestInAMinuteIs429WithRetryAfterAndTheStandardBody() throws Exception {
		stubReply();
		for (int i = 0; i < 3; i++) {
			sendMessage("10.1.0.1").andExpect(status().isCreated());
		}

		sendMessage("10.1.0.1")
				.andExpect(status().isTooManyRequests())
				.andExpect(header().exists("Retry-After"))
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.length()").value(5))
				.andExpect(jsonPath("$.status").value(429))
				.andExpect(jsonPath("$.error").value("Too Many Requests"))
				.andExpect(jsonPath("$.message").value("Rate limit exceeded, please retry later"))
				.andExpect(jsonPath("$.path").value("/api/v1/conversations/" + id + "/messages"))
				.andExpect(header().exists("X-Request-Id"));
	}

	@Test
	void retryAfterIsAPositiveNumberOfSeconds() throws Exception {
		stubReply();
		for (int i = 0; i < 3; i++) {
			sendMessage("10.1.0.2");
		}

		String retryAfter = sendMessage("10.1.0.2").andReturn().getResponse().getHeader("Retry-After");

		org.assertj.core.api.Assertions.assertThat(Long.parseLong(retryAfter)).isBetween(1L, 60L);
	}

	@Test
	void streamEndpointSharesTheSameBucket() throws Exception {
		stubReply();
		sendMessage("10.1.0.3").andExpect(status().isCreated());
		sendMessage("10.1.0.3").andExpect(status().isCreated());
		send("/api/v1/conversations/{id}/messages/stream", KEY, "10.1.0.3");

		send("/api/v1/conversations/{id}/messages/stream", KEY, "10.1.0.3")
				.andExpect(status().isTooManyRequests());
		sendMessage("10.1.0.3").andExpect(status().isTooManyRequests());
	}

	@Test
	void eachIpHasItsOwnAllowance() throws Exception {
		stubReply();
		for (int i = 0; i < 3; i++) {
			sendMessage("10.1.0.4");
		}
		sendMessage("10.1.0.4").andExpect(status().isTooManyRequests());

		sendMessage("10.1.0.5").andExpect(status().isCreated());
	}

	@Test
	void otherEndpointsAreNotRateLimited() throws Exception {
		for (int i = 0; i < 10; i++) {
			// Authenticated GET on a path with no handler: 404 every time, never 429.
			mvc.perform(get("/api/v1/conversations").header("X-API-Key", KEY).with(fromIp("10.1.0.6")))
					.andExpect(status().is(org.hamcrest.Matchers.not(429)));
		}
	}

	@Test
	void rejectedUnauthenticatedRequestsDoNotUseUpTheAllowance() throws Exception {
		stubReply();
		for (int i = 0; i < 10; i++) {
			send("/api/v1/conversations/{id}/messages", "wrong", "10.1.0.7").andExpect(status().isUnauthorized());
		}

		sendMessage("10.1.0.7").andExpect(status().isCreated());
	}

	@Test
	void everyResponseCarriesARequestId() throws Exception {
		stubReply();

		sendMessage("10.1.0.8").andExpect(header().exists("X-Request-Id"));
	}

}
