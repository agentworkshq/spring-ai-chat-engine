package com.agentworkshq.chatengine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.agentworkshq.chatengine.config.ChatProperties;
import com.agentworkshq.chatengine.entity.Message;
import com.agentworkshq.chatengine.entity.MessageRole;
import com.agentworkshq.chatengine.exception.LlmException;
import com.agentworkshq.chatengine.exception.LlmRateLimitedException;
import com.agentworkshq.chatengine.exception.LlmUnavailableException;
import com.agentworkshq.chatengine.service.LlmClient;
import com.agentworkshq.chatengine.service.LlmClient.LlmMessage;
import com.jayway.jsonpath.JsonPath;

/**
 * The full message flow end to end: HTTP, validation, the service, real PostgreSQL and a fake model.
 * The test profile uses {@code chat.history-window=4} and {@code chat.max-input-chars=50}.
 */
class MessageFlowIntegrationTest extends ApiIntegrationTestSupport {

	private static final String UNKNOWN_ID = "00000000-0000-0000-0000-000000000000";

	@Autowired
	LlmClient llmClient;

	@Value("${spring.ai.openai.base-url}")
	String providerBaseUrl;

	private List<Message> stored(String conversationId) {
		return messageRepository.findByConversationIdOrderByCreatedAtAsc(UUID.fromString(conversationId));
	}

	private static List<String> contents(List<LlmMessage> history) {
		return history.stream().map(LlmMessage::content).toList();
	}

	// ---- safety net ----

	@Test
	void theTestsTalkToTheFakeModelAndTheProviderUrlCannotReachGroq() {
		assertThat(llmClient).as("the primary LlmClient is the fake").isSameAs(fakeLlm);
		assertThat(providerBaseUrl).startsWith("http://localhost:1");
		assertThat(providerBaseUrl).doesNotContain("groq");
	}

	// ---- the happy path ----

	@Test
	void sendingAMessageReturnsTheReplyAndPersistsBothMessagesInOrder() throws Exception {
		String id = createConversation();

		sendMessage(id, "hello")
				.andExpect(status().isCreated())
				.andExpect(header().exists("X-Request-Id"))
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.role").value("ASSISTANT"))
				.andExpect(jsonPath("$.content").value("reply to: hello"))
				.andExpect(jsonPath("$.createdAt").isNotEmpty());

		List<Message> messages = stored(id);
		assertThat(messages).extracting(Message::getRole).containsExactly(MessageRole.USER, MessageRole.ASSISTANT);
		assertThat(messages).extracting(Message::getContent).containsExactly("hello", "reply to: hello");
		assertThat(messages.get(0).getCreatedAt()).isBefore(messages.get(1).getCreatedAt());
		assertThat(fakeLlm.calls()).hasSize(1);
	}

	@Test
	void theReturnedMessageIsTheOneThatWasSaved() throws Exception {
		String id = createConversation();

		String body = sendMessage(id, "hello").andReturn().getResponse().getContentAsString();

		String returnedId = JsonPath.read(body, "$.id");
		assertThat(stored(id).get(1).getId()).hasToString(returnedId);
	}

	@Test
	void theConversationDetailShowsTheWholeExchangeOldestFirst() throws Exception {
		String id = createConversation();
		sendMessage(id, "first").andExpect(status().isCreated());
		sendMessage(id, "second").andExpect(status().isCreated());

		getConversation(id)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.messages", hasSize(4)))
				.andExpect(jsonPath("$.messages[0].role").value("USER"))
				.andExpect(jsonPath("$.messages[0].content").value("first"))
				.andExpect(jsonPath("$.messages[1].role").value("ASSISTANT"))
				.andExpect(jsonPath("$.messages[1].content").value("reply to: first"))
				.andExpect(jsonPath("$.messages[2].content").value("second"))
				.andExpect(jsonPath("$.messages[3].content").value("reply to: second"));
	}

