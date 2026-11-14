package com.agentworkshq.chatengine.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import com.agentworkshq.chatengine.dto.StreamEvent;
import com.agentworkshq.chatengine.entity.Conversation;
import com.agentworkshq.chatengine.entity.Message;
import com.agentworkshq.chatengine.entity.MessageRole;
import com.agentworkshq.chatengine.exception.InvalidRequestException;
import com.agentworkshq.chatengine.exception.LlmException;
import com.agentworkshq.chatengine.exception.NotFoundException;
import com.agentworkshq.chatengine.repository.ConversationRepository;
import com.agentworkshq.chatengine.repository.MessageRepository;

import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class ChatServiceStreamTest {

	@Mock
	ConversationRepository conversations;

	@Mock
	MessageRepository messages;

	@Mock
	LlmClient llmClient;

	@Mock
	PlatformTransactionManager transactionManager;

	private final UUID id = UUID.randomUUID();
	private ChatService service;

	@BeforeEach
	void setUp() {
		ChatProperties properties = new ChatProperties("default prompt", 20, 8000);
		service = new ChatService(conversations, messages, llmClient, new PromptPlanner(properties), properties,
				transactionManager);
		lenient().when(messages.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));
		lenient().when(conversations.findById(id)).thenReturn(Optional.of(
				Conversation.builder().id(id).title("t").build()));
		lenient().when(messages.findByConversationIdOrderByCreatedAtDesc(eq(id), any(Pageable.class)))
				.thenReturn(List.of(Message.builder().role(MessageRole.USER).content("hi").build()));
	}

	private List<Message> savedMessages(int expectedSaves) {
		ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
		verify(messages, times(expectedSaves)).save(captor.capture());
		return captor.getAllValues();
	}

	@Test
	void streamsTokensThenSavesTheFullReplyAndEmitsDone() {
		when(llmClient.stream(any(), any(), any(), any())).thenReturn(Flux.just("Hel", "lo ", "world"));

		List<StreamEvent> events = service.streamMessage(id, "hi").collectList().block();

		assertThat(events).extracting(StreamEvent::event).containsExactly("token", "token", "token", "done");
		assertThat(events.subList(0, 3)).extracting(e -> ((StreamEvent.Token) e.data()).content())
				.containsExactly("Hel", "lo ", "world");
		MessageResponse done = (MessageResponse) events.get(3).data();
		assertThat(done.role()).isEqualTo(MessageRole.ASSISTANT);
		assertThat(done.content()).isEqualTo("Hello world");

		List<Message> saved = savedMessages(2);
		assertThat(saved).extracting(Message::getRole).containsExactly(MessageRole.USER, MessageRole.ASSISTANT);
		assertThat(saved.get(1).getContent()).isEqualTo("Hello world");
	}

	@Test
	void midStreamErrorEmitsErrorEventAndDoesNotSaveAPartialReply() {
		when(llmClient.stream(any(), any(), any(), any()))
				.thenReturn(Flux.concat(Flux.just("par", "tial"), Flux.error(new LlmException("boom"))));

		List<StreamEvent> events = service.streamMessage(id, "hi").collectList().block();

		assertThat(events).extracting(StreamEvent::event).containsExactly("token", "token", "error");
		assertThat(((StreamEvent.Error) events.get(2).data()).message()).isEqualTo("boom");
		assertThat(savedMessages(1)).extracting(Message::getRole).containsExactly(MessageRole.USER);
	}

	@Test
	void unexpectedExceptionIsHiddenBehindAGenericMessage() {
		when(llmClient.stream(any(), any(), any(), any()))
				.thenReturn(Flux.error(new IllegalStateException("secret provider detail")));

		List<StreamEvent> events = service.streamMessage(id, "hi").collectList().block();

		assertThat(events).extracting(StreamEvent::event).containsExactly("error");
		assertThat(((StreamEvent.Error) events.get(0).data()).message()).doesNotContain("secret");
		savedMessages(1);
	}

	@Test
	void clientDisconnectDiscardsThePartialReply() {
		when(llmClient.stream(any(), any(), any(), any())).thenReturn(Flux.just("one", "two", "three"));

		// take(1) cancels the subscription after the first chunk, like a dropped connection.
		List<StreamEvent> events = service.streamMessage(id, "hi").take(1).collectList().block();

		assertThat(events).extracting(StreamEvent::event).containsExactly("token");
		assertThat(savedMessages(1)).extracting(Message::getRole).containsExactly(MessageRole.USER);
	}

	@Test
	void emptyReplyIsAnErrorAndNothingIsSaved() {
		when(llmClient.stream(any(), any(), any(), any())).thenReturn(Flux.just("", "  "));

		List<StreamEvent> events = service.streamMessage(id, "hi").collectList().block();

		assertThat(events.get(events.size() - 1).event()).isEqualTo("error");
		assertThat(savedMessages(1)).extracting(Message::getRole).containsExactly(MessageRole.USER);
	}

	@Test
	void userMessageIsSavedBeforeStreamingStarts() {
		Flux<StreamEvent> flux = service.streamMessage(id, "hi");

		// Nothing subscribed yet, but the user message is already persisted and the model untouched.
		assertThat(savedMessages(1)).extracting(Message::getRole).containsExactly(MessageRole.USER);
		verifyNoInteractions(llmClient);
		assertThat(flux).isNotNull();
	}

	@Test
	void unknownConversationFailsEagerlyWithNotFound() {
		UUID other = UUID.randomUUID();
		when(conversations.findById(other)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.streamMessage(other, "hi")).isInstanceOf(NotFoundException.class);

		verify(messages, never()).save(any());
		verifyNoInteractions(llmClient);
	}

	@Test
	void overlongContentFailsEagerly() {
		assertThatThrownBy(() -> service.streamMessage(id, "x".repeat(8001)))
				.isInstanceOf(InvalidRequestException.class);

		verifyNoInteractions(llmClient);
	}

}
