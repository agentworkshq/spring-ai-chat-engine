package com.agentworkshq.chatengine.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.agentworkshq.chatengine.config.ChatProperties;
import com.agentworkshq.chatengine.entity.Message;
import com.agentworkshq.chatengine.entity.MessageRole;
import com.agentworkshq.chatengine.service.LlmClient.LlmMessage;

class PromptPlannerTest {

	private static final String DEFAULT_PROMPT = "default prompt";

	private PromptPlanner planner(int window) {
		return new PromptPlanner(new ChatProperties(DEFAULT_PROMPT, window, 8000));
	}

	/** Messages m1..mN, returned newest first like the repository does. */
	private List<Message> newestFirst(int count) {
		List<Message> list = new ArrayList<>();
		for (int i = count; i >= 1; i--) {
			list.add(Message.builder()
					.role(i % 2 == 1 ? MessageRole.USER : MessageRole.ASSISTANT)
					.content("m" + i)
					.build());
		}
		return list;
	}

	@Test
	void usesDefaultSystemPromptWhenConversationHasNone() {
		assertThat(planner(20).systemPrompt(null)).isEqualTo(DEFAULT_PROMPT);
	}

	@Test
	void usesDefaultSystemPromptWhenConversationPromptIsBlank() {
		assertThat(planner(20).systemPrompt("   ")).isEqualTo(DEFAULT_PROMPT);
	}

	@Test
	void conversationSystemPromptOverridesDefault() {
		assertThat(planner(20).systemPrompt("Be a pirate")).isEqualTo("Be a pirate");
	}

	@Test
	void keepsOnlyTheLastNMessagesInChronologicalOrder() {
		List<LlmMessage> history = planner(3).history(newestFirst(5));

		assertThat(history).extracting(LlmMessage::content).containsExactly("m3", "m4", "m5");
	}

	@Test
	void keepsEverythingWhenHistoryIsShorterThanTheWindow() {
		List<LlmMessage> history = planner(20).history(newestFirst(4));

		assertThat(history).extracting(LlmMessage::content).containsExactly("m1", "m2", "m3", "m4");
	}

	@Test
	void windowOfOneKeepsOnlyTheNewestMessage() {
		List<LlmMessage> history = planner(1).history(newestFirst(5));

		assertThat(history).extracting(LlmMessage::content).containsExactly("m5");
	}

	@Test
	void preservesRoles() {
		List<LlmMessage> history = planner(2).history(newestFirst(4));

		assertThat(history).extracting(LlmMessage::role)
				.containsExactly(MessageRole.USER, MessageRole.ASSISTANT);
	}

	@Test
	void emptyHistoryYieldsEmptyWindow() {
		assertThat(planner(5).history(List.of())).isEmpty();
	}

}
