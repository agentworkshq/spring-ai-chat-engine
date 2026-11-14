package com.agentworkshq.chatengine.config;

/**
 * Example payloads shown in Swagger UI. Kept as constants because annotation values must be
 * compile-time constants. They mirror the real response shapes.
 */
public final class OpenApiExamples {

	private OpenApiExamples() {
	}

	public static final String CONVERSATION_ID = "3f6c1b2e-8a4d-4c7e-9b1a-2d5e7f9a0c11";

	// ---- requests ----

	public static final String CREATE_REQUEST = """
			{
			  "title": "Trip planning",
			  "systemPrompt": "You are a concise travel assistant.",
			  "model": "openai/gpt-oss-120b",
			  "temperature": 0.4
			}""";

	public static final String CREATE_REQUEST_MINIMAL = """
			{
			  "title": "Quick question"
			}""";

	public static final String UPDATE_REQUEST = """
			{
			  "title": "Lisbon trip",
			  "temperature": 0.2
			}""";

	public static final String UPDATE_REQUEST_CLEAR = """
			{
			  "systemPrompt": "",
			  "model": ""
			}""";

	public static final String SEND_REQUEST = """
			{
			  "content": "What are three things to do in Lisbon?"
			}""";

	// ---- conversation responses ----

	public static final String CONVERSATION_SUMMARY = """
			{
			  "id": "3f6c1b2e-8a4d-4c7e-9b1a-2d5e7f9a0c11",
			  "title": "Trip planning",
			  "model": "openai/gpt-oss-120b",
			  "temperature": 0.4,
			  "createdAt": "2026-11-14T10:29:00.226087Z",
			  "updatedAt": "2026-11-14T10:29:00.226087Z"
			}""";

	public static final String CONVERSATION_SUMMARY_DEFAULTS = """
			{
			  "id": "9a1d4e60-52b7-4f0c-8c33-7e2f5b6d1a48",
			  "title": "Quick question",
			  "model": null,
			  "temperature": null,
			  "createdAt": "2026-11-14T10:31:12.480113Z",
			  "updatedAt": "2026-11-14T10:31:12.480113Z"
			}""";

	public static final String CONVERSATION_LIST = """
			[
			  {
			    "id": "9a1d4e60-52b7-4f0c-8c33-7e2f5b6d1a48",
			    "title": "Quick question",
			    "model": null,
			    "temperature": null,
			    "createdAt": "2026-11-14T10:31:12.480113Z",
			    "updatedAt": "2026-11-14T10:31:12.480113Z"
			  },
			  {
			    "id": "3f6c1b2e-8a4d-4c7e-9b1a-2d5e7f9a0c11",
			    "title": "Trip planning",
			    "model": "openai/gpt-oss-120b",
			    "temperature": 0.4,
			    "createdAt": "2026-11-14T10:29:00.226087Z",
			    "updatedAt": "2026-11-14T10:29:00.226087Z"
			  }
			]""";

	public static final String CONVERSATION_DETAIL = """
			{
			  "id": "3f6c1b2e-8a4d-4c7e-9b1a-2d5e7f9a0c11",
			  "title": "Trip planning",
			  "systemPrompt": "You are a concise travel assistant.",
			  "model": "openai/gpt-oss-120b",
			  "temperature": 0.4,
			  "createdAt": "2026-11-14T10:29:00.226087Z",
			  "updatedAt": "2026-11-14T10:29:00.226087Z",
			  "messages": [
			    {
			      "id": "d247df9b-76c5-481e-98cc-a0bff3118cbd",
			      "role": "USER",
			      "content": "What are three things to do in Lisbon?",
			      "createdAt": "2026-11-14T10:29:02.391939Z"
			    },
			    {
			      "id": "e8b7f50f-7a69-48dc-adde-d41202b0792a",
			      "role": "ASSISTANT",
			      "content": "Ride tram 28, eat a pastel de nata in Belem, and watch the sunset from Miradouro da Graca.",
			      "createdAt": "2026-11-14T10:29:04.512771Z"
			    }
			  ]
			}""";

	// ---- messages ----

