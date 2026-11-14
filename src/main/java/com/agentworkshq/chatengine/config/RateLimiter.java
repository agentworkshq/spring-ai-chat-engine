package com.agentworkshq.chatengine.config;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;

/**
 * Token-bucket rate limiter keyed by client. Each key gets a bucket holding
 * {@code rate-limit.requests-per-minute} tokens that refills continuously at the same rate.
 *
 * <p>Buckets live in memory, so the limit is per application instance.
 */
@Component
public class RateLimiter {

	/** Above this many tracked clients, buckets that are back to full are dropped. */
	private static final int CLEANUP_THRESHOLD = 10_000;

	/**
	 * Outcome of one attempt.
	 *
	 * @param allowed whether the request may proceed
	 * @param retryAfterSeconds when rejected, whole seconds until a token is available (at least 1)
	 */
	public record Decision(boolean allowed, long retryAfterSeconds) {
	}

	private final int limitPerMinute;
	private final TimeMeter timeMeter;
	private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

	@Autowired
	public RateLimiter(RateLimitProperties properties) {
		this(properties, TimeMeter.SYSTEM_MILLISECONDS);
	}

	RateLimiter(RateLimitProperties properties, TimeMeter timeMeter) {
		this.limitPerMinute = properties.requestsPerMinute();
		this.timeMeter = timeMeter;
	}

	public Decision tryAcquire(String clientKey) {
		if (buckets.size() > CLEANUP_THRESHOLD) {
			// A full bucket carries no state: dropping it and recreating it later is equivalent.
			buckets.values().removeIf(bucket -> bucket.getAvailableTokens() >= limitPerMinute);
		}
		Bucket bucket = buckets.computeIfAbsent(clientKey, key -> newBucket());
		ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
		if (probe.isConsumed()) {
			return new Decision(true, 0);
		}
		long seconds = Math.max(1, (probe.getNanosToWaitForRefill() + 999_999_999L) / 1_000_000_000L);
		return new Decision(false, seconds);
	}

	int trackedClients() {
		return buckets.size();
	}

	private Bucket newBucket() {
		return Bucket.builder()
				.addLimit(limit -> limit.capacity(limitPerMinute).refillGreedy(limitPerMinute, Duration.ofMinutes(1)))
				.withCustomTimePrecision(timeMeter)
				.build();
	}

}
