package com.agentworkshq.chatengine.config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springdoc.core.customizers.OpenApiCustomizer;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/** OpenAPI metadata and the documentation that applies to every operation. */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

	public static final String API_KEY_SCHEME = "ApiKeyAuth";

	private static final String ERROR_SCHEMA_REF = "#/components/schemas/ApiError";

	@Bean
	OpenAPI chatEngineOpenApi(ApiKeyProperties apiKey, @Value("${app.version}") String version) {
		OpenAPI openApi = new OpenAPI()
				.info(new Info()
						.title("Spring AI Chat Engine API")
						.version(version)
						.description("""
								Backend-only conversational AI API. Create conversations, send messages and get \
								replies from the language model, either as one JSON response or streamed as \
								server-sent events.

								**Conversations** hold an optional system prompt, model and temperature that override \
								the server defaults for every message in that conversation. Only the most recent \
								messages are sent to the model as history.

								**Errors** always use the same five-field JSON body: `timestamp`, `status`, `error`, \
								`message`, `path`. Messages are generic and never contain provider details. Every \
								response carries an `X-Request-Id` header (send your own, or one is generated) that \
								also appears in the server logs.

								**Limits.** The two message endpoints are rate limited per client IP and answer \
								`429` with a `Retry-After` header when the limit is hit.

								**Authentication.** API key authentication is optional and off by default. When the \
								server runs with `security.api-key.enabled=true`, send the key in the `X-API-Key` \
								header (use the *Authorize* button above). Health checks and these API docs stay open."""));

		openApi.components(new Components().addSecuritySchemes(API_KEY_SCHEME, new SecurityScheme()
				.type(SecurityScheme.Type.APIKEY)
				.in(SecurityScheme.In.HEADER)
				.name(ApiKeyAuthFilter.HEADER)
				.description("Shared API key. Only enforced when the server has security.api-key.enabled=true; "
						+ "ignored otherwise.")));

		if (apiKey.enabled()) {
			// Only mark operations as secured when the running server actually enforces the key.
			openApi.addSecurityItem(new SecurityRequirement().addList(API_KEY_SCHEME));
		}
		return openApi;
	}

	/**
	 * Adds what every operation can return regardless of its own annotations: 401 (when the key is
	 * enforced), 500, and the {@code X-Request-Id} response header.
	 */
	@Bean
	OpenApiCustomizer commonResponsesCustomizer(ApiKeyProperties apiKey) {
		return openApi -> {
			if (openApi.getPaths() == null) {
				return;
			}
			openApi.getPaths().values().forEach(pathItem -> pathItem.readOperations().forEach(operation -> {
				if (operation.getResponses() == null) {
					return;
				}
				if (apiKey.enabled()) {
					operation.getResponses().putIfAbsent("401",
							errorResponse("Missing or invalid `X-API-Key` header", 401, "Unauthorized",
									"Missing or invalid API key"));
				}
				operation.getResponses().putIfAbsent("500", errorResponse("Unexpected server error", 500,
						"Internal Server Error", "Internal server error"));
				operation.getResponses().values().forEach(response -> response.addHeaderObject("X-Request-Id",
						new Header()
								.description("Correlation id of this request: echoes the `X-Request-Id` you sent, "
										+ "or a generated one. Also appears in the server logs.")
								.schema(new StringSchema().example("7c9e6679-7425-40de-944b-e07fc1f90ae7"))));
			}));
		};
	}

	private static ApiResponse errorResponse(String description, int status, String error, String message) {
		Map<String, Object> example = new LinkedHashMap<>();
		example.put("timestamp", "2026-11-14T10:29:03.283345Z");
		example.put("status", status);
		example.put("error", error);
		example.put("message", message);
		example.put("path", "/api/v1/conversations");
		return new ApiResponse().description(description).content(new Content().addMediaType("application/json",
				new MediaType().schema(new Schema<>().$ref(ERROR_SCHEMA_REF))
						.addExamples("default", new Example().value(example))));
	}

}
