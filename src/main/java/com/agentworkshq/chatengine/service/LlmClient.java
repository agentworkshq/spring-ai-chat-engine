package com.agentworkshq.chatengine.service;

import java.util.List;

import com.agentworkshq.chatengine.entity.MessageRole;

/**
 * Abstraction over the chat model provider so the rest of the app (and tests) never depend on
 * a concrete vendor.
 */
public interface LlmClient {

	/**
	 * Sends the conversation to the model and returns the reply text.
	 *
	 * @param messages conversation history in chronological order
	 * @param systemPrompt optional system prompt, may be null
	 * @param model optional model override, null means the configured default
	 * @param temperature optional temperature override, null means the configured default
	 * @throws com.agentworkshq.chatengine.exception.LlmException if the provider call fails
	 */
	String complete(List<LlmMessage> messages, String systemPrompt, String model, Double temperature);

	record LlmMessage(MessageRole role, String content) {
	}

}
