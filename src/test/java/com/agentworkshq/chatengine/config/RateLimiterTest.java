package com.agentworkshq.chatengine.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import io.github.bucket4j.TimeMeter;

class RateLimiterTest {

	/** A clock the test moves by hand, so refill behaviour needs no sleeping. */
	private static final class ManualClock implements TimeMeter {

		private final AtomicLong nanos = new AtomicLong();

		void advance(Duration duration) {
			nanos.addAndGet(duration.toNanos());
		}

		@Override
		public long currentTimeNanos() {
			return nanos.get();
		}

		@Override
		public boolean isWallClockBased() {
			return false;
		}
	}

	private final ManualClock clock = new ManualClock();

	private RateLimiter limiter(int perMinute) {
		return new RateLimiter(new RateLimitProperties(true, perMinute), clock);
	}

	@Test
	void allowsUpToTheLimitThenRejects() {
		RateLimiter limiter = limiter(3);

		assertThat(limiter.tryAcquire("1.1.1.1").allowed()).isTrue();
		assertThat(limiter.tryAcquire("1.1.1.1").allowed()).isTrue();
		assertThat(limiter.tryAcquire("1.1.1.1").allowed()).isTrue();
		assertThat(limiter.tryAcquire("1.1.1.1").allowed()).isFalse();
	}

	@Test
	void rejectionSaysHowLongToWait() {
		RateLimiter limiter = limiter(60); // one token per second
		for (int i = 0; i < 60; i++) {
			limiter.tryAcquire("a");
		}

		RateLimiter.Decision rejected = limiter.tryAcquire("a");

		assertThat(rejected.allowed()).isFalse();
		assertThat(rejected.retryAfterSeconds()).isEqualTo(1);
	}

	@Test
	void retryAfterNeverBelowOneSecondAndRoundsUp() {
		RateLimiter limiter = limiter(2); // one token every 30s
		limiter.tryAcquire("a");
		limiter.tryAcquire("a");

		assertThat(limiter.tryAcquire("a").retryAfterSeconds()).isEqualTo(30);

		clock.advance(Duration.ofMillis(29_500));
		assertThat(limiter.tryAcquire("a").retryAfterSeconds()).isEqualTo(1);
	}

	@Test
	void clientsAreLimitedIndependently() {
		RateLimiter limiter = limiter(1);

		assertThat(limiter.tryAcquire("1.1.1.1").allowed()).isTrue();
		assertThat(limiter.tryAcquire("1.1.1.1").allowed()).isFalse();
		assertThat(limiter.tryAcquire("2.2.2.2").allowed()).isTrue();
	}

	@Test
	void tokensRefillOverTime() {
		RateLimiter limiter = limiter(2); // one token every 30s
		limiter.tryAcquire("a");
		limiter.tryAcquire("a");
		assertThat(limiter.tryAcquire("a").allowed()).isFalse();

		clock.advance(Duration.ofSeconds(30));
		assertThat(limiter.tryAcquire("a").allowed()).isTrue();
		assertThat(limiter.tryAcquire("a").allowed()).isFalse();

		clock.advance(Duration.ofMinutes(5));
		assertThat(limiter.tryAcquire("a").allowed()).isTrue();
		assertThat(limiter.tryAcquire("a").allowed()).isTrue();
		assertThat(limiter.tryAcquire("a").allowed()).isFalse();
	}

	@Test
	void idleClientsAreForgottenOnceTheTableGrowsLarge() {
		RateLimiter limiter = limiter(5);
		for (int i = 0; i <= 10_000; i++) {
			limiter.tryAcquire("ip-" + i);
		}
		assertThat(limiter.trackedClients()).isGreaterThan(10_000);

		clock.advance(Duration.ofMinutes(2)); // every bucket is full again
		limiter.tryAcquire("someone-new");

		assertThat(limiter.trackedClients()).isLessThan(10);
	}

}
