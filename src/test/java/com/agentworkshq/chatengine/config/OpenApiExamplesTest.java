package com.agentworkshq.chatengine.config;

import static com.agentworkshq.chatengine.config.OpenApiExamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.agentworkshq.chatengine.dto.ApiError;
import com.agentworkshq.chatengine.dto.ConversationDetailResponse;
import com.agentworkshq.chatengine.dto.ConversationSummaryResponse;
import com.agentworkshq.chatengine.dto.CreateConversationRequest;
import com.agentworkshq.chatengine.dto.MessageResponse;
import com.agentworkshq.chatengine.dto.SendMessageRequest;
import com.agentworkshq.chatengine.dto.StreamEvent;
import com.agentworkshq.chatengine.dto.UpdateConversationRequest;
import com.agentworkshq.chatengine.entity.MessageRole;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * The documentation examples are hand-written text, so this keeps them honest: each one must
 * deserialize into the real DTO (an unknown or renamed field fails here) and every example request
 * must pass the real validation rules.
 */
class OpenApiExamplesTest {

	private final JsonMapper mapper = JsonMapper.builder().build();
	private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

	@Test
	void requestExamplesAreValidRequests() {
		assertThat(validator.validate(mapper.readValue(CREATE_REQUEST, CreateConversationRequest.class))).isEmpty();
		assertThat(validator.validate(mapper.readValue(CREATE_REQUEST_MINIMAL, CreateConversationRequest.class)))
				.isEmpty();
		assertThat(validator.validate(mapper.readValue(UPDATE_REQUEST, UpdateConversationRequest.class))).isEmpty();
		assertThat(validator.validate(mapper.readValue(UPDATE_REQUEST_CLEAR, UpdateConversationRequest.class)))
				.isEmpty();
		assertThat(validator.validate(mapper.readValue(SEND_REQUEST, SendMessageRequest.class))).isEmpty();
	}

	@Test
	void conversationExamplesMatchTheResponseShapes() {
		ConversationSummaryResponse summary = mapper.readValue(CONVERSATION_SUMMARY, ConversationSummaryResponse.class);
		assertThat(summary.id()).isEqualTo(UUID.fromString(CONVERSATION_ID));
		assertThat(summary.temperature()).isEqualTo(0.4);

		ConversationSummaryResponse defaults = mapper.readValue(CONVERSATION_SUMMARY_DEFAULTS,
				ConversationSummaryResponse.class);
		assertThat(defaults.model()).isNull();
		assertThat(defaults.temperature()).isNull();

		List<ConversationSummaryResponse> list = mapper.readValue(CONVERSATION_LIST,
				new TypeReference<List<ConversationSummaryResponse>>() {
				});
		assertThat(list).hasSize(2);
		assertThat(list.get(0).createdAt()).isAfter(list.get(1).createdAt()); // newest first, like the API

		ConversationDetailResponse detail = mapper.readValue(CONVERSATION_DETAIL, ConversationDetailResponse.class);
		assertThat(detail.messages()).extracting(MessageResponse::role)
				.containsExactly(MessageRole.USER, MessageRole.ASSISTANT);
		assertThat(detail.messages().get(0).createdAt()).isBefore(detail.messages().get(1).createdAt());
	}

	@Test
	void messageExampleMatchesTheResponseShape() {
		MessageResponse message = mapper.readValue(MESSAGE_RESPONSE, MessageResponse.class);

		assertThat(message.role()).isEqualTo(MessageRole.ASSISTANT);
	}

	@Test
	void everyErrorExampleHasTheStandardBodyAndAMatchingStatusText() {
		record Case(String json, int status, String error) {
		}
		for (Case c : List.of(
				new Case(ERROR_VALIDATION, 400, "Bad Request"),
				new Case(ERROR_MESSAGE_TOO_LONG, 400, "Bad Request"),
				new Case(ERROR_BAD_ID, 400, "Bad Request"),
				new Case(ERROR_NOT_FOUND, 404, "Not Found"),
				new Case(ERROR_RATE_LIMITED, 429, "Too Many Requests"),
				new Case(ERROR_LLM_RATE_LIMITED, 429, "Too Many Requests"),
				new Case(ERROR_LLM_UNAVAILABLE, 503, "Service Unavailable"),
				new Case(ERROR_LLM_BAD_RESPONSE, 502, "Bad Gateway"))) {
			ApiError error = mapper.readValue(c.json(), ApiError.class);

			assertThat(error.status()).as(c.json()).isEqualTo(c.status());
			assertThat(error.error()).isEqualTo(c.error());
			assertThat(error.timestamp()).isNotNull();
			assertThat(error.message()).isNotBlank();
			assertThat(error.path()).startsWith("/api/v1/conversations");
		}
	}

	@Test
	void errorExampleMessagesAreTheRealOnesTheServerSends() {
		assertThat(mapper.readValue(ERROR_LLM_RATE_LIMITED, ApiError.class).message())
				.isEqualTo(com.agentworkshq.chatengine.exception.LlmRateLimitedException.MESSAGE);
		assertThat(mapper.readValue(ERROR_LLM_UNAVAILABLE, ApiError.class).message())
				.isEqualTo(com.agentworkshq.chatengine.exception.LlmUnavailableException.MESSAGE);
	}

	@Test
	void streamExamplesUseTheRealEventNamesAndPayloads() {
		for (String stream : List.of(STREAM_SUCCESS, STREAM_ERROR)) {
			String[] events = stream.strip().split("\n\n");
			for (String event : events) {
				String[] lines = event.split("\n");
				String name = lines[0].substring("event:".length());
				String data = lines[1].substring("data:".length());
				switch (name) {
					case "token" -> assertThat(mapper.readValue(data, StreamEvent.Token.class).content()).isNotEmpty();
					case "done" -> assertThat(mapper.readValue(data, MessageResponse.class).role())
							.isEqualTo(MessageRole.ASSISTANT);
					case "error" -> assertThat(mapper.readValue(data, StreamEvent.Error.class).message())
							.isNotBlank();
					default -> throw new AssertionError("Unknown event name in example: " + name);
				}
			}
		}
	}

}
