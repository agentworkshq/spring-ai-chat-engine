package com.agentworkshq.chatengine.service;

import java.io.InterruptedIOException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;

import com.agentworkshq.chatengine.exception.LlmException;
import com.agentworkshq.chatengine.exception.LlmRateLimitedException;
import com.agentworkshq.chatengine.exception.LlmUnavailableException;
import com.openai.errors.OpenAIInvalidDataException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;

/**
 * Translates whatever the provider SDK throws into the app's own exceptions. The SDK messages
 * embed the provider response body, so they are deliberately never copied into the result.
 */
final class LlmErrorMapper {

	static final String BAD_RESPONSE_MESSAGE = "The language model returned an unexpected response";

	private static final int MAX_CAUSE_DEPTH = 10;

	private LlmErrorMapper() {
	}

	static LlmException map(Throwable error) {
		int depth = 0;
		for (Throwable t = error; t != null && depth < MAX_CAUSE_DEPTH; t = t.getCause(), depth++) {
			if (t instanceof LlmException llm) {
				return llm;
			}
			if (t instanceof OpenAIServiceException service) {
				return fromStatus(service);
			}
			if (t instanceof OpenAIInvalidDataException) {
				// A 200 whose body could not be parsed. Its cause is a parser IOException, which must
				// not be mistaken for a network failure below.
				return new LlmException(BAD_RESPONSE_MESSAGE);
			}
			if (t instanceof OpenAIIoException || isNetworkFailure(t)) {
				// Timeouts and connection failures: nothing came back from the provider.
				return new LlmUnavailableException(null, null);
			}
		}
		return new LlmException(BAD_RESPONSE_MESSAGE);
	}

	private static boolean isNetworkFailure(Throwable t) {
		return t instanceof SocketException || t instanceof UnknownHostException
				|| t instanceof InterruptedIOException; // includes SocketTimeoutException
	}

	private static LlmException fromStatus(OpenAIServiceException error) {
		int status = error.statusCode();
		Duration retryAfter = retryAfter(error);
		if (status == 429) {
			return new LlmRateLimitedException(retryAfter);
		}
		if (status == 408 || status >= 500) {
			return new LlmUnavailableException(status, retryAfter);
		}
		// Other 4xx (bad request, auth, unknown model, ...) mean our request or config is wrong,
		// which is not something the caller can fix by retrying.
		return new LlmException(BAD_RESPONSE_MESSAGE, status, null);
	}

	private static Duration retryAfter(OpenAIServiceException error) {
		List<String> values = error.headers().values("Retry-After");
		if (values.isEmpty()) {
			return null;
		}
		try {
			double seconds = Double.parseDouble(values.get(0).trim());
			return seconds >= 0 ? Duration.ofMillis((long) Math.ceil(seconds * 1000)) : null;
		}
		catch (NumberFormatException ex) {
			return null; // HTTP-date form is not supported; treat as unknown.
		}
	}

}