	@Test
	void quotesAndUnicodeSurviveTheRoundTrip() throws Exception {
		String id = createConversation();
		String text = "She said \"hi\" — café 😀";

		sendMessage(id, text).andExpect(status().isCreated()).andExpect(jsonPath("$.content").value("reply to: " + text));

		assertThat(stored(id).get(0).getContent()).isEqualTo(text);
	}

	// ---- what the model is asked ----

	@Test
	void theDefaultSystemPromptIsSentFirstWhenTheConversationHasNone() throws Exception {
		String id = createConversation();

		sendMessage(id, "hello").andExpect(status().isCreated());

		FakeLlmClient.Call call = fakeLlm.lastCall();
		assertThat(call.systemPrompt()).isEqualTo(ChatProperties.DEFAULT_SYSTEM_PROMPT);
		assertThat(call.model()).isNull();
		assertThat(call.temperature()).isNull();
	}

	@Test
	void theConversationsOwnPromptModelAndTemperatureOverrideTheDefaults() throws Exception {
		String id = createConversation("""
				{"title":"t","systemPrompt":"Reply like a pirate","model":"fake-model","temperature":1.2}""");

		sendMessage(id, "hello").andExpect(status().isCreated());

		FakeLlmClient.Call call = fakeLlm.lastCall();
		assertThat(call.systemPrompt()).isEqualTo("Reply like a pirate");
		assertThat(call.model()).isEqualTo("fake-model");
		assertThat(call.temperature()).isEqualTo(1.2);
	}

	@Test
	void overridesChangedWithPatchApplyToTheNextMessage() throws Exception {
		String id = createConversation("{\"title\":\"t\",\"model\":\"before\",\"temperature\":0.1}");
		sendMessage(id, "one").andExpect(status().isCreated());
		assertThat(fakeLlm.lastCall().model()).isEqualTo("before");

		patchConversation(id, "{\"model\":\"after\",\"temperature\":1.9,\"systemPrompt\":\"New prompt\"}")
				.andExpect(status().isOk());
		sendMessage(id, "two").andExpect(status().isCreated());

		FakeLlmClient.Call call = fakeLlm.lastCall();
		assertThat(call.model()).isEqualTo("after");
		assertThat(call.temperature()).isEqualTo(1.9);
		assertThat(call.systemPrompt()).isEqualTo("New prompt");
	}

	@Test
	void clearingThePromptFallsBackToTheDefaultAgain() throws Exception {
		String id = createConversation("{\"title\":\"t\",\"systemPrompt\":\"custom\"}");
		patchConversation(id, "{\"systemPrompt\":\"\"}").andExpect(status().isOk());

		sendMessage(id, "hello").andExpect(status().isCreated());

		assertThat(fakeLlm.lastCall().systemPrompt()).isEqualTo(ChatProperties.DEFAULT_SYSTEM_PROMPT);
	}

	// ---- history and windowing ----

	@Test
	void earlierTurnsAreSentAsHistoryInOrder() throws Exception {
		String id = createConversation();
		sendMessage(id, "m1").andExpect(status().isCreated());

		sendMessage(id, "m2").andExpect(status().isCreated());

		FakeLlmClient.Call second = fakeLlm.lastCall();
		assertThat(contents(second.history())).containsExactly("m1", "reply to: m1", "m2");
		assertThat(second.history()).extracting(LlmMessage::role)
				.containsExactly(MessageRole.USER, MessageRole.ASSISTANT, MessageRole.USER);
	}

	@Test
	void onlyTheLastFourMessagesAreSentButEverythingStaysStored() throws Exception {
		String id = createConversation();
		for (int i = 1; i <= 5; i++) {
			sendMessage(id, "m" + i).andExpect(status().isCreated());
		}

		assertThat(fakeLlm.calls()).extracting(call -> call.history().size()).containsExactly(1, 3, 4, 4, 4);
		FakeLlmClient.Call fifth = fakeLlm.lastCall();
		assertThat(contents(fifth.history())).containsExactly("reply to: m3", "m4", "reply to: m4", "m5");
		assertThat(fifth.history()).extracting(LlmMessage::role).containsExactly(MessageRole.ASSISTANT,
				MessageRole.USER, MessageRole.ASSISTANT, MessageRole.USER);

		getConversation(id).andExpect(jsonPath("$.messages", hasSize(10)));
	}

