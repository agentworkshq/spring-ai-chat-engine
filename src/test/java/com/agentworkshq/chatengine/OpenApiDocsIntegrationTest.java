package com.agentworkshq.chatengine;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Boots the whole application (API key auth on) and checks the OpenAPI document springdoc really
 * generates, plus the Swagger UI entry points.
 */
@SpringBootTest(properties = {
		"spring.ai.openai.api-key=test-key-not-real",
		"spring.ai.openai.base-url=http://localhost:1/v1",
		"security.api-key.enabled=true",
		"security.api-key.value=docs-test-key" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OpenApiDocsIntegrationTest {

	private static final String CONVERSATIONS = "/api/v1/conversations";
	private static final String BY_ID = "/api/v1/conversations/{id}";
	private static final String MESSAGES = "/api/v1/conversations/{id}/messages";
	private static final String STREAM = "/api/v1/conversations/{id}/messages/stream";

	@Autowired
	MockMvc mvc;

	@Test
	void docsAreOpenWithoutAnApiKeyWhileTheApiStaysProtected() throws Exception {
		mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
		mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk());
		mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
		mvc.perform(get(CONVERSATIONS)).andExpect(status().isUnauthorized());
	}

	@Test
	void swaggerUiIsServedAtSwaggerUiHtml() throws Exception {
		mvc.perform(get("/swagger-ui.html")).andExpect(status().isFound())
				.andExpect(header().string("Location", containsString("/swagger-ui/index.html")));
	}

	@Test
	void infoHasTitleDescriptionAndTheRealProjectVersion() throws Exception {
		mvc.perform(get("/v3/api-docs"))
				.andExpect(jsonPath("$.info.title").value("Spring AI Chat Engine API"))
				.andExpect(jsonPath("$.info.description").isNotEmpty())
				.andExpect(jsonPath("$.info.version").isNotEmpty())
				// An unfiltered placeholder would mean Maven resource filtering stopped working.
				.andExpect(jsonPath("$.info.version").value(not(containsString("@"))));
	}

	@Test
	void everyEndpointIsDocumentedWithASummary() throws Exception {
		mvc.perform(get("/v3/api-docs"))
				.andExpect(jsonPath("$.paths['" + CONVERSATIONS + "'].post.summary").isNotEmpty())
				.andExpect(jsonPath("$.paths['" + CONVERSATIONS + "'].get.summary").isNotEmpty())
				.andExpect(jsonPath("$.paths['" + BY_ID + "'].get.summary").isNotEmpty())
				.andExpect(jsonPath("$.paths['" + BY_ID + "'].patch.summary").isNotEmpty())
				.andExpect(jsonPath("$.paths['" + BY_ID + "'].delete.summary").isNotEmpty())
				.andExpect(jsonPath("$.paths['" + MESSAGES + "'].post.summary").isNotEmpty())
				.andExpect(jsonPath("$.paths['" + STREAM + "'].post.summary").isNotEmpty())
				.andExpect(jsonPath("$.paths.length()").value(4));
	}

	@Test
	void responseCodesAndExamplesAreDocumented() throws Exception {
		mvc.perform(get("/v3/api-docs"))
				.andExpect(jsonPath("$.paths['" + CONVERSATIONS + "'].post.responses['201']").exists())
				.andExpect(jsonPath("$.paths['" + CONVERSATIONS + "'].post.responses['400']").exists())
				.andExpect(jsonPath("$.paths['" + CONVERSATIONS + "'].post.requestBody.content['application/json'].examples")
						.isNotEmpty())
				.andExpect(jsonPath("$.paths['" + BY_ID + "'].get.responses['200']").exists())
				.andExpect(jsonPath("$.paths['" + BY_ID + "'].get.responses['404']").exists())
				.andExpect(jsonPath("$.paths['" + BY_ID + "'].delete.responses['204']").exists())
				.andExpect(jsonPath("$.paths['" + MESSAGES + "'].post.responses['201']").exists())
				.andExpect(jsonPath("$.paths['" + MESSAGES + "'].post.responses['429'].headers['Retry-After']").exists())
				.andExpect(jsonPath("$.paths['" + MESSAGES + "'].post.responses['502']").exists())
				.andExpect(jsonPath("$.paths['" + MESSAGES + "'].post.responses['503'].headers['Retry-After']").exists())
				.andExpect(jsonPath("$.paths['" + STREAM + "'].post.responses['200'].content['text/event-stream']")
						.exists())
				.andExpect(jsonPath("$.paths['" + STREAM + "'].post.responses['200'].content['application/json']")
						.doesNotExist());
	}

	@Test
	void everyOperationAlsoDocuments401And500AndTheRequestIdHeader() throws Exception {
		for (String path : new String[] { CONVERSATIONS, BY_ID, MESSAGES, STREAM }) {
			String method = path.equals(CONVERSATIONS) ? "get" : path.equals(BY_ID) ? "get" : "post";
			mvc.perform(get("/v3/api-docs"))
					.andExpect(jsonPath("$.paths['" + path + "']." + method + ".responses['401']").exists())
					.andExpect(jsonPath("$.paths['" + path + "']." + method + ".responses['500']").exists())
					.andExpect(jsonPath("$.paths['" + path + "']." + method
							+ ".responses['500'].headers['X-Request-Id']").exists());
		}
	}

	@Test
	void apiKeySecuritySchemeIsDocumentedAndApplied() throws Exception {
		mvc.perform(get("/v3/api-docs"))
				.andExpect(jsonPath("$.components.securitySchemes.ApiKeyAuth.type").value("apiKey"))
				.andExpect(jsonPath("$.components.securitySchemes.ApiKeyAuth.in").value("header"))
				.andExpect(jsonPath("$.components.securitySchemes.ApiKeyAuth.name").value("X-API-Key"))
				.andExpect(jsonPath("$.security[0].ApiKeyAuth").exists());
	}

	@Test
	void errorSchemaListsTheFiveStandardFields() throws Exception {
		mvc.perform(get("/v3/api-docs"))
				.andExpect(jsonPath("$.components.schemas.ApiError.properties.timestamp").exists())
				.andExpect(jsonPath("$.components.schemas.ApiError.properties.status").exists())
				.andExpect(jsonPath("$.components.schemas.ApiError.properties.error").exists())
				.andExpect(jsonPath("$.components.schemas.ApiError.properties.message").exists())
				.andExpect(jsonPath("$.components.schemas.ApiError.properties.path").exists())
				.andExpect(jsonPath("$.components.schemas.ApiError.properties.length()").value(5));
	}

}
