package com.agentworkshq.chatengine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Optional API key authentication, bound to {@code security.api-key.*}.
 *
 * @param enabled whether requests must carry a valid {@code X-API-Key} header
 * @param value the expected key; required when enabled
 */
@ConfigurationProperties(prefix = "security.api-key")
public record ApiKeyProperties(@DefaultValue("false") boolean enabled, String value) {

	public ApiKeyProperties {
		if (enabled && (value == null || value.isBlank())) {
			throw new IllegalArgumentException(
					"security.api-key.value must be set when security.api-key.enabled is true");
		}
	}

	/** Never print the key, even if this object ends up in a log line or an error message. */
	@Override
	public String toString() {
		return "ApiKeyProperties[enabled=" + enabled + ", value=" + (value == null || value.isBlank() ? "" : "****")
				+ "]";
	}

}
