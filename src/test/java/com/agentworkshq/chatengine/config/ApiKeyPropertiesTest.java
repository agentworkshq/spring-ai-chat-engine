package com.agentworkshq.chatengine.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ApiKeyPropertiesTest {

	@Test
	void enablingWithoutAKeyFailsFast() {
		assertThatThrownBy(() -> new ApiKeyProperties(true, null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new ApiKeyProperties(true, "   ")).isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("security.api-key.value");
	}

	@Test
	void disabledNeedsNoKey() {
		assertThatCode(() -> new ApiKeyProperties(false, null)).doesNotThrowAnyException();
		assertThatCode(() -> new ApiKeyProperties(false, "")).doesNotThrowAnyException();
	}

	@Test
	void toStringNeverRevealsTheKey() {
		assertThat(new ApiKeyProperties(true, "super-secret-key").toString()).doesNotContain("super-secret-key");
	}

}
