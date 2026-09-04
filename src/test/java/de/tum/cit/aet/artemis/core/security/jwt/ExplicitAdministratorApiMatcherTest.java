package de.tum.cit.aet.artemis.core.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Checks the matcher against the mappings the running context registered, which is the only place the answer is real.
 *
 * <p>
 * The paths below are the ones a path-shaped heuristic got wrong. {@code /api/exam/rooms/admin/**} has an
 * {@code /admin/} segment but not directly after the module, and the passkey approval and bonus calculation endpoints
 * carry {@link de.tum.cit.aet.artemis.core.security.annotations.EnforceSuperAdmin} and
 * {@link de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin} without any {@code /admin/} segment at all. The
 * negative cases are their immediate neighbours, which a prefix wide enough to cover them would have exempted too.
 */
class ExplicitAdministratorApiMatcherTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private ObjectProvider<RequestMappingHandlerMapping> handlerMappings;

    private boolean matches(String method, String requestUri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, requestUri);
        return new ExplicitAdministratorApiMatcher(handlerMappings).matches(request);
    }

    @ParameterizedTest
    @CsvSource({ "GET, /api/admin/audits", "DELETE, /api/exam/rooms/admin/outdated-and-unused", "PUT, /api/account/passkeys/some-credential/approval",
            "GET, /api/account/passkeys/admin", "GET, /api/assessment/courses/1/exams/2/bonuses/calculate-raw" })
    void testRecognizesAnnotatedAdministratorEndpoints(String method, String requestUri) {
        assertThat(matches(method, requestUri)).as("%s %s is served by an administrator endpoint", method, requestUri).isTrue();
    }

    @ParameterizedTest
    @CsvSource({ "GET, /api/core/public/account", "GET, /api/exam/courses/1/exams/2", "GET, /api/assessment/courses/1/exams/2/bonus", "GET, /api/communication/courses/1/posts" })
    void testDoesNotRecognizeOrdinaryEndpoints(String method, String requestUri) {
        assertThat(matches(method, requestUri)).as("%s %s is not served by an administrator endpoint", method, requestUri).isFalse();
    }

    /**
     * The path alone cannot decide it: {@code /api/account/passkeys/admin} is the super-administrator passkey overview
     * for {@code GET}, while the same path on {@code PUT} and {@code DELETE} reaches the ordinary
     * {@code /api/account/passkeys/{passkeyId}} handlers a student uses on their own passkeys. Keeping the
     * administrator authority for those would suspend the passkey requirement on an endpoint no administrator
     * annotation guards.
     */
    @ParameterizedTest
    @CsvSource({ "PUT, /api/account/passkeys/admin", "DELETE, /api/account/passkeys/admin", "POST, /api/admin/audits", "GET, /api/exam/rooms/admin/outdated-and-unused" })
    void testDoesNotRecognizeOrdinaryMethodsOnAdministratorPaths(String method, String requestUri) {
        assertThat(matches(method, requestUri)).as("%s %s is not served by an administrator endpoint", method, requestUri).isFalse();
    }

    /**
     * The point of reading the registered mappings rather than the path: an administrator endpoint added later is
     * covered without anyone touching the matcher. A drop to a handful would mean the scan stopped seeing them.
     */
    @Test
    void testFindsTheAdministratorEndpointsOfTheWholeApplication() {
        assertThat(List.of("/api/admin/audits", "/api/admin/audits/1")).allMatch(requestUri -> matches("GET", requestUri));
    }
}
