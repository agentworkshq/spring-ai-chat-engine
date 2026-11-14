package com.agentworkshq.chatengine.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.agentworkshq.chatengine.dto.ConversationDetailResponse;
import com.agentworkshq.chatengine.dto.ConversationSummaryResponse;
import com.agentworkshq.chatengine.dto.CreateConversationRequest;
import com.agentworkshq.chatengine.dto.MessageResponse;
import com.agentworkshq.chatengine.dto.UpdateConversationRequest;
import com.agentworkshq.chatengine.entity.Conversation;
import com.agentworkshq.chatengine.exception.NotFoundException;
import com.agentworkshq.chatengine.repository.ConversationRepository;
import com.agentworkshq.chatengine.repository.MessageRepository;

@Service
public class ConversationService {

	private final ConversationRepository conversations;
	private final MessageRepository messages;

	public ConversationService(ConversationRepository conversations, MessageRepository messages) {
		this.conversations = conversations;
		this.messages = messages;
	}

	@Transactional
	public ConversationSummaryResponse create(CreateConversationRequest request) {
		Conversation saved = conversations.save(Conversation.builder()
				.title(request.title().trim())
				.systemPrompt(request.systemPrompt())
				.model(request.model())
				.temperature(request.temperature())
				.build());
		return ConversationSummaryResponse.from(saved);
	}

	@Transactional(readOnly = true)
	public List<ConversationSummaryResponse> list() {
		return conversations.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
				.map(ConversationSummaryResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public ConversationDetailResponse get(UUID id) {
		Conversation conversation = find(id);
		List<MessageResponse> history = messages.findByConversationIdOrderByCreatedAtAsc(id).stream()
				.map(MessageResponse::from)
				.toList();
		return ConversationDetailResponse.from(conversation, history);
	}

	@Transactional
	public ConversationSummaryResponse update(UUID id, UpdateConversationRequest request) {
		Conversation conversation = find(id);
		if (request.title() != null) {
			conversation.setTitle(request.title().trim());
		}
		if (request.systemPrompt() != null) {
			conversation.setSystemPrompt(blankToNull(request.systemPrompt()));
		}
		if (request.model() != null) {
			conversation.setModel(blankToNull(request.model()));
		}
		if (request.temperature() != null) {
			conversation.setTemperature(request.temperature());
		}
		return ConversationSummaryResponse.from(conversations.saveAndFlush(conversation));
	}

	private static String blankToNull(String value) {
		return value.isBlank() ? null : value.trim();
	}

	@Transactional
	public void delete(UUID id) {
		if (!conversations.existsById(id)) {
			throw notFound(id);
		}
		conversations.deleteById(id);
	}

	private Conversation find(UUID id) {
		return conversations.findById(id).orElseThrow(() -> notFound(id));
	}

	private NotFoundException notFound(UUID id) {
		return new NotFoundException("Conversation " + id + " not found");
	}
}
