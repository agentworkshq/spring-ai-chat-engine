package com.agentworkshq.chatengine.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Component;

import com.agentworkshq.chatengine.config.ChatProperties;
import com.agentworkshq.chatengine.entity.Message;
import com.agentworkshq.chatengine.service.LlmClient.LlmMessage;

/** Pure prompt-shaping rules: which system prompt applies and which history is sent. */
@Component
public class PromptPlanner {

	private final ChatProperties properties;

	public PromptPlanner(ChatProperties properties) {
		this.properties = properties;
	}

	/** The conversation own prompt wins; a missing or blank one falls back to the default. */
	public String systemPrompt(String conversationSystemPrompt) {
		if (conversationSystemPrompt != null && !conversationSystemPrompt.isBlank()) {
			return conversationSystemPrompt;
		}
		return properties.systemPrompt();
	}

	public int historyWindow() {
		return properties.historyWindow();
	}

	/**
	 * Turns the most recent messages (newest first, as fetched) into a chronological history that
	 * holds at most {@code chat.history-window} entries.
	 */
	public List<LlmMessage> history(List<Message> newestFirst) {
		int size = Math.min(newestFirst.size(), properties.historyWindow());
		List<LlmMessage> window = new ArrayList<>(size);
		for (Message m : newestFirst.subList(0, size)) {
			window.add(new LlmMessage(m.getRole(), m.getContent()));
		}
		Collections.reverse(window);
		return window;
	}

}