	@Test
	void theWindowAlwaysEndsWithTheMessageJustSent() throws Exception {
		String id = createConversation();
		for (int i = 1; i <= 6; i++) {
			sendMessage(id, "q" + i).andExpect(status().isCreated());

			List<LlmMessage> history = fakeLlm.lastCall().history();
			assertThat(history.get(history.size() - 1)).isEqualTo(new LlmMessage(MessageRole.USER, "q" + i));
		}
	}

	@Test
	void conversationsNeverSeeEachOthersMessages() throws Exception {
		String a = createConversation("{\"title\":\"A\"}");
		String b = createConversation("{\"title\":\"B\"}");
		sendMessage(a, "alpha").andExpect(status().isCreated());
		sendMessage(b, "beta").andExpect(status().isCreated());
		sendMessage(a, "alpha two").andExpect(status().isCreated());

		assertThat(fakeLlm.calls().get(1).history()).extracting(LlmMessage::content).containsExactly("beta");
		assertThat(contents(fakeLlm.calls().get(2).history()))
				.containsExactly("alpha", "reply to: alpha", "alpha two");
	}

	// ---- validation ----

	@Test
	void blankMissingAndNullContentAreRejectedBeforeAnythingIsSaved() throws Exception {
		String id = createConversation();
		String path = CONVERSATIONS + "/" + id + "/messages";

		for (String body : new String[] { "{\"content\":\"   \"}", "{\"content\":\"\"}", "{}", "{\"content\":null}" }) {
			ResultActions result = mvc.perform(post(path).contentType(JSON).content(body));
			expectErrorBody(result, 400, "Bad Request", path);
			result.andExpect(jsonPath("$.message").value(containsString("content")));
		}
		expectErrorBody(mvc.perform(post(path).contentType(JSON).content("{broken")), 400, "Bad Request", path);

		assertThat(messageRepository.count()).isZero();
		assertThat(fakeLlm.calls()).isEmpty();
	}

	@Test
	void contentOverTheConfiguredLimitIsRejectedAndNothingIsSaved() throws Exception {
		String id = createConversation();
		String path = CONVERSATIONS + "/" + id + "/messages";

		ResultActions result = sendMessage(id, "x".repeat(51));

		expectErrorBody(result, 400, "Bad Request", path);
		result.andExpect(jsonPath("$.message").value(containsString("50")));
		assertThat(messageRepository.count()).isZero();
		assertThat(fakeLlm.calls()).isEmpty();
	}

	@Test
	void contentOfExactlyTheLimitIsAccepted() throws Exception {
		String id = createConversation();

		sendMessage(id, "x".repeat(50)).andExpect(status().isCreated());

		assertThat(fakeLlm.calls()).hasSize(1);
	}

	// ---- 404 and bad ids ----

	@Test
	void anUnknownConversationIs404AndTheModelIsNeverCalled() throws Exception {
		expectErrorBody(sendMessage(UNKNOWN_ID, "hello"), 404, "Not Found",
				CONVERSATIONS + "/" + UNKNOWN_ID + "/messages");

		assertThat(messageRepository.count()).isZero();
		assertThat(fakeLlm.calls()).isEmpty();
	}

	@Test
	void aMalformedConversationIdIs400() throws Exception {
		expectErrorBody(sendMessage("not-a-uuid", "hello"), 400, "Bad Request",
				CONVERSATIONS + "/not-a-uuid/messages");
		assertThat(fakeLlm.calls()).isEmpty();
	}

	@Test
	void sendingToADeletedConversationIs404() throws Exception {
		String id = createConversation();
		sendMessage(id, "hello").andExpect(status().isCreated());
		deleteConversation(id).andExpect(status().isNoContent());

		sendMessage(id, "anyone there?").andExpect(status().isNotFound());

		assertThat(messageRepository.count()).isZero();
	}

