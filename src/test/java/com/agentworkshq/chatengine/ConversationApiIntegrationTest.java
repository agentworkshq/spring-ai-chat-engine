package com.agentworkshq.chatengine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.agentworkshq.chatengine.entity.Conversation;
import com.agentworkshq.chatengine.entity.Message;
import com.agentworkshq.chatengine.entity.MessageRole;
import com.jayway.jsonpath.JsonPath;

import java.util.stream.Stream;

/** Conversation CRUD, validation and 404 handling against a real database. */
class ConversationApiIntegrationTest extends ApiIntegrationTestSupport {

	private static final String UNKNOWN_ID = "00000000-0000-0000-0000-000000000000";

	// ---- create ----

	@Test
	void createReturns201WithLocationAndPersistsTheConversation() throws Exception {
		MvcResult result = createConversationRequest("""
				{"title":"Trip planning","systemPrompt":"Be brief","model":"some-model","temperature":0.4}""")
				.andExpect(status().isCreated())
				.andExpect(header().exists("X-Request-Id"))
				.andExpect(jsonPath("$.title").value("Trip planning"))
				.andExpect(jsonPath("$.model").value("some-model"))
				.andExpect(jsonPath("$.temperature").value(0.4))
				.andExpect(jsonPath("$.createdAt").isNotEmpty())
				.andExpect(jsonPath("$.updatedAt").isNotEmpty())
				.andReturn();

		String id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
		assertThat(result.getResponse().getHeader("Location")).isEqualTo(CONVERSATIONS + "/" + id);

		Conversation stored = conversationRepository.findById(UUID.fromString(id)).orElseThrow();
		assertThat(stored.getTitle()).isEqualTo("Trip planning");
		assertThat(stored.getSystemPrompt()).isEqualTo("Be brief");
		assertThat(stored.getModel()).isEqualTo("some-model");
		assertThat(stored.getTemperature()).isEqualTo(0.4);
		assertThat(stored.getCreatedAt()).isNotNull();
		assertThat(stored.getUpdatedAt()).isNotNull();
	}

	@Test
	void createWithOnlyATitleLeavesTheOverridesNull() throws Exception {
		createConversationRequest("{\"title\":\"Just a title\"}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.model").value(nullValue()))
				.andExpect(jsonPath("$.temperature").value(nullValue()));

