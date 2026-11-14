package com.agentworkshq.chatengine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.agentworkshq.chatengine.entity.MessageRole;
import com.agentworkshq.chatengine.exception.LlmException;
import com.agentworkshq.chatengine.exception.LlmRateLimitedException;
import com.agentworkshq.chatengine.exception.LlmUnavailableException;
import com.agentworkshq.chatengine.service.LlmClient;
import com.agentworkshq.chatengine.service.LlmClient.LlmMessage;
import com.sun.net.httpserver.HttpServer;

/**
 * Runs the real {@code GroqLlmClient} (real Spring AI client, real timeouts and retries) against a
 * local fake of the Groq API, so resilience can be verified without ever calling Groq.
 */
@SpringBootTest(properties = {
		"spring.ai.openai.api-key=test-key-not-real",
		"chat.llm.max-attempts=3",
		"chat.llm.connect-timeout=2s",
		"chat.llm.read-timeout=1s" })
@Import(TestcontainersConfiguration.class)
class LlmResilienceIntegrationTest {

	private static final String COMPLETION = """
			{"id":"c1","object":"chat.completion","created":1,"model":"fake",
			 "choices":[{"index":0,"finish_reason":"stop","message":{"role":"assistant","content":"hello from fake"}}]}""";

	private static final String STREAM = """
			data: {"id":"c1","object":"chat.completion.chunk","created":1,"model":"fake","choices":[{"index":0,"delta":{"role":"assistant","content":"Hi"},"finish_reason":null}]}

			data: {"id":"c1","object":"chat.completion.chunk","created":1,"model":"fake","choices":[{"index":0,"delta":{"content":" there"},"finish_reason":"stop"}]}

			data: [DONE]

			""";

	record Reply(int status, Map<String, String> headers, String body, long delayMillis) {

		static Reply ok() {
			return new Reply(200, Map.of("Content-Type", "application/json"), COMPLETION, 0);
		}

		static Reply sse() {
			return new Reply(200, Map.of("Content-Type", "text/event-stream"), STREAM, 0);
		}

		static Reply status(int status) {
			return new Reply(status, Map.of("Content-Type", "application/json"),
					"{\"error\":{\"message\":\"SECRET provider detail\",\"type\":\"x\"}}", 0);
		}

		static Reply status(int status, String retryAfter) {
			return new Reply(status, Map.of("Content-Type", "application/json", "Retry-After", retryAfter),
					"{\"error\":{\"message\":\"SECRET provider detail\",\"type\":\"x\"}}", 0);
		}

		static Reply slow(long millis) {
			return new Reply(200, Map.of("Content-Type", "application/json"), COMPLETION, millis);
		}

		static Reply garbage() {
			return new Reply(200, Map.of("Content-Type", "application/json"), "this is not json", 0);
		}
	}

	private static final HttpServer SERVER = startServer();
	private static final AtomicInteger REQUESTS = new AtomicInteger();
	private static volatile IntFunction<Reply> script = n -> Reply.ok();

