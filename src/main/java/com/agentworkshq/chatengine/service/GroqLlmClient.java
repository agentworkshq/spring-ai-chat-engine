package com.agentworkshq.chatengine.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

import com.agentworkshq.chatengine.exception.LlmException;

import reactor.core.publisher.Flux;

/**
 * {@link LlmClient} backed by Groq through the OpenAI-compatible Spring AI starter. Timeouts and
 * retries of transient failures are configured on the underlying client (see
 * {@link com.agentworkshq.chatengine.config.LlmClientConfig}); whatever still fails is translated
 * by {@link LlmErrorMapper}. Failures are not logged here: the caller logs them once, with request
 * context, using the safe fields on {@link LlmException}.
 */
@Component
public class GroqLlmClient implements LlmClient {

	private final ChatClient chatClient;

	public GroqLlmClient(ChatClient.Builder chatClientBuilder) {
		this.chatClient = chatClientBuilder.build();
	}

	@Override
	public String complete(List<LlmMessage> messages, String systemPrompt, String model, Double temperature) {
		String reply;
		try {
			reply = chatClient.prompt()
					.messages(toPrompt(messages, systemPrompt))
					.options(toOptions(model, temperature))
					.call()
					.content();
		}
		catch (RuntimeException ex) {
			throw LlmErrorMapper.map(ex);
		}
		if (reply == null || reply.isBlank()) {
			throw new LlmException("The language model returned an empty reply");
		}
		return reply;
	}

	@Override
	public Flux<String> stream(List<LlmMessage> messages, String systemPrompt, String model, Double temperature) {
		return Flux.defer(() -> chatClient.prompt()
				.messages(toPrompt(messages, systemPrompt))
				.options(toOptions(model, temperature))
				.stream()
				.content())
				.onErrorMap(LlmErrorMapper::map);
	}

	private List<Message> toPrompt(List<LlmMessage> messages, String systemPrompt) {
		List<Message> prompt = new ArrayList<>();
		if (systemPrompt != null && !systemPrompt.isBlank()) {
			prompt.add(new SystemMessage(systemPrompt));
		}
		for (LlmMessage m : messages) {
			prompt.add(switch (m.role()) {
				case USER -> new UserMessage(m.content());
				case ASSISTANT -> new AssistantMessage(m.content());
				case SYSTEM -> new SystemMessage(m.content());
			});
		}
		return prompt;
	}

	/** Only override what the conversation specifies; everything else keeps the configured defaults. */
	private OpenAiChatOptions.Builder toOptions(String model, Double temperature) {
		OpenAiChatOptions.Builder options = OpenAiChatOptions.builder();
		if (model != null && !model.isBlank()) {
			options.model(model);
		}
		if (temperature != null) {
			options.temperature(temperature);
		}
		return options;
	}

}
