package com.agentworkshq.chatengine.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import com.agentworkshq.chatengine.config.ApiKeyAuthFilter;
import com.agentworkshq.chatengine.config.ErrorResponseWriter;
import com.agentworkshq.chatengine.config.ProtectionConfig;
import com.agentworkshq.chatengine.config.RateLimitFilter;
import com.agentworkshq.chatengine.config.RateLimiter;
import com.agentworkshq.chatengine.dto.MessageResponse;
import com.agentworkshq.chatengine.entity.MessageRole;
import com.agentworkshq.chatengine.service.ChatService;

/** Defaults: no API key required, and the rate limit can be switched off. */
@WebMvcTest(controllers = MessageController.class, properties = {
		"rate-limit.enabled=false",
		"rate-limit.requests-per-minute=1" })
@Import({ ProtectionConfig.class, RateLimiter.class, ErrorResponseWriter.class, RateLimitFilter.class,
		ApiKeyAuthFilter.class })
class ProtectionDisabledWebTest {

	@Autowired
	MockMvc mvc;

	@MockitoBean
	ChatService chatService;

	@Test
	void noApiKeyIsNeededWhenAuthIsNotEnabled() throws Exception {
		when(chatService.sendMessage(any(), any()))
				.thenReturn(new MessageResponse(UUID.randomUUID(), MessageRole.ASSISTANT, "ok", Instant.now()));

		mvc.perform(post("/api/v1/conversations/{id}/messages", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"hi\"}"))
				.andExpect(status().isCreated());
	}

	@Test
	void disabledRateLimitNeverRejects() throws Exception {
		when(chatService.sendMessage(any(), any()))
				.thenReturn(new MessageResponse(UUID.randomUUID(), MessageRole.ASSISTANT, "ok", Instant.now()));

		for (int i = 0; i < 5; i++) {
			mvc.perform(post("/api/v1/conversations/{id}/messages", UUID.randomUUID())
					.contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"hi\"}"))
					.andExpect(status().isCreated());
		}
	}

}
