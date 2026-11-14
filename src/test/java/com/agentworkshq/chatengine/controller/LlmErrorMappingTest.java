package com.agentworkshq.chatengine.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;

import com.agentworkshq.chatengine.config.ChatProperties;
import com.agentworkshq.chatengine.entity.Conversation;
import com.agentworkshq.chatengine.entity.Message;
import com.agentworkshq.chatengine.entity.MessageRole;
import com.agentworkshq.chatengine.exception.GlobalExceptionHandler;
import com.agentworkshq.chatengine.exception.LlmException;
import com.agentworkshq.chatengine.exception.LlmRateLimitedException;
import com.agentworkshq.chatengine.exception.LlmUnavailableException;
import com.agentworkshq.chatengine.repository.ConversationRepository;
import com.agentworkshq.chatengine.repository.MessageRepository;
import com.agentworkshq.chatengine.service.ChatService;
import com.agentworkshq.chatengine.service.LlmClient;
import com.agentworkshq.chatengine.service.PromptPlanner;

import reactor.core.publisher.Flux;

/**
 * Drives the real controller, service and exception advice with an {@link LlmClient} that always
 * fails, and checks the HTTP contract of every failure kind.
 */
@ExtendWith(MockitoExtension.class)
class LlmErrorMappingTest {

	private static final String SECRET = "sk-live-SECRET raw provider body";

	@Mock
	ConversationRepository conversations;

	@Mock
	MessageRepository messages;

	@Mock
	PlatformTransactionManager transactionManager;

	private final UUID id = UUID.randomUUID();
	private RuntimeException failure;
	private MockMvc mvc;

	/** The failing fake: every call throws whatever the current test configured. */
	private final LlmClient failingClient = new LlmClient() {
		@Override
		public String complete(List<LlmMessage> messages, String systemPrompt, String model, Double temperature) {
			throw failure;
		}

		@Override
		public Flux<String> stream(List<LlmMessage> messages, String systemPrompt, String model,
				Double temperature) {
			return Flux.error(failure);
		}
	};

	@BeforeEach
	void setUp() {
		ChatProperties properties = new ChatProperties("prompt", 20, 8000);
		ChatService chatService = new ChatService(conversations, messages, failingClient,
				new PromptPlanner(properties), properties, transactionManager);
		mvc = MockMvcBuilders.standaloneSetup(new MessageController(chatService))
				.setControllerAdvice(new GlobalExceptionHandler())
				.build();

		lenient().when(messages.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));
		lenient().when(conversations.findById(id))
				.thenReturn(Optional.of(Conversation.builder().id(id).title("t").build()));
		lenient().when(messages.findByConversationIdOrderByCreatedAtDesc(eq(id), any(Pageable.class)))
				.thenReturn(List.of(Message.builder().role(MessageRole.USER).content("hi").build()));
	}

	private org.springframework.test.web.servlet.ResultActions send() throws Exception {
		return mvc.perform(post("/api/v1/conversations/{id}/messages", id)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"content\":\"hi\"}"));
	}

	@Test
	void rateLimitedIs429WithRetryAfter() throws Exception {
		failure = new LlmRateLimitedException(Duration.ofMillis(2500));

		send().andExpect(status().isTooManyRequests())
				.andExpect(header().string("Retry-After", "3"))
				.andExpect(jsonPath("$.status").value(429))
				.andExpect(jsonPath("$.error").value("Too Many Requests"))
				.andExpect(jsonPath("$.message").value(LlmRateLimitedException.MESSAGE));
	}

	@Test
	void rateLimitedWithUnknownDelayHasNoRetryAfterHeader() throws Exception {
		failure = new LlmRateLimitedException(null);

		send().andExpect(status().isTooManyRequests()).andExpect(header().doesNotExist("Retry-After"));
	}

	@Test
	void unavailableIs503WithRetryAfterWhenKnown() throws Exception {
		failure = new LlmUnavailableException(503, Duration.ofSeconds(10));

		send().andExpect(status().isServiceUnavailable())
				.andExpect(header().string("Retry-After", "10"))
				.andExpect(jsonPath("$.status").value(503))
				.andExpect(jsonPath("$.message").value(LlmUnavailableException.MESSAGE));
	}

	@Test
	void unavailableWithUnknownDelayHasNoRetryAfterHeader() throws Exception {
		failure = new LlmUnavailableException(null, null);

		send().andExpect(status().isServiceUnavailable()).andExpect(header().doesNotExist("Retry-After"));
	}

	@Test
	void retryAfterIsNeverZero() throws Exception {
		failure = new LlmRateLimitedException(Duration.ofMillis(100));

		send().andExpect(header().string("Retry-After", "1"));
	}

	@Test
	void badUpstreamResponseIs502() throws Exception {
		failure = new LlmException("The language model returned an unexpected response", 400, null);

		send().andExpect(status().isBadGateway())
				.andExpect(header().doesNotExist("Retry-After"))
				.andExpect(jsonPath("$.status").value(502))
				.andExpect(jsonPath("$.error").value("Bad Gateway"))
				.andExpect(jsonPath("$.message").value("The language model returned an unexpected response"));
	}

	@Test
	void errorBodyHasExactlyTheDocumentedFields() throws Exception {
		failure = new LlmUnavailableException(503, null);

		send().andExpect(jsonPath("$.length()").value(5))
				.andExpect(jsonPath("$.timestamp").exists())
				.andExpect(jsonPath("$.status").exists())
				.andExpect(jsonPath("$.error").exists())
				.andExpect(jsonPath("$.message").exists())
				.andExpect(jsonPath("$.path").value("/api/v1/conversations/" + id + "/messages"));
	}

	@Test
	void unexpectedFailuresAre500WithAGenericMessageAndNoLeak() throws Exception {
		failure = new IllegalStateException(SECRET);

		send().andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.message").value("Internal server error"))
				.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SECRET"))));
	}

}
