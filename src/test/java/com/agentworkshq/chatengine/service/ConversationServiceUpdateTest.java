package com.agentworkshq.chatengine.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.agentworkshq.chatengine.dto.ConversationSummaryResponse;
import com.agentworkshq.chatengine.dto.UpdateConversationRequest;
import com.agentworkshq.chatengine.entity.Conversation;
import com.agentworkshq.chatengine.repository.ConversationRepository;
import com.agentworkshq.chatengine.repository.MessageRepository;

@ExtendWith(MockitoExtension.class)
class ConversationServiceUpdateTest {

	@Mock
	ConversationRepository conversations;

	@Mock
	MessageRepository messages;

	private final UUID id = UUID.randomUUID();
	private Conversation conversation;
	private ConversationService service;

	@BeforeEach
	void setUp() {
		conversation = Conversation.builder().id(id).title("old").systemPrompt("old prompt").model("old-model")
				.temperature(0.4).build();
		when(conversations.findById(id)).thenReturn(Optional.of(conversation));
		when(conversations.saveAndFlush(any(Conversation.class))).thenAnswer(inv -> inv.getArgument(0));
		service = new ConversationService(conversations, messages);
	}

	@Test
	void nullFieldsLeaveTheConversationUnchanged() {
		ConversationSummaryResponse result = service.update(id, new UpdateConversationRequest(null, null, null, null));

		assertThat(result.title()).isEqualTo("old");
		assertThat(result.model()).isEqualTo("old-model");
		assertThat(result.temperature()).isEqualTo(0.4);
		assertThat(conversation.getSystemPrompt()).isEqualTo("old prompt");
	}

	@Test
	void setsModelAndTemperatureOverrides() {
		ConversationSummaryResponse result = service.update(id,
				new UpdateConversationRequest(null, null, "new-model", 1.5));

		assertThat(result.model()).isEqualTo("new-model");
		assertThat(result.temperature()).isEqualTo(1.5);
		assertThat(result.title()).isEqualTo("old");
	}

	@Test
	void updatesTitleAndSystemPromptTogether() {
		service.update(id, new UpdateConversationRequest("  new title  ", "new prompt", null, null));

		assertThat(conversation.getTitle()).isEqualTo("new title");
		assertThat(conversation.getSystemPrompt()).isEqualTo("new prompt");
	}

	@Test
	void blankModelAndSystemPromptClearTheOverrides() {
		service.update(id, new UpdateConversationRequest(null, "  ", "", null));

		assertThat(conversation.getSystemPrompt()).isNull();
		assertThat(conversation.getModel()).isNull();
	}

}
