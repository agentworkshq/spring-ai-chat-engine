package com.agentworkshq.chatengine.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.agentworkshq.chatengine.dto.MessageResponse;
import com.agentworkshq.chatengine.dto.SendMessageRequest;
import com.agentworkshq.chatengine.service.ChatService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/conversations/{id}/messages")
public class MessageController {

	private final ChatService chatService;

	public MessageController(ChatService chatService) {
		this.chatService = chatService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public MessageResponse send(@PathVariable UUID id, @Valid @RequestBody SendMessageRequest request) {
		return chatService.sendMessage(id, request.content());
	}

}
