package com.agentworkshq.chatengine.dto;

/**
 * One server-sent event of a streamed reply.
 *
 * <ul>
 * <li>{@code token}: a chunk of the reply, data is {@code {"content": "..."}}</li>
 * <li>{@code done}: the stream finished and the full reply was saved, data is the saved message</li>
 * <li>{@code error}: the stream failed, nothing was saved, data is {@code {"message": "..."}}</li>
 * </ul>
 */
public record StreamEvent(String event, Object data) {

	public record Token(String content) {
	}

	public record Error(String message) {
	}

	public static StreamEvent token(String content) {
		return new StreamEvent("token", new Token(content));
	}

	public static StreamEvent done(MessageResponse message) {
		return new StreamEvent("done", message);
	}

	public static StreamEvent error(String message) {
		return new StreamEvent("error", new Error(message));
	}

}
