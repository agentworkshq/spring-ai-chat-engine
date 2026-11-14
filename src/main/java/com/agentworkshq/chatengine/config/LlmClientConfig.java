package com.agentworkshq.chatengine.config;

import java.util.concurrent.TimeUnit;

import org.springframework.ai.model.openai.autoconfigure.AbstractOpenAiProperties;
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Wires {@link LlmProperties} into the Spring AI OpenAI client (used here for Groq).
 *
 * <p>The client is built on the openai-java SDK, which already retries transient failures (408,
 * 409, 429, 5xx, I/O errors) with exponential backoff and honours Retry-After, so retries are
 * configured rather than re-implemented.
 */
@Configuration(proxyBeanMethods = false)
public class LlmClientConfig {

	/**
	 * Enforces separate connect and read timeouts on every call. Setting them on the client builder
	 * is not enough: the SDK applies its own per-request timeout ({@code spring.ai.openai.timeout},
	 * one value for every phase, 10 minutes by default) on top of it. An OkHttp application
	 * interceptor runs after that and gets the final say. The read timeout is the maximum silence
	 * between two chunks, so long streamed replies are not cut off while they keep flowing.
	 */
	@Bean
	OpenAiHttpClientBuilderCustomizer llmTimeoutsCustomizer(LlmProperties properties) {
		int connectMillis = Math.toIntExact(properties.connectTimeout().toMillis());
		int readMillis = Math.toIntExact(properties.readTimeout().toMillis());
		return builder -> builder.interceptor(chain -> chain
				.withConnectTimeout(connectMillis, TimeUnit.MILLISECONDS)
				.withReadTimeout(readMillis, TimeUnit.MILLISECONDS)
				.withWriteTimeout(readMillis, TimeUnit.MILLISECONDS)
				.proceed(chain.request()));
	}

	/**
	 * Translates {@code chat.llm.max-attempts} (total attempts) into the SDK setting (retries after
	 * the first attempt) on the Spring AI properties before the chat model is built.
	 */
	@Bean
	static BeanPostProcessor llmMaxRetriesPostProcessor(Environment environment) {
		return new BeanPostProcessor() {
			@Override
			public Object postProcessBeforeInitialization(Object bean, String beanName) {
				if (bean instanceof AbstractOpenAiProperties properties) {
					int attempts = environment.getProperty(LlmProperties.MAX_ATTEMPTS_PROPERTY, Integer.class,
							LlmProperties.DEFAULT_MAX_ATTEMPTS);
					properties.setMaxRetries(Math.max(attempts, 1) - 1);
				}
				return bean;
			}
		};
	}

}
