package com.agentworkshq.chatengine.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.agentworkshq.chatengine.exception.LlmException;
import com.agentworkshq.chatengine.exception.LlmRateLimitedException;
import com.agentworkshq.chatengine.exception.LlmUnavailableException;
import com.openai.core.http.Headers;
import com.openai.errors.BadRequestException;
import com.openai.errors.InternalServerException;
import com.openai.errors.OpenAIInvalidDataException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.RateLimitException;
import com.openai.errors.UnauthorizedException;
import com.openai.errors.UnexpectedStatusCodeException;

class LlmErrorMapperTest {

	private static Headers headers(String retryAfter) {
		Headers.Builder builder = Headers.builder();
		if (retryAfter != null) {
			builder.put("Retry-After", retryAfter);
		}
		return builder.build();
	}

	@Test
	void rateLimitBecomesRateLimitedWithRetryAfter() {
		LlmException mapped = LlmErrorMapper.map(RateLimitException.builder().headers(headers("7")).build());

		assertThat(mapped).isExactlyInstanceOf(LlmRateLimitedException.class);
		assertThat(mapped.getRetryAfter()).isEqualTo(Duration.ofSeconds(7));
		assertThat(mapped.getUpstreamStatus()).isEqualTo(429);
	}

	@Test
	void fractionalRetryAfterIsKeptInMilliseconds() {
		LlmException mapped = LlmErrorMapper.map(RateLimitException.builder().headers(headers("1.5")).build());

		assertThat(mapped.getRetryAfter()).isEqualTo(Duration.ofMillis(1500));
	}

	@Test
	void rateLimitWithoutRetryAfterHasUnknownDelay() {
		LlmException mapped = LlmErrorMapper.map(RateLimitException.builder().headers(headers(null)).build());

		assertThat(mapped).isExactlyInstanceOf(LlmRateLimitedException.class);
		assertThat(mapped.getRetryAfter()).isNull();
	}

	@Test
	void unparsableRetryAfterIsTreatedAsUnknown() {
		LlmException mapped = LlmErrorMapper
				.map(RateLimitException.builder().headers(headers("Wed, 21 Oct 2026 07:28:00 GMT")).build());

		assertThat(mapped.getRetryAfter()).isNull();
	}

	@Test
	void serverErrorsBecomeUnavailable() {
		for (int status : new int[] { 500, 502, 503, 504 }) {
			LlmException mapped = LlmErrorMapper
					.map(InternalServerException.builder().statusCode(status).headers(headers("3")).build());

			assertThat(mapped).as("status %d", status).isExactlyInstanceOf(LlmUnavailableException.class);
			assertThat(mapped.getUpstreamStatus()).isEqualTo(status);
			assertThat(mapped.getRetryAfter()).isEqualTo(Duration.ofSeconds(3));
		}
	}

	@Test
	void requestTimeoutStatusBecomesUnavailable() {
		LlmException mapped = LlmErrorMapper
				.map(UnexpectedStatusCodeException.builder().statusCode(408).headers(headers(null)).build());

		assertThat(mapped).isExactlyInstanceOf(LlmUnavailableException.class);
	}

	@Test
	void clientErrorsAreBadUpstreamResponsesNotRetryableOnes() {
		LlmException badRequest = LlmErrorMapper.map(BadRequestException.builder().headers(headers(null)).build());
		LlmException unauthorized = LlmErrorMapper.map(UnauthorizedException.builder().headers(headers(null)).build());

		assertThat(badRequest).isExactlyInstanceOf(LlmException.class);
		assertThat(badRequest.getUpstreamStatus()).isEqualTo(400);
		assertThat(unauthorized).isExactlyInstanceOf(LlmException.class);
		assertThat(unauthorized.getUpstreamStatus()).isEqualTo(401);
	}

	@Test
	void ioAndTimeoutFailuresBecomeUnavailableWithoutStatus() {
		assertThat(LlmErrorMapper.map(new OpenAIIoException("Request failed", new SocketTimeoutException("read"))))
				.isExactlyInstanceOf(LlmUnavailableException.class)
				.satisfies(e -> assertThat(e.getUpstreamStatus()).isNull());
		assertThat(LlmErrorMapper.map(new RuntimeException(new ConnectException("refused"))))
				.isExactlyInstanceOf(LlmUnavailableException.class);
	}

	@Test
	void unparsableResponseBodyIsBadUpstreamNotUnavailable() {
		// The SDK wraps the parser IOException; it must not be mistaken for a network failure.
		LlmException mapped = LlmErrorMapper
				.map(new OpenAIInvalidDataException("Error reading response", new java.io.IOException("bad json")));

		assertThat(mapped).isExactlyInstanceOf(LlmException.class);
		assertThat(mapped.getUpstreamStatus()).isNull();
	}

	@Test
	void findsTheProviderErrorInsideWrapperExceptions() {
		LlmException mapped = LlmErrorMapper
				.map(new IllegalStateException("wrapped", RateLimitException.builder().headers(headers("2")).build()));

		assertThat(mapped).isExactlyInstanceOf(LlmRateLimitedException.class);
	}

	@Test
	void alreadyMappedExceptionsPassThroughUnchanged() {
		LlmUnavailableException original = new LlmUnavailableException(503, null);

		assertThat(LlmErrorMapper.map(original)).isSameAs(original);
	}

	@Test
	void unknownFailuresBecomeGenericBadUpstream() {
		LlmException mapped = LlmErrorMapper.map(new IllegalArgumentException("who knows"));

		assertThat(mapped).isExactlyInstanceOf(LlmException.class);
	}

	@Test
	void mappedMessagesNeverContainTheOriginalErrorText() {
		String secret = "sk-live-SECRET-KEY raw provider body {\"error\":\"leaked\"}";
		Throwable[] inputs = {
				new OpenAIIoException(secret, new SocketTimeoutException(secret)),
				new OpenAIInvalidDataException(secret, new java.io.IOException(secret)),
				new RuntimeException(secret),
				new SocketTimeoutException(secret) };

		for (Throwable input : inputs) {
			assertThat(LlmErrorMapper.map(input).getMessage()).doesNotContain("SECRET").doesNotContain("leaked");
		}
	}

}
