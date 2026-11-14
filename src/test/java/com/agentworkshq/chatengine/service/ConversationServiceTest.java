package com.agentworkshq.chatengine.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import com.agentworkshq.chatengine.dto.ConversationDetailResponse;
import com.agentworkshq.chatengine.dto.ConversationSummaryResponse;
import com.agentworkshq.chatengine.dto.CreateConversationRequest;
import com.agentworkshq.chatengine.dto.UpdateConversationRequest;
import com.agentworkshq.chatengine.entity.Conversation;
import com.agentworkshq.chatengine.entity.Message;
import com.agentworkshq.chatengine.entity.MessageRole;
import com.agentworkshq.chatengine.exception.NotFoundException;
import com.agentworkshq.chatengine.repository.ConversationRepository;
import com.agentworkshq.chatengine.repository.MessageRepository;

/** Create, list, get and delete. The partial-update rules are in {@link ConversationServiceUpdateTest}. */
@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

	@Mock
	ConversationRepository conversations;

	@Mock
	MessageRepository messages;

	private ConversationService service;
	private final UUID id = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		service = new ConversationService(conversations, messages);
	}

	private static Conversation conversation(UUID id, String title, Instant createdAt) {
		return Conversation.builder().id(id).title(title).createdAt(createdAt).updatedAt(createdAt).build();
	}

	// ---- create ----

	@Test
	void createSavesTheRequestedSettingsAndReturnsASummary() {
		when(conversations.save(any(Conversation.class))).thenAnswer(inv -> {
			Conversation c = inv.getArgument(0);
			c.setId(id);
			return c;
		});

		ConversationSummaryResponse created = service
				.create(new CreateConversationRequest("Trip", "Be brief", "some-model", 0.7));

		ArgumentCaptor<Conversation> saved = ArgumentCaptor.forClass(Conversation.class);
		verify(conversations).save(saved.capture());
		assertThat(saved.getValue().getTitle()).isEqualTo("Trip");
		assertThat(saved.getValue().getSystemPrompt()).isEqualTo("Be brief");
		assertThat(saved.getValue().getModel()).isEqualTo("some-model");
		assertThat(saved.getValue().getTemperature()).isEqualTo(0.7);
		assertThat(created.id()).isEqualTo(id);
		assertThat(created.title()).isEqualTo("Trip");
		assertThat(created.model()).isEqualTo("some-model");
		assertThat(created.temperature()).isEqualTo(0.7);
	}

	@Test
	void createTrimsTheTitleAndKeepsUnsetOverridesNull() {
		when(conversations.save(any(Conversation.class))).thenAnswer(inv -> inv.getArgument(0));

		ConversationSummaryResponse created = service
				.create(new CreateConversationRequest("   Padded title  ", null, null, null));

		assertThat(created.title()).isEqualTo("Padded title");
		assertThat(created.model()).isNull();
		assertThat(created.temperature()).isNull();
	}

	// ---- list ----

	@Test
	void listAsksForNewestFirstAndKeepsThatOrder() {
		Conversation newer = conversation(UUID.randomUUID(), "newer", Instant.parse("2026-11-14T10:00:00Z"));
		Conversation older = conversation(UUID.randomUUID(), "older", Instant.parse("2026-11-13T10:00:00Z"));
		when(conversations.findAll(any(Sort.class))).thenReturn(List.of(newer, older));

		List<ConversationSummaryResponse> result = service.list();

		ArgumentCaptor<Sort> sort = ArgumentCaptor.forClass(Sort.class);
		verify(conversations).findAll(sort.capture());
		assertThat(sort.getValue()).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
		assertThat(result).extracting(ConversationSummaryResponse::title).containsExactly("newer", "older");
	}

	@Test
	void listIsEmptyWhenThereAreNoConversations() {
		when(conversations.findAll(any(Sort.class))).thenReturn(List.of());

		assertThat(service.list()).isEmpty();
	}

	// ---- get ----

	@Test
	void getReturnsTheConversationWithItsMessagesOldestFirst() {
		Conversation c = Conversation.builder().id(id).title("t").systemPrompt("sys").model("m").temperature(0.3)
				.createdAt(Instant.now()).updatedAt(Instant.now()).build();
		Message first = Message.builder().id(UUID.randomUUID()).role(MessageRole.USER).content("hello")
				.createdAt(Instant.parse("2026-11-14T10:00:00Z")).build();
		Message second = Message.builder().id(UUID.randomUUID()).role(MessageRole.ASSISTANT).content("hi")
				.createdAt(Instant.parse("2026-11-14T10:00:01Z")).build();
		when(conversations.findById(id)).thenReturn(Optional.of(c));
		when(messages.findByConversationIdOrderByCreatedAtAsc(id)).thenReturn(List.of(first, second));

		ConversationDetailResponse detail = service.get(id);

		assertThat(detail.id()).isEqualTo(id);
		assertThat(detail.systemPrompt()).isEqualTo("sys");
		assertThat(detail.model()).isEqualTo("m");
		assertThat(detail.temperature()).isEqualTo(0.3);
		assertThat(detail.messages()).extracting("role", "content")
				.containsExactly(org.assertj.core.groups.Tuple.tuple(MessageRole.USER, "hello"),
						org.assertj.core.groups.Tuple.tuple(MessageRole.ASSISTANT, "hi"));
	}

	@Test
	void getOfAConversationWithoutMessagesHasAnEmptyList() {
		when(conversations.findById(id)).thenReturn(Optional.of(conversation(id, "t", Instant.now())));
		when(messages.findByConversationIdOrderByCreatedAtAsc(id)).thenReturn(List.of());

		assertThat(service.get(id).messages()).isEmpty();
	}

	@Test
	void getOfAnUnknownConversationIsNotFoundAndLoadsNoMessages() {
		when(conversations.findById(id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.get(id)).isInstanceOf(NotFoundException.class)
				.hasMessageContaining(id.toString());

		verifyNoInteractions(messages);
	}

	// ---- update (only the not-found path; the rest is in ConversationServiceUpdateTest) ----

	@Test
	void updateOfAnUnknownConversationIsNotFoundAndSavesNothing() {
		when(conversations.findById(id)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.update(id, new UpdateConversationRequest("x", null, null, null)))
				.isInstanceOf(NotFoundException.class);

		verify(conversations, never()).saveAndFlush(any());
	}

	// ---- delete ----

	@Test
	void deleteRemovesAnExistingConversation() {
		when(conversations.existsById(id)).thenReturn(true);

		service.delete(id);

		verify(conversations).deleteById(id);
	}

	@Test
	void deleteOfAnUnknownConversationIsNotFoundAndDeletesNothing() {
		when(conversations.existsById(id)).thenReturn(false);

		assertThatThrownBy(() -> service.delete(id)).isInstanceOf(NotFoundException.class)
				.hasMessageContaining(id.toString());

		verify(conversations, never()).deleteById(any());
	}

}
