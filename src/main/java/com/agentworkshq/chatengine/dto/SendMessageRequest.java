package com.agentworkshq.chatengine.dto;

import jakarta.validation.constraints.NotBlank;

/** The maximum length is enforced against {@code chat.max-input-chars} in the service. */
public record SendMessageRequest(@NotBlank String content) {
}
