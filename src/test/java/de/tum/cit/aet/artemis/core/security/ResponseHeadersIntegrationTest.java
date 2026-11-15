package de.tum.cit.aet.artemis.core.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
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
     * The policy must then not forbid framing either, while it restricts the document base.
     */
    @Test
    void shouldRestrictTheBaseUriAndKeepFramingOpenForLti() throws Exception {
        String policy = mockMvc.perform(get("/management/info")).andReturn().getResponse().getHeader("Content-Security-Policy");

        assertThat(policy).contains("base-uri 'self'").doesNotContain("frame-ancestors");
    }

    @Test
    void shouldNotServeTheSpecificationWithoutAuthenticationWhenItIsSwitchedOff() throws Exception {
        for (String path : new String[] { "/api-docs", "/api-docs.yaml", "/api-docs/agent", "/swagger-ui/index.html" }) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
    }
}
