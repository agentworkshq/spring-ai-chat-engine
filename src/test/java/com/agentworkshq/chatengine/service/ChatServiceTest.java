package com.agentworkshq.chatengine.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;

import com.agentworkshq.chatengine.config.ChatProperties;
import com.agentworkshq.chatengine.dto.MessageResponse;
import com.agentworkshq.chatengine.entity.Conversation;
import com.agentworkshq.chatengine.entity.Message;
import com.agentworkshq.chatengine.entity.MessageRole;
import com.agentworkshq.chatengine.exception.InvalidRequestException;
import com.agentworkshq.chatengine.exception.NotFoundException;
import com.agentworkshq.chatengine.repository.ConversationRepository;
import com.agentworkshq.chatengine.repository.MessageRepository;
import com.agentworkshq.chatengine.service.LlmClient.LlmMessage;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

	private static final String DEFAULT_PROMPT = "default prompt";

	@Mock
	ConversationRepository conversations;

	@Mock
	MessageRepository messages;

	@Mock
	LlmClient llmClient;

	@Mock
	PlatformTransactionManager transactionManager;

	private final UUID id = UUID.randomUUID();

	private ChatService service(int window, int maxChars) {
		ChatProperties properties = new ChatProperties(DEFAULT_PROMPT, window, maxChars);
		return new ChatService(conversations, messages, llmClient, new PromptPlanner(properties), properties,
				transactionManager);
	}

	@BeforeEach
	void echoSavedMessages() {
		org.mockito.Mockito.lenient().when(messages.save(any(Message.class)))
				.thenAnswer(inv -> inv.getArgument(0));
	}

	private Conversation conversation(String systemPrompt, String model, Double temperature) {
		return Conversation.builder().id(id).title("t").systemPrompt(systemPrompt).model(model)
				.temperature(temperature).build();
	}

	private Message msg(MessageRole role, String content) {
		return Message.builder().role(role).content(content).build();
	}

	@Test
	void sendsDefaultPromptAndNoOverridesWhenConversationHasNone() {
		when(conversations.findById(id)).thenReturn(Optional.of(conversation(null, null, null)));
		when(messages.findByConversationIdOrderByCreatedAtDesc(eq(id), any(Pageable.class)))
				.thenReturn(List.of(msg(MessageRole.USER, "hi")));
		when(llmClient.complete(any(), any(), any(), any())).thenReturn("hello");

		MessageResponse reply = service(20, 8000).sendMessage(id, "hi");

		verify(llmClient).complete(List.of(new LlmMessage(MessageRole.USER, "hi")), DEFAULT_PROMPT, null, null);
		assertThat(reply.role()).isEqualTo(MessageRole.ASSISTANT);
		assertThat(reply.content()).isEqualTo("hello");
	}

	@Test
	void conversationOverridesAreForwardedToTheClient() {
		when(conversations.findById(id)).thenReturn(Optional.of(conversation("Be a pirate", "some-model", 1.3)));
		when(messages.findByConversationIdOrderByCreatedAtDesc(eq(id), any(Pageable.class)))
				.thenReturn(List.of(msg(MessageRole.USER, "hi")));
		when(llmClient.complete(any(), any(), any(), any())).thenReturn("arr");

		service(20, 8000).sendMessage(id, "hi");

		verify(llmClient).complete(any(), eq("Be a pirate"), eq("some-model"), eq(1.3));
	}

	@Test
	void fetchesOnlyTheConfiguredWindowAndSendsItChronologically() {
		when(conversations.findById(id)).thenReturn(Optional.of(conversation(null, null, null)));
		// Repository returns newest first.
		when(messages.findByConversationIdOrderByCreatedAtDesc(eq(id), any(Pageable.class)))
				.thenReturn(List.of(msg(MessageRole.USER, "third"), msg(MessageRole.ASSISTANT, "second"),
						msg(MessageRole.USER, "first")));
		when(llmClient.complete(any(), any(), any(), any())).thenReturn("ok");

		service(3, 8000).sendMessage(id, "third");

		ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
		verify(messages).findByConversationIdOrderByCreatedAtDesc(eq(id), page.capture());
		assertThat(page.getValue().getPageSize()).isEqualTo(3);
		assertThat(page.getValue().getPageNumber()).isZero();

		verify(llmClient).complete(
				List.of(new LlmMessage(MessageRole.USER, "first"), new LlmMessage(MessageRole.ASSISTANT, "second"),
						new LlmMessage(MessageRole.USER, "third")),
				DEFAULT_PROMPT, null, null);
	}

	@Test
	void rejectsContentOverTheLimitBeforeTouchingTheDatabaseOrTheModel() {
		assertThatThrownBy(() -> service(20, 10).sendMessage(id, "x".repeat(11)))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessageContaining("10");

		verifyNoInteractions(conversations, llmClient);
		verify(messages, never()).save(any());
	}

	@Test
	void acceptsContentExactlyAtTheLimit() {
		when(conversations.findById(id)).thenReturn(Optional.of(conversation(null, null, null)));
		when(messages.findByConversationIdOrderByCreatedAtDesc(eq(id), any(Pageable.class)))
				.thenReturn(List.of(msg(MessageRole.USER, "x".repeat(10))));
		when(llmClient.complete(any(), any(), any(), any())).thenReturn("ok");

		assertThat(service(20, 10).sendMessage(id, "x".repeat(10)).content()).isEqualTo("ok");
	}

	@Test
	void unknownConversationIsNotFoundAndNeverCallsTheModel() {
		when(conversations.findById(id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service(20, 8000).sendMessage(id, "hi")).isInstanceOf(NotFoundException.class);

		verifyNoInteractions(llmClient);
	}

}
