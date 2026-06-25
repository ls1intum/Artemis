package de.tum.cit.aet.artemis.admin.management;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;

import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

/**
 * Integration tests for {@link de.tum.cit.aet.artemis.admin.web.AdminConfigCheckResource}.
 *
 * <p>
 * Sets {@code artemis.config-check.token} to a known test value so all three test cases
 * (happy path, missing token, wrong token) are exercised. The parent context already sets
 * {@code artemis.iris.enabled=true} and {@code artemis.athena.enabled=true}.
 */
@TestPropertySource(properties = "artemis.config-check.token=claudia-test-token-12345")
class AdminConfigCheckResourceIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String ENDPOINT = "/api/admin/internal/config-check";

    private static final String TEST_TOKEN = "claudia-test-token-12345";

    @Test
    void returnsSubsystemStateForValidToken() throws Exception {
        var headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + TEST_TOKEN);

        @SuppressWarnings("unchecked")
        Map<String, Object> body = request.get(ENDPOINT, HttpStatus.OK, Map.class, headers);

        assertThat(body).containsKey("iris");
        assertThat(body).containsKey("atlas");
        assertThat(body).containsKey("atlasml");
        assertThat(body).containsKey("athena");

        @SuppressWarnings("unchecked")
        Map<String, Object> iris = (Map<String, Object>) body.get("iris");
        assertThat(iris.get("enabled")).isInstanceOf(Boolean.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> athena = (Map<String, Object>) body.get("athena");
        assertThat(athena.get("enabled")).isInstanceOf(Boolean.class);
    }

    @Test
    void rejectsMissingToken() throws Exception {
        request.get(ENDPOINT, HttpStatus.UNAUTHORIZED, String.class, new HttpHeaders());
    }

    @Test
    void rejectsWrongToken() throws Exception {
        var headers = new HttpHeaders();
        headers.set("Authorization", "Bearer wrong-token");
        request.get(ENDPOINT, HttpStatus.UNAUTHORIZED, String.class, headers);
    }
}
