package de.tum.cit.aet.artemis.core.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * The response headers and the public paths of the application, checked through the complete filter chain.
 */
class ResponseHeadersIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * The test application enables the LTI module, whose configuration removes {@code X-Frame-Options} so that learning management systems can embed the client.
     * The policy must then not forbid framing either, while it restricts the document base. Other tests of the shared application context may have stored LTI platforms,
     * so the policy may list their origins, but it never forbids framing.
     */
    @Test
    void shouldRestrictTheBaseUriAndKeepFramingOpenForLti() throws Exception {
        String policy = mockMvc.perform(get("/management/info")).andReturn().getResponse().getHeader("Content-Security-Policy");

        assertThat(policy).contains("base-uri 'self'").doesNotContain("frame-ancestors 'none'");
    }

    @Test
    void shouldNotServeTheSpecificationWithoutAuthenticationWhenItIsSwitchedOff() throws Exception {
        for (String path : new String[] { "/api-docs", "/api-docs.yaml", "/api-docs/agent", "/swagger-ui/index.html" }) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void shouldServeMonacoWorkerAssetsWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/vs/editor/authorization-test-worker.js")).andExpect(status().isOk())
                .andExpect(content().string("self.postMessage('monaco-worker-public-resource');\n"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "/vs-private/authorization-test-worker.js", "/api/core/account" })
    void shouldKeepOtherPathsProtected(String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
    }
}
