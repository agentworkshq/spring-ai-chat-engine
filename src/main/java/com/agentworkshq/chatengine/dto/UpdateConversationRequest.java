package com.agentworkshq.chatengine.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateConversationRequest(@NotBlank @Size(max = 200) String title) {
}
