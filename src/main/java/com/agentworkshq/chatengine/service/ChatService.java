package com.agentworkshq.chatengine.service;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.agentworkshq.chatengine.config.ChatProperties;
import com.agentworkshq.chatengine.dto.MessageResponse;
import com.agentworkshq.chatengine.dto.StreamEvent;
import com.agentworkshq.chatengine.entity.Conversation;
import com.agentworkshq.chatengine.entity.Message;
import com.agentworkshq.chatengine.entity.MessageRole;
import com.agentworkshq.chatengine.exception.InvalidRequestException;
import com.agentworkshq.chatengine.exception.LlmException;
import com.agentworkshq.chatengine.exception.NotFoundException;
import com.agentworkshq.chatengine.repository.ConversationRepository;
import com.agentworkshq.chatengine.repository.MessageRepository;
import com.agentworkshq.chatengine.service.LlmClient.LlmMessage;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Runs one chat turn. Deliberately not {@code @Transactional}: the two database steps each run in
 * their own short transaction so no connection is held while waiting on the LLM.
 */
@Service
public class ChatService {

	private static final Logger log = LoggerFactory.getLogger(ChatService.class);

	private final ConversationRepository conversations;
	private final MessageRepository messages;
	private final LlmClient llmClient;
	private final PromptPlanner promptPlanner;
	private final ChatProperties properties;
	private final TransactionTemplate tx;

	public ChatService(ConversationRepository conversations, MessageRepository messages, LlmClient llmClient,
			PromptPlanner promptPlanner, ChatProperties properties, PlatformTransactionManager transactionManager) {
		this.conversations = conversations;
		this.messages = messages;
		this.llmClient = llmClient;
		this.promptPlanner = promptPlanner;
		this.properties = properties;
		this.tx = new TransactionTemplate(transactionManager);
	}

	public MessageResponse sendMessage(UUID conversationId, String content) {
		TurnContext turn = beginTurn(conversationId, content);

		// No transaction (and no DB connection) held during the LLM call.
		String reply = llmClient.complete(turn.history(), turn.systemPrompt(), turn.model(), turn.temperature());

		return saveAssistantMessage(conversationId, reply);
	}

	/**
	 * Streams the reply as events. The user message is validated and saved before this returns, so
	 * a bad request or unknown conversation fails as an ordinary HTTP error. The assistant message
	 * is saved only if the stream completes: on an error or a client disconnect the partial reply
	 * is discarded.
	 */
	public Flux<StreamEvent> streamMessage(UUID conversationId, String content) {
		TurnContext turn = beginTurn(conversationId, content);

		return Flux.defer(() -> {
			StringBuilder reply = new StringBuilder();
			return llmClient.stream(turn.history(), turn.systemPrompt(), turn.model(), turn.temperature())
					.filter(chunk -> !chunk.isEmpty())
					.doOnNext(reply::append)
					.map(StreamEvent::token)
					.concatWith(Mono.fromCallable(() -> StreamEvent.done(completeStream(conversationId, reply)))
							.subscribeOn(Schedulers.boundedElastic()))
					.onErrorResume(ex -> Mono.just(toErrorEvent(conversationId, ex)))
					.doOnCancel(() -> log.info(
							"Client disconnected mid-stream for conversation {}; discarding partial reply ({} chars)",
							conversationId, reply.length()));
		});
	}

	private MessageResponse completeStream(UUID conversationId, StringBuilder reply) {
		if (reply.toString().isBlank()) {
			throw new LlmException("The language model returned an empty reply");
		}
		return saveAssistantMessage(conversationId, reply.toString());
	}

	private StreamEvent toErrorEvent(UUID conversationId, Throwable ex) {
		if (ex instanceof LlmException) {
			// The client already logged the provider detail it is allowed to log.
			log.warn("Stream failed for conversation {}: {}; partial reply discarded", conversationId,
					ex.getMessage());
			return StreamEvent.error(ex.getMessage());
		}
		log.error("Stream failed for conversation {} while saving the reply", conversationId, ex);
		return StreamEvent.error("Failed to save the reply");
	}

	/** Transaction 1: validate, persist the user message and snapshot everything the LLM call needs. */
	private TurnContext beginTurn(UUID conversationId, String content) {
		if (content.length() > properties.maxInputChars()) {
			throw new InvalidRequestException(
					"content must be at most " + properties.maxInputChars() + " characters");
		}
		return tx.execute(status -> {
			Conversation conversation = conversations.findById(conversationId)
					.orElseThrow(() -> notFound(conversationId));
			messages.save(Message.builder()
					.conversation(conversation)
					.role(MessageRole.USER)
					.content(content)
					.build());
			List<Message> recent = messages.findByConversationIdOrderByCreatedAtDesc(conversationId,
					PageRequest.of(0, promptPlanner.historyWindow()));
			return new TurnContext(promptPlanner.history(recent),
					promptPlanner.systemPrompt(conversation.getSystemPrompt()), conversation.getModel(),
					conversation.getTemperature());
		});
	}

	/** Transaction 2: persist the assistant reply. */
	private MessageResponse saveAssistantMessage(UUID conversationId, String reply) {
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