	// ---- when the model fails ----

	static Stream<Arguments> providerFailures() {
		return Stream.of(
				Arguments.of("provider rate limit", new LlmRateLimitedException(Duration.ofSeconds(7)), 429,
						"Too Many Requests", "7"),
				Arguments.of("provider unavailable with a delay", new LlmUnavailableException(503, Duration.ofSeconds(10)),
						503, "Service Unavailable", "10"),
				Arguments.of("provider unavailable", new LlmUnavailableException(null, null), 503,
						"Service Unavailable", null),
				Arguments.of("unusable provider response", new LlmException("generic", 400, null), 502, "Bad Gateway",
						null),
				Arguments.of("unexpected exception", new IllegalStateException("sk-SECRET provider body"), 500,
						"Internal Server Error", null));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("providerFailures")
	void aFailingModelIsMappedToTheRightStatusAndTheUserMessageIsKeptWithoutAReply(String description,
			RuntimeException failure, int status, String error, String retryAfter) throws Exception {
		String id = createConversation();
		fakeLlm.failWith(failure);

		ResultActions result = sendMessage(id, "hello");

		expectErrorBody(result, status, error, CONVERSATIONS + "/" + id + "/messages");
		result.andExpect(header().exists("X-Request-Id"));
		if (retryAfter != null) {
			result.andExpect(header().string("Retry-After", retryAfter));
		}
		else {
			result.andExpect(header().doesNotExist("Retry-After"));
		}
		result.andExpect(jsonPath("$.message").value(not(containsString("SECRET"))));

		assertThat(stored(id)).extracting(Message::getRole).containsExactly(MessageRole.USER);
	}

	@Test
	void afterAFailureTheNextMessageWorksAndTheUnansweredOneIsPartOfTheHistory() throws Exception {
		String id = createConversation();
		fakeLlm.failWith(new LlmUnavailableException(503, null));
		sendMessage(id, "lost").andExpect(status().isServiceUnavailable());
		fakeLlm.reset();

		sendMessage(id, "again").andExpect(status().isCreated()).andExpect(jsonPath("$.content").value("reply to: again"));

		assertThat(fakeLlm.lastCall().history()).extracting(LlmMessage::role)
				.containsExactly(MessageRole.USER, MessageRole.USER);
		assertThat(contents(fakeLlm.lastCall().history())).containsExactly("lost", "again");
		assertThat(stored(id)).extracting(Message::getRole)
				.containsExactly(MessageRole.USER, MessageRole.USER, MessageRole.ASSISTANT);
	}

	// ---- streaming ----

	private record SseEvent(String name, String data) {
	}

	/** Starts a streamed request, waits for the stream to finish and returns its events. */
	private List<SseEvent> stream(String conversationId, String content) throws Exception {
		MvcResult started = mvc.perform(post(CONVERSATIONS + "/{id}/messages/stream", conversationId)
				.contentType(JSON).accept(MediaType.TEXT_EVENT_STREAM).content(contentBody(content)))
				.andExpect(request().asyncStarted())
				.andReturn();
		await().atMost(Duration.ofSeconds(10)).until(() -> {
			String body = started.getResponse().getContentAsString();
			return body.contains("event:done") || body.contains("event:error");
		});
		assertThat(started.getResponse().getContentType()).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);

		List<SseEvent> events = new ArrayList<>();
		for (String block : started.getResponse().getContentAsString().strip().split("\n\n")) {
			String name = null;
			String data = null;
			for (String line : block.split("\n")) {
				if (line.startsWith("event:")) {
					name = line.substring("event:".length()).strip();
				}
				else if (line.startsWith("data:")) {
					data = line.substring("data:".length());
				}
			}
			events.add(new SseEvent(name, data));
		}
		return events;
	}