	private static HttpServer startServer() {
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			server.setExecutor(Executors.newCachedThreadPool());
			server.createContext("/v1/chat/completions", exchange -> {
				exchange.getRequestBody().readAllBytes();
				Reply reply = script.apply(REQUESTS.incrementAndGet());
				try {
					if (reply.delayMillis() > 0) {
						Thread.sleep(reply.delayMillis());
					}
					byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
					reply.headers().forEach((k, v) -> exchange.getResponseHeaders().add(k, v));
					exchange.sendResponseHeaders(reply.status(), body.length);
					try (OutputStream out = exchange.getResponseBody()) {
						out.write(body);
					}
				}
				catch (InterruptedException | IOException ex) {
					// The client gave up (for example a read timeout); nothing to answer.
				}
				finally {
					exchange.close();
				}
			});
			server.start();
			return server;
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	@DynamicPropertySource
	static void fakeGroq(DynamicPropertyRegistry registry) {
		registry.add("spring.ai.openai.base-url", () -> "http://127.0.0.1:" + SERVER.getAddress().getPort() + "/v1");
	}

	@AfterAll
	static void stopServer() {
		SERVER.stop(0);
	}

	@Autowired
	LlmClient llm;

	private final List<LlmMessage> hi = List.of(new LlmMessage(MessageRole.USER, "hi"));

	@BeforeEach
	void reset() {
		REQUESTS.set(0);
		script = n -> Reply.ok();
	}

	@Test
	void happyPathReturnsTheReplyWithASingleRequest() {
		assertThat(llm.complete(hi, "sys", null, null)).isEqualTo("hello from fake");
		assertThat(REQUESTS.get()).isEqualTo(1);
	}

	@Test
	void transientServerErrorsAreRetriedUntilSuccess() {
		script = n -> n < 3 ? Reply.status(503) : Reply.ok();

		assertThat(llm.complete(hi, null, null, null)).isEqualTo("hello from fake");
		assertThat(REQUESTS.get()).isEqualTo(3);
	}

	@Test
	void persistentServerErrorsGiveUpAfterMaxAttemptsAsUnavailable() {
		script = n -> Reply.status(500);

		assertThatThrownBy(() -> llm.complete(hi, null, null, null))
				.isExactlyInstanceOf(LlmUnavailableException.class)
				.satisfies(e -> assertThat(((LlmException) e).getUpstreamStatus()).isEqualTo(500))
				.hasMessageNotContaining("SECRET");
		assertThat(REQUESTS.get()).isEqualTo(3);
	}

	@Test
	void rateLimitIsRetriedHonouringRetryAfterThenMappedWithIt() {
		script = n -> Reply.status(429, "1");

		long started = System.nanoTime();
		assertThatThrownBy(() -> llm.complete(hi, null, null, null))
				.isExactlyInstanceOf(LlmRateLimitedException.class)
				.satisfies(e -> assertThat(((LlmException) e).getRetryAfter()).isEqualTo(Duration.ofSeconds(1)))
				.hasMessageNotContaining("SECRET");
		long elapsedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();

		assertThat(REQUESTS.get()).isEqualTo(3);
		assertThat(elapsedMillis).as("two waits of Retry-After: 1s").isGreaterThanOrEqualTo(1800);
	}

	@Test
	void clientErrorsAreNotRetriedAndMapToBadUpstream() {
		script = n -> Reply.status(400);

		assertThatThrownBy(() -> llm.complete(hi, null, null, null))
				.isExactlyInstanceOf(LlmException.class)
				.satisfies(e -> assertThat(((LlmException) e).getUpstreamStatus()).isEqualTo(400))
				.hasMessageNotContaining("SECRET");
		assertThat(REQUESTS.get()).as("4xx other than 408/409/429 is not transient").isEqualTo(1);
	}

	@Test
	void authFailuresAreNotRetried() {
		script = n -> Reply.status(401);

		assertThatThrownBy(() -> llm.complete(hi, null, null, null)).isExactlyInstanceOf(LlmException.class);
		assertThat(REQUESTS.get()).isEqualTo(1);
	}

	@Test
	void readTimeoutIsEnforcedRetriedAndMappedAsUnavailable() {
		script = n -> Reply.slow(2500); // read timeout is 1s

		long started = System.nanoTime();
		assertThatThrownBy(() -> llm.complete(hi, null, null, null))
				.isExactlyInstanceOf(LlmUnavailableException.class)
				.satisfies(e -> assertThat(((LlmException) e).getUpstreamStatus()).isNull());
		long elapsedMillis = Duration.ofNanos(System.nanoTime() - started).toMillis();

		assertThat(REQUESTS.get()).isEqualTo(3);
		assertThat(elapsedMillis).as("bounded by the 1s read timeout, not the 2.5s server delay x3")
				.isLessThan(7000);
	}

	@Test
	void unparsableSuccessBodyIsBadUpstreamNotUnavailable() {
		script = n -> Reply.garbage();

		assertThatThrownBy(() -> llm.complete(hi, null, null, null))
				.isExactlyInstanceOf(LlmException.class)
				.hasMessageNotContaining("not json");
	}

	@Test
	void streamingWorksThroughTheFake() {
		script = n -> Reply.sse();

		List<String> chunks = llm.stream(hi, null, null, null).collectList().block();

		assertThat(String.join("", chunks)).isEqualTo("Hi there");
		assertThat(REQUESTS.get()).isEqualTo(1);
	}

	@Test
	void streamingRetriesTransientFailuresBeforeTheStreamStarts() {
		script = n -> n < 3 ? Reply.status(503) : Reply.sse();

		List<String> chunks = llm.stream(hi, null, null, null).collectList().block();

		assertThat(String.join("", chunks)).isEqualTo("Hi there");
		assertThat(REQUESTS.get()).isEqualTo(3);
	}

	@Test
	void streamingFailuresSurfaceAsMappedExceptions() {
		script = n -> Reply.status(429, "1");

		assertThatThrownBy(() -> llm.stream(hi, null, null, null).collectList().block())
				.isExactlyInstanceOf(LlmRateLimitedException.class);
	}

}
