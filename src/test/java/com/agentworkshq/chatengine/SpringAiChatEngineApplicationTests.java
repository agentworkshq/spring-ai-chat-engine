package com.agentworkshq.chatengine;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
// Dummy key and local base URL: the context test must never depend on, or reach, the real Groq.
@SpringBootTest(properties = { "spring.ai.openai.api-key=test-key-not-real",
		"spring.ai.openai.base-url=http://localhost:1/v1" })
class SpringAiChatEngineApplicationTests {

	@Test
	void contextLoads() {
	}

}
