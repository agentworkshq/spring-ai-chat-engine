package com.agentworkshq.chatengine;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.agentworkshq.chatengine.repository.ConversationRepository;
import com.agentworkshq.chatengine.repository.MessageRepository;
import com.jayway.jsonpath.JsonPath;

/**
 * Shared setup for the end-to-end API tests: the whole application on a real PostgreSQL 16 in
 * Testcontainers (wired with {@code @ServiceConnection} in {@link TestcontainersConfiguration}) and
 * a {@link FakeLlmClient} in place of the provider client.
 *
 * <p>Every subclass uses this exact configuration, so Spring creates the context, and the database
 * container, only once for all of them. Tests are isolated by emptying the tables before each one.
 *
 * <p>Nothing here can reach Groq: the fake is the primary {@code LlmClient}, and as a second line of
 * defence the provider base URL points at a closed local port with a dummy key.
 */
@SpringBootTest(properties = {
		"spring.ai.openai.api-key=test-key-not-real",
		"spring.ai.openai.base-url=http://localhost:1/v1",
		// The default per-IP limit would throttle a test suite that sends everything from 127.0.0.1.
		"rate-limit.enabled=false",
		// Small values so windowing and the input limit are cheap to exercise.
		"chat.history-window=4",
		"chat.max-input-chars=50" })
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, ApiIntegrationTestSupport.FakeLlmConfiguration.class })
abstract class ApiIntegrationTestSupport {

	protected static final String CONVERSATIONS = "/api/v1/conversations";
	protected static final MediaType JSON = MediaType.APPLICATION_JSON;

	@TestConfiguration(proxyBeanMethods = false)
	static class FakeLlmConfiguration {

		@Bean
		@Primary
		FakeLlmClient fakeLlmClient() {
			return new FakeLlmClient();
		}
	}

	@Autowired
	protected MockMvc mvc;

	@Autowired
	protected FakeLlmClient fakeLlm;

	@Autowired
	protected ConversationRepository conversationRepository;

	@Autowired
	protected MessageRepository messageRepository;

	@BeforeEach
	void cleanDatabaseAndFake() {
		messageRepository.deleteAllInBatch();
		conversationRepository.deleteAllInBatch();
		fakeLlm.reset();
	}

	// ---- request helpers ----

	protected ResultActions createConversationRequest(String json) throws Exception {
		return mvc.perform(post(CONVERSATIONS).contentType(JSON).content(json));
	}

	/** Creates a conversation through the API and returns its id. */
	protected String createConversation(String json) throws Exception {
		String body = createConversationRequest(json).andExpect(status().isCreated()).andReturn().getResponse()
				.getContentAsString();
		return JsonPath.read(body, "$.id");
	}

	protected String createConversation() throws Exception {
		return createConversation("{\"title\":\"Test conversation\"}");
	}

	protected ResultActions getConversation(String id) throws Exception {
		return mvc.perform(get(CONVERSATIONS + "/{id}", id));
	}

	protected ResultActions patchConversation(String id, String json) throws Exception {
		return mvc.perform(patch(CONVERSATIONS + "/{id}", id).contentType(JSON).content(json));
	}

	protected ResultActions deleteConversation(String id) throws Exception {
		return mvc.perform(delete(CONVERSATIONS + "/{id}", id));
	}

	protected ResultActions sendMessage(String conversationId, String content) throws Exception {
		return mvc.perform(post(CONVERSATIONS + "/{id}/messages", conversationId).contentType(JSON)
				.content(contentBody(content)));
	}

	protected static String contentBody(String content) {
		return "{\"content\":\"" + content.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
	}

	// ---- assertions ----

	/** Asserts the standard five-field error body. */
	protected static void expectErrorBody(ResultActions result, int status, String error, String path)
			throws Exception {
		result.andExpect(status().is(status))
				.andExpect(jsonPath("$.length()").value(5))
				.andExpect(jsonPath("$.timestamp").isNotEmpty())
				.andExpect(jsonPath("$.status").value(status))
				.andExpect(jsonPath("$.error").value(error))
				.andExpect(jsonPath("$.message").isNotEmpty())
				.andExpect(jsonPath("$.path").value(path));
	}

}
