package com.agentworkshq.chatengine.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

	private final RequestIdFilter filter = new RequestIdFilter();

	@AfterEach
	void clearMdc() {
		MDC.clear();
	}

	/** Runs the filter and returns the id that was visible in the MDC while the chain ran. */
	private String run(MockHttpServletRequest request, MockHttpServletResponse response) throws Exception {
		AtomicReference<String> seen = new AtomicReference<>();
		MockFilterChain chain = new MockFilterChain() {
			@Override
			public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res)
					throws java.io.IOException, jakarta.servlet.ServletException {
				seen.set(MDC.get(RequestIdFilter.MDC_KEY));
				super.doFilter(req, res);
			}
		};
		filter.doFilter(request, response, chain);
		return seen.get();
	}

	@Test
	void generatesAnIdWhenTheRequestHasNone() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();

		String seen = run(new MockHttpServletRequest(), response);

		String header = response.getHeader(RequestIdFilter.HEADER);
		assertThat(header).isNotBlank();
		assertThat(UUID.fromString(header)).isNotNull();
		assertThat(seen).isEqualTo(header);
	}

	@Test
	void reusesAValidIncomingId() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader(RequestIdFilter.HEADER, "client-req_123.A");
		MockHttpServletResponse response = new MockHttpServletResponse();

		String seen = run(request, response);

		assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo("client-req_123.A");
		assertThat(seen).isEqualTo("client-req_123.A");
	}

	@Test
	void replacesUnsafeOrOversizedIdsSoLogsCannotBeInjected() throws Exception {
		for (String bad : new String[] { "abc\nFAKE LOG LINE", "has space", "x".repeat(65), "" }) {
			MockHttpServletRequest request = new MockHttpServletRequest();
			request.addHeader(RequestIdFilter.HEADER, bad);
			MockHttpServletResponse response = new MockHttpServletResponse();

			run(request, response);

			String header = response.getHeader(RequestIdFilter.HEADER);
			assertThat(header).isNotEqualTo(bad);
			assertThat(UUID.fromString(header)).isNotNull();
		}
	}

	@Test
	void clearsTheMdcAfterTheRequest() throws Exception {
		run(new MockHttpServletRequest(), new MockHttpServletResponse());

		assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
	}

	@Test
	void clearsTheMdcEvenWhenTheChainThrows() {
		MockFilterChain failing = new MockFilterChain() {
			@Override
			public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
				throw new IllegalStateException("boom");
			}
		};

		try {
			filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), failing);
		}
		catch (Exception expected) {
			// the failure itself is not under test
		}

		assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
	}

}