	public static final String MESSAGE_RESPONSE = """
			{
			  "id": "e8b7f50f-7a69-48dc-adde-d41202b0792a",
			  "role": "ASSISTANT",
			  "content": "Ride tram 28, eat a pastel de nata in Belem, and watch the sunset from Miradouro da Graca.",
			  "createdAt": "2026-11-14T10:29:04.512771Z"
			}""";

	public static final String STREAM_SUCCESS = """
			event:token
			data:{"content":"Ride tram 28, "}

			event:token
			data:{"content":"eat a pastel de nata"}

			event:done
			data:{"id":"e8b7f50f-7a69-48dc-adde-d41202b0792a","role":"ASSISTANT","content":"Ride tram 28, eat a pastel de nata","createdAt":"2026-11-14T10:29:04.512771Z"}
			""";

	public static final String STREAM_ERROR = """
			event:token
			data:{"content":"Ride tram 28, "}

			event:error
			data:{"message":"The language model is temporarily unavailable"}
			""";

	// ---- errors (all share the same five-field body) ----

	public static final String ERROR_VALIDATION = """
			{
			  "timestamp": "2026-11-14T10:29:03.283345Z",
			  "status": 400,
			  "error": "Bad Request",
			  "message": "Validation failed: title: must not be blank",
			  "path": "/api/v1/conversations"
			}""";

	public static final String ERROR_MESSAGE_TOO_LONG = """
			{
			  "timestamp": "2026-11-14T10:29:03.283345Z",
			  "status": 400,
			  "error": "Bad Request",
			  "message": "content must be at most 8000 characters",
			  "path": "/api/v1/conversations/3f6c1b2e-8a4d-4c7e-9b1a-2d5e7f9a0c11/messages"
			}""";

	public static final String ERROR_BAD_ID = """
			{
			  "timestamp": "2026-11-14T10:29:03.591421Z",
			  "status": 400,
			  "error": "Bad Request",
			  "message": "Invalid value for parameter id",
			  "path": "/api/v1/conversations/not-a-uuid"
			}""";

	public static final String ERROR_NOT_FOUND = """
			{
			  "timestamp": "2026-11-14T10:29:03.536837Z",
			  "status": 404,
			  "error": "Not Found",
			  "message": "Conversation 3f6c1b2e-8a4d-4c7e-9b1a-2d5e7f9a0c11 not found",
			  "path": "/api/v1/conversations/3f6c1b2e-8a4d-4c7e-9b1a-2d5e7f9a0c11"
			}""";

	public static final String ERROR_RATE_LIMITED = """
			{
			  "timestamp": "2026-11-14T10:29:19.236669Z",
			  "status": 429,
			  "error": "Too Many Requests",
			  "message": "Rate limit exceeded, please retry later",
			  "path": "/api/v1/conversations/3f6c1b2e-8a4d-4c7e-9b1a-2d5e7f9a0c11/messages"
			}""";

	public static final String ERROR_LLM_RATE_LIMITED = """
			{
			  "timestamp": "2026-11-14T10:29:19.236669Z",
			  "status": 429,
			  "error": "Too Many Requests",
			  "message": "The language model is rate limited, please retry later",
			  "path": "/api/v1/conversations/3f6c1b2e-8a4d-4c7e-9b1a-2d5e7f9a0c11/messages"
			}""";

	public static final String ERROR_LLM_UNAVAILABLE = """
			{
			  "timestamp": "2026-11-14T10:29:19.236669Z",
			  "status": 503,
			  "error": "Service Unavailable",
			  "message": "The language model is temporarily unavailable",
			  "path": "/api/v1/conversations/3f6c1b2e-8a4d-4c7e-9b1a-2d5e7f9a0c11/messages"
			}""";

	public static final String ERROR_LLM_BAD_RESPONSE = """
			{
			  "timestamp": "2026-11-14T10:29:17.493340Z",
			  "status": 502,
			  "error": "Bad Gateway",
			  "message": "The language model returned an unexpected response",
			  "path": "/api/v1/conversations/3f6c1b2e-8a4d-4c7e-9b1a-2d5e7f9a0c11/messages"
			}""";

}
