package de.tum.cit.aet.artemis.core.aspects;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class InternalTest extends AbstractSpringIntegrationIndependentTest {

    @Test
    void testInternal() throws Exception {
        var authHeaders = new HttpHeaders();
        authHeaders.add("X-Forwarded-For", "127.0.0.1");

        request.get("/api/core/internal/test", HttpStatus.OK, Void.class, authHeaders);
    }

    @Test
    void testInternalFails() throws Exception {
        var authHeaders = new HttpHeaders();
        authHeaders.add("X-Forwarded-For", "10.0.0.1");

        request.get("/api/core/internal/test", HttpStatus.FORBIDDEN, Void.class, authHeaders);
    }

}
