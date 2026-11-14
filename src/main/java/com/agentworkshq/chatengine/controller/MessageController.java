package com.agentworkshq.chatengine.controller;

import static com.agentworkshq.chatengine.config.OpenApiExamples.*;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.agentworkshq.chatengine.dto.ApiError;
import com.agentworkshq.chatengine.dto.MessageResponse;
import com.agentworkshq.chatengine.dto.SendMessageRequest;
import com.agentworkshq.chatengine.service.ChatService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/v1/conversations/{id}/messages")
@Tag(name = "Messages", description = "Send a message and get the model's reply, as one response or streamed")
public class MessageController {

	private final ChatService chatService;

	public MessageController(ChatService chatService) {
		this.chatService = chatService;
	}

	@Operation(summary = "Send a message and get the reply",
			description = "Saves your message, sends the conversation (system prompt first, then the most recent "
					+ "messages) to the language model, saves the reply and returns it. Rate limited per client IP. "
					+ "If the model call fails nothing is saved for the reply, but your message stays in the "
					+ "conversation.",
			requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
					content = @Content(mediaType = "application/json",
							schema = @Schema(implementation = SendMessageRequest.class),
							examples = @ExampleObject(name = "A question", value = SEND_REQUEST))))
	@ApiResponse(responseCode = "201", description = "The assistant's reply, already saved",
			content = @Content(mediaType = "application/json",
					schema = @Schema(implementation = MessageResponse.class),
					examples = @ExampleObject(name = "Reply", value = MESSAGE_RESPONSE)))
	@ApiResponse(responseCode = "400", description = "Blank content, content longer than the limit, malformed "
			+ "JSON, or the id is not a valid UUID",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class),
					examples = @ExampleObject(name = "Too long", value = ERROR_MESSAGE_TOO_LONG)))
	@ApiResponse(responseCode = "404", description = "No conversation with this id",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class),
					examples = @ExampleObject(name = "Unknown id", value = ERROR_NOT_FOUND)))
	@ApiResponse(responseCode = "429", description = "Either this server's per-IP rate limit was hit, or the "
			+ "language model provider is rate limiting. Retry-After says how many seconds to wait, when known.",
			headers = @Header(name = "Retry-After", description = "Seconds to wait before retrying",
					schema = @Schema(type = "integer", example = "20")),
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class),
					examples = {
							@ExampleObject(name = "Per-IP rate limit", value = ERROR_RATE_LIMITED),
							@ExampleObject(name = "Provider rate limit", value = ERROR_LLM_RATE_LIMITED) }))
	@ApiResponse(responseCode = "502", description = "The language model answered with something unusable, or "
			+ "rejected the request",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class),
					examples = @ExampleObject(name = "Bad upstream response", value = ERROR_LLM_BAD_RESPONSE)))
	@ApiResponse(responseCode = "503", description = "The language model is down, overloaded or timed out, even "
			+ "after retries. Retry-After is included when the provider gave one.",
			headers = @Header(name = "Retry-After", description = "Seconds to wait before retrying, when known",
					schema = @Schema(type = "integer", example = "10")),
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class),
					examples = @ExampleObject(name = "Provider unavailable", value = ERROR_LLM_UNAVAILABLE)))
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public MessageResponse send(
			@Parameter(description = "Conversation id", example = CONVERSATION_ID) @PathVariable UUID id,
			@Valid @RequestBody SendMessageRequest request) {
		return chatService.sendMessage(id, request.content());
	}

	@Operation(summary = "Send a message and stream the reply",
			description = "Same as sending a message, but the reply is streamed as server-sent events "
					+ "(`text/event-stream`) while it is generated.\n\n"
					+ "Events:\n"
					+ "* `token`: a chunk of the reply, data `{\"content\": \"...\"}`\n"
					+ "* `done`: the full reply was saved, data is the saved message (same shape as the "
					+ "non-streaming response)\n"
					+ "* `error`: the stream failed after it had started, data `{\"message\": \"...\"}`; "
					+ "nothing was saved for the reply\n\n"
					+ "If the client disconnects mid-stream the partial reply is discarded and not saved. "
					+ "Problems detected before streaming starts (validation, unknown conversation, rate limit) are "
					+ "ordinary JSON error responses. Shares the rate limit of the non-streaming endpoint.",
			requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
					content = @Content(mediaType = "application/json",
							schema = @Schema(implementation = SendMessageRequest.class),
							examples = @ExampleObject(name = "A question", value = SEND_REQUEST))))
	@ApiResponse(responseCode = "200", description = "A stream of server-sent events",
			content = @Content(mediaType = "text/event-stream", schema = @Schema(type = "string"),
					examples = {
							@ExampleObject(name = "Completed stream", value = STREAM_SUCCESS),
							@ExampleObject(name = "Stream that failed midway", value = STREAM_ERROR) }))
	@ApiResponse(responseCode = "400", description = "Blank content, content longer than the limit, malformed "
			+ "JSON, or the id is not a valid UUID",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class),
					examples = @ExampleObject(name = "Too long", value = ERROR_MESSAGE_TOO_LONG)))
	@ApiResponse(responseCode = "404", description = "No conversation with this id",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class),
					examples = @ExampleObject(name = "Unknown id", value = ERROR_NOT_FOUND)))
	@ApiResponse(responseCode = "429", description = "This server's per-IP rate limit was hit (before streaming "
			+ "started). Provider rate limits arrive as an `error` event instead.",
			headers = @Header(name = "Retry-After", description = "Seconds to wait before retrying",
					schema = @Schema(type = "integer", example = "20")),
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class),
					examples = @ExampleObject(name = "Per-IP rate limit", value = ERROR_RATE_LIMITED)))
	@PostMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<ServerSentEvent<Object>> stream(
			@Parameter(description = "Conversation id", example = CONVERSATION_ID) @PathVariable UUID id,
			@Valid @RequestBody SendMessageRequest request) {
		return chatService.streamMessage(id, request.content())
				.map(e -> ServerSentEvent.<Object>builder().event(e.event()).data(e.data()).build());
	}

}