		Conversation stored = conversationRepository.findAll().get(0);
		assertThat(stored.getSystemPrompt()).isNull();
		assertThat(stored.getModel()).isNull();
		assertThat(stored.getTemperature()).isNull();
	}

	@Test
	void createTrimsTheTitle() throws Exception {
		createConversationRequest("{\"title\":\"   Padded   \"}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.title").value("Padded"));
	}

	@Test
	void createAcceptsATitleOfExactlyTwoHundredCharacters() throws Exception {
		createConversationRequest("{\"title\":\"" + "x".repeat(200) + "\"}").andExpect(status().isCreated());
	}

	static Stream<Arguments> invalidCreateBodies() {
		return Stream.of(
				Arguments.of("blank title", "{\"title\":\"   \"}", "title"),
				Arguments.of("empty title", "{\"title\":\"\"}", "title"),
				Arguments.of("missing title", "{}", "title"),
				Arguments.of("null title", "{\"title\":null}", "title"),
				Arguments.of("title over 200 chars", "{\"title\":\"" + "x".repeat(201) + "\"}", "title"),
				Arguments.of("temperature below 0", "{\"title\":\"t\",\"temperature\":-0.1}", "temperature"),
				Arguments.of("temperature above 2", "{\"title\":\"t\",\"temperature\":2.1}", "temperature"),
				Arguments.of("model over 255 chars", "{\"title\":\"t\",\"model\":\"" + "m".repeat(256) + "\"}",
						"model"));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("invalidCreateBodies")
	void createRejectsInvalidInputWithFieldDetailsAndSavesNothing(String description, String body, String field)
			throws Exception {
		ResultActions result = createConversationRequest(body);

		expectErrorBody(result, 400, "Bad Request", CONVERSATIONS);
		result.andExpect(jsonPath("$.message").value(containsString(field)));
		assertThat(conversationRepository.count()).isZero();
	}

	@Test
	void createRejectsMalformedJson() throws Exception {
		expectErrorBody(createConversationRequest("{not json"), 400, "Bad Request", CONVERSATIONS);
		assertThat(conversationRepository.count()).isZero();
	}

	@Test
	void createAcceptsTheTemperatureBounds() throws Exception {
		createConversationRequest("{\"title\":\"cold\",\"temperature\":0.0}").andExpect(status().isCreated());
		createConversationRequest("{\"title\":\"hot\",\"temperature\":2.0}").andExpect(status().isCreated());
	}

	// ---- list ----

	@Test
	void listIsEmptyWhenThereAreNoConversations() throws Exception {
		mvc.perform(get(CONVERSATIONS))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void listReturnsConversationsNewestFirst() throws Exception {
		createConversation("{\"title\":\"first\"}");
		Thread.sleep(5);
		createConversation("{\"title\":\"second\"}");
		Thread.sleep(5);
		createConversation("{\"title\":\"third\"}");

		mvc.perform(get(CONVERSATIONS))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(3)))
				.andExpect(jsonPath("$[0].title").value("third"))
				.andExpect(jsonPath("$[1].title").value("second"))
				.andExpect(jsonPath("$[2].title").value("first"));
	}

	// ---- get ----

	@Test
	void getReturnsTheSettingsAndAnEmptyMessageList() throws Exception {
		String id = createConversation("""
				{"title":"Settings","systemPrompt":"Answer in French","model":"m1","temperature":1.1}""");

		getConversation(id)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.title").value("Settings"))
				.andExpect(jsonPath("$.systemPrompt").value("Answer in French"))
				.andExpect(jsonPath("$.model").value("m1"))
				.andExpect(jsonPath("$.temperature").value(1.1))
				.andExpect(jsonPath("$.messages", hasSize(0)));
	}

	@Test
	void getReturnsMessagesOldestFirst() throws Exception {
		String id = createConversation();
		Conversation conversation = conversationRepository.findById(UUID.fromString(id)).orElseThrow();
		for (String text : new String[] { "one", "two", "three" }) {
			messageRepository.save(Message.builder().conversation(conversation)
					.role(text.equals("two") ? MessageRole.ASSISTANT : MessageRole.USER).content(text).build());
			Thread.sleep(5);
		}

		getConversation(id)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.messages", hasSize(3)))
				.andExpect(jsonPath("$.messages[0].content").value("one"))
				.andExpect(jsonPath("$.messages[0].role").value("USER"))
				.andExpect(jsonPath("$.messages[1].content").value("two"))
				.andExpect(jsonPath("$.messages[1].role").value("ASSISTANT"))
				.andExpect(jsonPath("$.messages[2].content").value("three"));
	}

	// ---- update ----

	@Test
	void patchChangesOnlyTheGivenFieldsAndBumpsUpdatedAt() throws Exception {
		String id = createConversation("""
				{"title":"Old","systemPrompt":"keep me","model":"keep-model","temperature":0.9}""");
		Instant before = conversationRepository.findById(UUID.fromString(id)).orElseThrow().getUpdatedAt();
		Thread.sleep(5);

		patchConversation(id, "{\"title\":\"New title\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("New title"))
				.andExpect(jsonPath("$.model").value("keep-model"))
				.andExpect(jsonPath("$.temperature").value(0.9));

		Conversation stored = conversationRepository.findById(UUID.fromString(id)).orElseThrow();
		assertThat(stored.getSystemPrompt()).isEqualTo("keep me");
		assertThat(stored.getUpdatedAt()).isAfter(before);
		assertThat(stored.getCreatedAt()).isBefore(stored.getUpdatedAt());
	}

	@Test
	void patchSetsSystemPromptModelAndTemperature() throws Exception {
		String id = createConversation();

		patchConversation(id, """
				{"systemPrompt":"New prompt","model":"new-model","temperature":0.2}""")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.model").value("new-model"))
				.andExpect(jsonPath("$.temperature").value(0.2));

		getConversation(id).andExpect(jsonPath("$.systemPrompt").value("New prompt"));
	}

	@Test
	void patchWithBlankPromptAndModelClearsThem() throws Exception {
		String id = createConversation("""
				{"title":"t","systemPrompt":"set","model":"set-model","temperature":0.5}""");

		patchConversation(id, "{\"systemPrompt\":\"\",\"model\":\"  \"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.model").value(nullValue()))
				.andExpect(jsonPath("$.temperature").value(0.5));

		getConversation(id).andExpect(jsonPath("$.systemPrompt").value(nullValue()));
	}

	@Test
	void patchWithAnEmptyBodyChangesNothing() throws Exception {
		String id = createConversation("{\"title\":\"Same\",\"model\":\"m\"}");

		patchConversation(id, "{}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("Same"))
				.andExpect(jsonPath("$.model").value("m"));
	}

	static Stream<Arguments> invalidPatchBodies() {
		return Stream.of(
				Arguments.of("blank title", "{\"title\":\"  \"}", "title"),
				Arguments.of("title over 200 chars", "{\"title\":\"" + "x".repeat(201) + "\"}", "title"),
				Arguments.of("temperature above 2", "{\"temperature\":3.5}", "temperature"),
				Arguments.of("temperature below 0", "{\"temperature\":-1}", "temperature"),
				Arguments.of("model over 255 chars", "{\"model\":\"" + "m".repeat(256) + "\"}", "model"));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("invalidPatchBodies")
	void patchRejectsInvalidInputAndLeavesTheConversationUntouched(String description, String body, String field)
			throws Exception {
		String id = createConversation("{\"title\":\"Original\",\"temperature\":0.5}");

		expectErrorBody(patchConversation(id, body), 400, "Bad Request", CONVERSATIONS + "/" + id);

		getConversation(id)
				.andExpect(jsonPath("$.title").value("Original"))
				.andExpect(jsonPath("$.temperature").value(0.5));
	}

	// ---- delete ----

	@Test
	void deleteReturns204AndAlsoRemovesTheMessages() throws Exception {
		String id = createConversation();
		sendMessage(id, "hello").andExpect(status().isCreated());
		assertThat(messageRepository.count()).isEqualTo(2);

		deleteConversation(id).andExpect(status().isNoContent());

		assertThat(conversationRepository.existsById(UUID.fromString(id))).isFalse();
		assertThat(messageRepository.count()).as("messages are removed by ON DELETE CASCADE").isZero();
		getConversation(id).andExpect(status().isNotFound());
	}

	@Test
	void deleteOnlyAffectsTheTargetedConversation() throws Exception {
		String keep = createConversation("{\"title\":\"keep\"}");
		String remove = createConversation("{\"title\":\"remove\"}");
		sendMessage(keep, "stay").andExpect(status().isCreated());
		sendMessage(remove, "go").andExpect(status().isCreated());

		deleteConversation(remove).andExpect(status().isNoContent());

		getConversation(keep).andExpect(status().isOk()).andExpect(jsonPath("$.messages", hasSize(2)));
		assertThat(messageRepository.count()).isEqualTo(2);
	}

	@Test
	void deletingTwiceReturns404TheSecondTime() throws Exception {
		String id = createConversation();

		deleteConversation(id).andExpect(status().isNoContent());
		deleteConversation(id).andExpect(status().isNotFound());
	}

	// ---- 404 and bad ids ----

	@Test
	void unknownIdIs404ForGetPatchAndDeleteWithTheStandardBody() throws Exception {
		String path = CONVERSATIONS + "/" + UNKNOWN_ID;

		expectErrorBody(getConversation(UNKNOWN_ID), 404, "Not Found", path);
		expectErrorBody(patchConversation(UNKNOWN_ID, "{\"title\":\"x\"}"), 404, "Not Found", path);
		expectErrorBody(deleteConversation(UNKNOWN_ID), 404, "Not Found", path);
		getConversation(UNKNOWN_ID).andExpect(jsonPath("$.message").value(
				allOf(containsString(UNKNOWN_ID),
						containsString("not found"))));
	}

	@Test
	void aMalformedIdIs400NotAServerError() throws Exception {
		expectErrorBody(getConversation("not-a-uuid"), 400, "Bad Request", CONVERSATIONS + "/not-a-uuid");
		expectErrorBody(patchConversation("123", "{\"title\":\"x\"}"), 400, "Bad Request", CONVERSATIONS + "/123");
		expectErrorBody(deleteConversation("abc"), 400, "Bad Request", CONVERSATIONS + "/abc");
	}

	@Test
	void unsupportedMethodsKeepTheirStatusAndTheStandardBody() throws Exception {
		String id = createConversation();

		expectErrorBody(mvc.perform(put(CONVERSATIONS + "/{id}", id)), 405, "Method Not Allowed",
				CONVERSATIONS + "/" + id);
	}

	@Test
	void everyResponseCarriesARequestIdAndTheClientCanSupplyOne() throws Exception {
		mvc.perform(get(CONVERSATIONS)
				.header("X-Request-Id", "client-supplied-123"))
				.andExpect(header().string("X-Request-Id", "client-supplied-123"));
		getConversation(UNKNOWN_ID).andExpect(header().string("X-Request-Id", not(
				emptyString())));
	}

}
