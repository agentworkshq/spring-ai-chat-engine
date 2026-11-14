package com.agentworkshq.chatengine.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springdoc.core.customizers.OpenApiCustomizer;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;

/** Covers the documentation when API key auth is off, which the full-context test (auth on) does not. */
class OpenApiConfigTest {

	private final OpenApiConfig config = new OpenApiConfig();

	private static OpenAPI withOneOperation() {
		Operation operation = new Operation().responses(new ApiResponses().addApiResponse("200",
				new ApiResponse().description("ok")));
		return new OpenAPI().paths(new Paths().addPathItem("/x", new PathItem().get(operation)));
	}

	@Test
	void schemeIsAlwaysDeclaredSoSwaggerUiOffersAuthorize() {
		OpenAPI off = config.chatEngineOpenApi(new ApiKeyProperties(false, null), "1.2.3");

		assertThat(off.getComponents().getSecuritySchemes()).containsKey("ApiKeyAuth");
		assertThat(off.getComponents().getSecuritySchemes().get("ApiKeyAuth").getName()).isEqualTo("X-API-Key");
	}

	@Test
	void operationsAreOnlyMarkedSecuredWhenTheServerEnforcesTheKey() {
		assertThat(config.chatEngineOpenApi(new ApiKeyProperties(false, null), "1").getSecurity()).isNullOrEmpty();
		assertThat(config.chatEngineOpenApi(new ApiKeyProperties(true, "k"), "1").getSecurity()).hasSize(1);
	}

	@Test
	void infoCarriesTitleDescriptionAndVersion() {
		OpenAPI api = config.chatEngineOpenApi(new ApiKeyProperties(false, null), "1.2.3");

		assertThat(api.getInfo().getTitle()).isEqualTo("Spring AI Chat Engine API");
		assertThat(api.getInfo().getVersion()).isEqualTo("1.2.3");
		assertThat(api.getInfo().getDescription()).contains("X-API-Key").contains("Retry-After");
	}

	@Test
	void noAuthMeansNo401IsDocumentedButThe500AndRequestIdAre() {
		OpenAPI api = withOneOperation();
		OpenApiCustomizer customizer = config.commonResponsesCustomizer(new ApiKeyProperties(false, null));

		customizer.customise(api);

		ApiResponses responses = api.getPaths().get("/x").getGet().getResponses();
		assertThat(responses).containsKeys("200", "500").doesNotContainKey("401");
		assertThat(responses.get("200").getHeaders()).containsKey("X-Request-Id");
	}

	@Test
	void authEnabledDocuments401() {
		OpenAPI api = withOneOperation();

		config.commonResponsesCustomizer(new ApiKeyProperties(true, "k")).customise(api);

		assertThat(api.getPaths().get("/x").getGet().getResponses()).containsKey("401");
	}

	@Test
	void customizerNeverOverwritesAResponseTheEndpointDocumentedItself() {
		OpenAPI api = withOneOperation();
		api.getPaths().get("/x").getGet().getResponses().addApiResponse("500", new ApiResponse().description("custom"));

		config.commonResponsesCustomizer(new ApiKeyProperties(false, null)).customise(api);

		assertThat(api.getPaths().get("/x").getGet().getResponses().get("500").getDescription()).isEqualTo("custom");
	}

}
