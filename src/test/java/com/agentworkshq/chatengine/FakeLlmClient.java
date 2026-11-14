package com.agentworkshq.chatengine;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import com.agentworkshq.chatengine.entity.MessageRole;
import com.agentworkshq.chatengine.service.LlmClient;

import reactor.core.publisher.Flux;

/**
 * A scriptable stand-in for the real provider client, used so that no test ever reaches Groq. It
 * records every call and can reply from a function or fail on demand.
 */
class FakeLlmClient implements LlmClient {

	/** What the service asked the model: exactly the arguments of {@code complete} / {@code stream}. */
	record Call(List<LlmMessage> history, String systemPrompt, String model, Double temperature) {

		String lastUserText() {
			for (int i = history.size() - 1; i >= 0; i--) {
				if (history.get(i).role() == MessageRole.USER) {
					return history.get(i).content();
				}
			}
			return "";
		}
	}

	private static final int STREAM_CHUNK_SIZE = 4;

	private final List<Call> calls = new CopyOnWriteArrayList<>();
	private volatile Function<Call, String> reply = call -> "reply to: " + call.lastUserText();
	private volatile RuntimeException failure;

	@Override
	public String complete(List<LlmMessage> messages, String systemPrompt, String model, Double temperature) {
		Call call = record(messages, systemPrompt, model, temperature);
		if (failure != null) {
			throw failure;
		}
		return reply.apply(call);
	}

	@Override
	public Flux<String> stream(List<LlmMessage> messages, String systemPrompt, String model, Double temperature) {
		return Flux.defer(() -> {
			Call call = record(messages, systemPrompt, model, temperature);
			if (failure != null) {
				return Flux.error(failure);
			}
			String text = reply.apply(call);
			List<String> chunks = new ArrayList<>();
			for (int i = 0; i < text.length(); i += STREAM_CHUNK_SIZE) {
				chunks.add(text.substring(i, Math.min(text.length(), i + STREAM_CHUNK_SIZE)));
			}
			return Flux.fromIterable(chunks);
		});
	}

	private Call record(List<LlmMessage> messages, String systemPrompt, String model, Double temperature) {
		Call call = new Call(List.copyOf(messages), systemPrompt, model, temperature);
		calls.add(call);
		return call;
	}

	List<Call> calls() {
		return calls;
	}

	Call lastCall() {
		return calls.get(calls.size() - 1);
	}

	void replyWith(Function<Call, String> reply) {
		this.reply = reply;
	}

	void failWith(RuntimeException failure) {
		this.failure = failure;
	}

	void reset() {
		calls.clear();
		failure = null;
		reply = call -> "reply to: " + call.lastUserText();
	}

}
