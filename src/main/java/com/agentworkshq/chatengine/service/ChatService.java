package com.agentworkshq.chatengine.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.agentworkshq.chatengine.dto.MessageResponse;
import com.agentworkshq.chatengine.entity.Conversation;
import com.agentworkshq.chatengine.entity.Message;
import com.agentworkshq.chatengine.entity.MessageRole;
import com.agentworkshq.chatengine.exception.NotFoundException;
import com.agentworkshq.chatengine.repository.ConversationRepository;
import com.agentworkshq.chatengine.repository.MessageRepository;
import com.agentworkshq.chatengine.service.LlmClient.LlmMessage;

/**
 * Runs one chat turn. Deliberately not {@code @Transactional}: the two database steps each run in
 * their own short transaction so no connection is held while waiting on the LLM.
 */
@Service
public class ChatService {

	private final ConversationRepository conversations;
	private final MessageRepository messages;
	private final LlmClient llmClient;
	private final TransactionTemplate tx;

	public ChatService(ConversationRepository conversations, MessageRepository messages, LlmClient llmClient,
			PlatformTransactionManager transactionManager) {
		this.conversations = conversations;
		this.messages = messages;
		this.llmClient = llmClient;
		this.tx = new TransactionTemplate(transactionManager);
	}

	public MessageResponse sendMessage(UUID conversationId, String content) {
		// Transaction 1: persist the user message and snapshot everything the LLM call needs.
		TurnContext turn = tx.execute(status -> {
			Conversation conversation = conversations.findById(conversationId)
					.orElseThrow(() -> notFound(conversationId));
			messages.save(Message.builder()
					.conversation(conversation)
					.role(MessageRole.USER)
					.content(content)
					.build());
			List<LlmMessage> history = messages.findByConversationIdOrderByCreatedAtAsc(conversationId).stream()
					.map(m -> new LlmMessage(m.getRole(), m.getContent()))
					.toList();
			return new TurnContext(history, conversation.getSystemPrompt(), conversation.getModel(),
					conversation.getTemperature());
		});

		// No transaction (and no DB connection) held during the LLM call.
		String reply = llmClient.complete(turn.history(), turn.systemPrompt(), turn.model(), turn.temperature());

		// Transaction 2: persist the assistant reply.
		return tx.execute(status -> {
			Conversation conversation = conversations.findById(conversationId)
					.orElseThrow(() -> notFound(conversationId));
			Message saved = messages.save(Message.builder()
					.conversation(conversation)
					.role(MessageRole.ASSISTANT)
					.content(reply)
					.build());
			return MessageResponse.from(saved);
		});
	}

	private NotFoundException notFound(UUID id) {
		return new NotFoundException("Conversation " + id + " not found");
	}

	private record TurnContext(List<LlmMessage> history, String systemPrompt, String model, Double temperature) {
	}

}
