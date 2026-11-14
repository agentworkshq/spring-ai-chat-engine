package com.agentworkshq.chatengine.controller;

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

import com.agentworkshq.chatengine.dto.ConversationDetailResponse;
import com.agentworkshq.chatengine.dto.ConversationSummaryResponse;
import com.agentworkshq.chatengine.dto.CreateConversationRequest;
import com.agentworkshq.chatengine.dto.UpdateConversationRequest;
import com.agentworkshq.chatengine.service.ConversationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationController {

	private final ConversationService service;

	public ConversationController(ConversationService service) {
		this.service = service;
	}

	@PostMapping
	public ResponseEntity<ConversationSummaryResponse> create(@Valid @RequestBody CreateConversationRequest request) {
		ConversationSummaryResponse created = service.create(request);
		return ResponseEntity.created(URI.create("/api/v1/conversations/" + created.id())).body(created);
	}

	@GetMapping
	public List<ConversationSummaryResponse> list() {
		return service.list();
	}

	@GetMapping("/{id}")
	public ConversationDetailResponse get(@PathVariable UUID id) {
		return service.get(id);
	}

	@PatchMapping("/{id}")
	public ConversationSummaryResponse update(@PathVariable UUID id,
			@Valid @RequestBody UpdateConversationRequest request) {
		return service.update(id, request);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable UUID id) {
		service.delete(id);
		return ResponseEntity.noContent().build();
	}
}
