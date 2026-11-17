package de.tum.cit.aet.artemis.core.aspects;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class InternalTest extends AbstractSpringIntegrationIndependentTest {

    @Test
    void testInternal() throws Exception {
        request.get("/api/core/internal/test", HttpStatus.OK, Void.class, new HttpHeaders());
    }

    @Test
    void testInternalFailsFromAnAddressOutsideTheAllowedRanges() throws Exception {
        request.performMvcRequest(get("/api/core/internal/test").with(servletRequest -> {
            servletRequest.setRemoteAddr("203.0.113.10");
            return servletRequest;
        })).andExpect(status().isForbidden());
    }

    @Test
    void testInternalIgnoresAddressHeadersOfTheRequest() throws Exception {
        request.performMvcRequest(get("/api/core/internal/test").header("X-Forwarded-For", "127.0.0.1").with(servletRequest -> {
            servletRequest.setRemoteAddr("203.0.113.10");
            return servletRequest;
        })).andExpect(status().isForbidden());
    }
}