	@Test
	void aStreamedReplyArrivesAsTokensThenDoneAndIsSavedInFull() throws Exception {
		String id = createConversation("{\"title\":\"t\",\"systemPrompt\":\"stream prompt\",\"model\":\"stream-model\"}");

		List<SseEvent> events = stream(id, "hello world");

		assertThat(events).extracting(SseEvent::name).contains("done").doesNotContain("error");
		assertThat(events.get(events.size() - 1).name()).isEqualTo("done");
		List<SseEvent> tokens = events.subList(0, events.size() - 1);
		assertThat(tokens).hasSizeGreaterThan(1).extracting(SseEvent::name).containsOnly("token");
		String assembled = tokens.stream().map(e -> (String) JsonPath.read(e.data(), "$.content"))
				.reduce("", String::concat);
		assertThat(assembled).isEqualTo("reply to: hello world");

		SseEvent done = events.get(events.size() - 1);
		assertThat((String) JsonPath.read(done.data(), "$.role")).isEqualTo("ASSISTANT");
		assertThat((String) JsonPath.read(done.data(), "$.content")).isEqualTo("reply to: hello world");

		List<Message> messages = stored(id);
		assertThat(messages).extracting(Message::getRole).containsExactly(MessageRole.USER, MessageRole.ASSISTANT);
		assertThat(messages.get(1).getContent()).isEqualTo("reply to: hello world");
		assertThat(messages.get(1).getId()).hasToString(JsonPath.read(done.data(), "$.id"));

		assertThat(fakeLlm.lastCall().systemPrompt()).isEqualTo("stream prompt");
		assertThat(fakeLlm.lastCall().model()).isEqualTo("stream-model");
	}

	@Test
	void streamedRepliesUseTheSameHistoryWindowAsNormalOnes() throws Exception {
		String id = createConversation();
		for (int i = 1; i <= 3; i++) {
			stream(id, "s" + i);
		}

		// All six messages are stored (s1, r1, s2, r2, s3, r3), but when s3 was sent only five existed
		// and the window of 4 dropped the oldest of them.
		assertThat(messageRepository.count()).isEqualTo(6);
		assertThat(contents(fakeLlm.lastCall().history()))
				.containsExactly("reply to: s1", "s2", "reply to: s2", "s3");
	}

	@Test
	void aFailingStreamEmitsAGenericErrorEventAndSavesNoReply() throws Exception {
		String id = createConversation();
		fakeLlm.failWith(new LlmRateLimitedException(Duration.ofSeconds(5)));

		List<SseEvent> events = stream(id, "hello");

		assertThat(events).extracting(SseEvent::name).containsExactly("error");
		assertThat((String) JsonPath.read(events.get(0).data(), "$.message"))
				.isEqualTo(LlmRateLimitedException.MESSAGE);
		assertThat(stored(id)).extracting(Message::getRole).containsExactly(MessageRole.USER);
	}

	@Test
	void problemsFoundBeforeStreamingAreOrdinaryJsonErrors() throws Exception {
		String id = createConversation();
		String unknownPath = CONVERSATIONS + "/" + UNKNOWN_ID + "/messages/stream";

		expectErrorBody(mvc.perform(post(unknownPath).contentType(JSON).accept(MediaType.TEXT_EVENT_STREAM)
				.content(contentBody("hello"))), 404, "Not Found", unknownPath);
		expectErrorBody(mvc.perform(post(CONVERSATIONS + "/{id}/messages/stream", id).contentType(JSON)
				.accept(MediaType.TEXT_EVENT_STREAM).content("{\"content\":\" \"}")), 400, "Bad Request",
				CONVERSATIONS + "/" + id + "/messages/stream");
		expectErrorBody(mvc.perform(post(CONVERSATIONS + "/{id}/messages/stream", id).contentType(JSON)
				.accept(MediaType.TEXT_EVENT_STREAM).content(contentBody("x".repeat(51)))), 400, "Bad Request",
				CONVERSATIONS + "/" + id + "/messages/stream");

		assertThat(messageRepository.count()).isZero();
		assertThat(fakeLlm.calls()).isEmpty();
	}

}
