package de.tum.cit.aet.artemis.core.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
class AdministratorEndpointMatcherTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private ObjectProvider<RequestMappingHandlerMapping> handlerMappings;

    private boolean matches(String requestUri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(requestUri);
        return new AdministratorEndpointMatcher(handlerMappings).matches(request);
    }

    @ParameterizedTest
    @ValueSource(strings = { "/api/admin/audits", "/api/exam/rooms/admin/outdated-and-unused", "/api/account/passkeys/some-credential/approval", "/api/account/passkeys/admin",
            "/api/assessment/courses/1/exams/2/bonuses/calculate-raw" })
    void testRecognizesAnnotatedAdministratorEndpoints(String requestUri) {
        assertThat(matches(requestUri)).as("%s is served by an administrator endpoint", requestUri).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "/api/core/public/account", "/api/exam/courses/1/exams/2", "/api/assessment/courses/1/exams/2/bonus", "/api/communication/courses/1/posts" })
    void testDoesNotRecognizeOrdinaryEndpoints(String requestUri) {
        assertThat(matches(requestUri)).as("%s is not served by an administrator endpoint", requestUri).isFalse();
    }

    /**
     * The point of reading the registered mappings rather than the path: an administrator endpoint added later is
     * covered without anyone touching the matcher. A drop to a handful would mean the scan stopped seeing them.
     */
    @Test
    void testFindsTheAdministratorEndpointsOfTheWholeApplication() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/admin/audits");
        var matcher = new AdministratorEndpointMatcher(handlerMappings);

        assertThat(matcher.matches(request)).isTrue();
        assertThat(List.of("/api/admin/audits", "/api/admin/audits/1")).allMatch(this::matches);
    }
}
