package com.agentworkshq.chatengine.controller;

import static com.agentworkshq.chatengine.config.OpenApiExamples.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.agentworkshq.chatengine.dto.ApiError;
import com.agentworkshq.chatengine.dto.ConversationDetailResponse;
import com.agentworkshq.chatengine.dto.ConversationSummaryResponse;
import com.agentworkshq.chatengine.dto.CreateConversationRequest;
import com.agentworkshq.chatengine.dto.UpdateConversationRequest;
import com.agentworkshq.chatengine.service.ConversationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/conversations")
@Tag(name = "Conversations", description = "Create, list, read, rename and delete conversations")
public class ConversationController {

	private final ConversationService service;

	public ConversationController(ConversationService service) {
		this.service = service;
	}

	@Operation(summary = "Create a conversation",
			description = "Creates an empty conversation. The system prompt, model and temperature are optional "
					+ "overrides for every message sent to this conversation; leave them out to use the server "
					+ "defaults.",
			requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
					content = @Content(mediaType = "application/json",
							schema = @Schema(implementation = CreateConversationRequest.class),
							examples = {
									@ExampleObject(name = "With overrides", value = CREATE_REQUEST),
									@ExampleObject(name = "Title only", value = CREATE_REQUEST_MINIMAL) })))
	@ApiResponse(responseCode = "201", description = "Conversation created. The Location header points to it.",
			headers = @Header(name = "Location", description = "URL of the new conversation",
					schema = @Schema(type = "string",
							example = "/api/v1/conversations/3f6c1b2e-8a4d-4c7e-9b1a-2d5e7f9a0c11")),
			content = @Content(mediaType = "application/json",
					schema = @Schema(implementation = ConversationSummaryResponse.class),
					examples = {
							@ExampleObject(name = "With overrides", value = CONVERSATION_SUMMARY),
							@ExampleObject(name = "Title only", value = CONVERSATION_SUMMARY_DEFAULTS) }))
	@ApiResponse(responseCode = "400", description = "Validation failed (blank or too long title, temperature "
			+ "outside 0.0 to 2.0, malformed JSON)",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class),
					examples = @ExampleObject(name = "Blank title", value = ERROR_VALIDATION)))
	@PostMapping
	public ResponseEntity<ConversationSummaryResponse> create(@Valid @RequestBody CreateConversationRequest request) {
		ConversationSummaryResponse created = service.create(request);
		return ResponseEntity.created(URI.create("/api/v1/conversations/" + created.id())).body(created);
	}

	@Operation(summary = "List conversations", description = "Returns all conversations, newest first, without "
			+ "their messages.")
	@ApiResponse(responseCode = "200", description = "The conversations (empty array if there are none)",
			content = @Content(mediaType = "application/json",
					array = @ArraySchema(schema = @Schema(implementation = ConversationSummaryResponse.class)),
					examples = @ExampleObject(name = "Two conversations", value = CONVERSATION_LIST)))
	@GetMapping
	public List<ConversationSummaryResponse> list() {
		return service.list();
	}

	@Operation(summary = "Get a conversation with its messages",
			description = "Returns the conversation settings and every message, oldest first.")
	@ApiResponse(responseCode = "200", description = "The conversation and its messages",
			content = @Content(mediaType = "application/json",
					schema = @Schema(implementation = ConversationDetailResponse.class),
					examples = @ExampleObject(name = "With two messages", value = CONVERSATION_DETAIL)))
	@ApiResponse(responseCode = "400", description = "The id is not a valid UUID",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class),
					examples = @ExampleObject(name = "Bad id", value = ERROR_BAD_ID)))
	@ApiResponse(responseCode = "404", description = "No conversation with this id",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class),
					examples = @ExampleObject(name = "Unknown id", value = ERROR_NOT_FOUND)))
	@GetMapping("/{id}")
	public ConversationDetailResponse get(
			@Parameter(description = "Conversation id", example = CONVERSATION_ID) @PathVariable UUID id) {
		return service.get(id);
	}

	@Operation(summary = "Update a conversation",
			description = "Partial update of the title, system prompt, model and temperature. Fields you leave out "
					+ "stay unchanged. A blank systemPrompt or model clears that override so the server default "
					+ "applies again. The temperature can be changed but not cleared.",
			requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
					content = @Content(mediaType = "application/json",
							schema = @Schema(implementation = UpdateConversationRequest.class),
							examples = {
									@ExampleObject(name = "Rename and cool down", value = UPDATE_REQUEST),
									@ExampleObject(name = "Clear the prompt and model overrides",
											value = UPDATE_REQUEST_CLEAR) })))
	@ApiResponse(responseCode = "200", description = "The updated conversation",
			content = @Content(mediaType = "application/json",
					schema = @Schema(implementation = ConversationSummaryResponse.class),
					examples = @ExampleObject(name = "Updated", value = CONVERSATION_SUMMARY)))
	@ApiResponse(responseCode = "400", description = "Validation failed (blank or too long title, temperature "
			+ "outside 0.0 to 2.0, malformed JSON) or the id is not a valid UUID",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class),
					examples = @ExampleObject(name = "Blank title", value = ERROR_VALIDATION)))
	@ApiResponse(responseCode = "404", description = "No conversation with this id",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class),
					examples = @ExampleObject(name = "Unknown id", value = ERROR_NOT_FOUND)))
	@PatchMapping("/{id}")
	public ConversationSummaryResponse update(
			@Parameter(description = "Conversation id", example = CONVERSATION_ID) @PathVariable UUID id,
			@Valid @RequestBody UpdateConversationRequest request) {
		return service.update(id, request);
	}

	@Operation(summary = "Delete a conversation",
			description = "Deletes the conversation and all of its messages. This cannot be undone.")
	@ApiResponse(responseCode = "204", description = "Deleted", content = @Content)
	@ApiResponse(responseCode = "400", description = "The id is not a valid UUID",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class),
					examples = @ExampleObject(name = "Bad id", value = ERROR_BAD_ID)))
	@ApiResponse(responseCode = "404", description = "No conversation with this id",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class),
					examples = @ExampleObject(name = "Unknown id", value = ERROR_NOT_FOUND)))
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(
			@Parameter(description = "Conversation id", example = CONVERSATION_ID) @PathVariable UUID id) {
		service.delete(id);
		return ResponseEntity.noContent().build();
	}
}
