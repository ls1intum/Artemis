package de.tum.cit.aet.artemis.core.service.featureusage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;

import de.tum.cit.aet.artemis.core.config.FeatureUsageProperties;
import de.tum.cit.aet.artemis.core.security.Role;

/**
 * Tests what the request path records.
 * <p>
 * The role and the clock are read in {@code preHandle} on purpose, so an asynchronous dispatch that finishes on a
 * different thread still reports the right role and the full duration.
 */
class FeatureUsageInterceptorTest {

    private static final long FEATURE_ID = 3L;

    private FeatureUsageRegistry registry;

    private FeatureUsageCollector collector;

    private FeatureUsageInterceptor interceptor;

    private HandlerMethod handlerMethod;

    @BeforeEach
    void init() throws NoSuchMethodException {
        registry = mock(FeatureUsageRegistry.class);
        collector = new FeatureUsageCollector(registry, new FeatureUsageProperties(true, 400, new FeatureUsageProperties.Digest(false, List.of())));
        interceptor = new FeatureUsageInterceptor(registry, collector);
        handlerMethod = new HandlerMethod(new DummyResource(), DummyResource.class.getDeclaredMethod("handle"));
        when(registry.restFeatureId(any(Method.class))).thenReturn(FEATURE_ID);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldRecordASuccessfulRequest() {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, handlerMethod);
        interceptor.afterCompletion(request, response, handlerMethod, null);

        var deltas = collector.drain(LocalDate.now(ZoneOffset.UTC));
        assertThat(deltas).hasSize(1);
        assertThat(deltas.getFirst().featureId()).isEqualTo(FEATURE_ID);
        assertThat(deltas.getFirst().callCount()).isEqualTo(1);
        assertThat(deltas.getFirst().errorCount()).isZero();
    }

    @Test
    void shouldCountAnErrorResponseAsAFailure() {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        response.setStatus(HttpStatus.FORBIDDEN.value());

        interceptor.preHandle(request, response, handlerMethod);
        interceptor.afterCompletion(request, response, handlerMethod, null);

        assertThat(collector.drain(LocalDate.now(ZoneOffset.UTC)).getFirst().errorCount()).isEqualTo(1);
    }

    @Test
    void shouldCountAnExceptionAsAFailureEvenWhenTheStatusLooksFine() {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, handlerMethod);
        interceptor.afterCompletion(request, response, handlerMethod, new IllegalStateException("boom"));

        assertThat(collector.drain(LocalDate.now(ZoneOffset.UTC)).getFirst().errorCount()).isEqualTo(1);
    }

    @Test
    void shouldRecordTheHighestGlobalRoleOfTheCaller() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("instructor", "credentials",
                List.of(new SimpleGrantedAuthority(Role.STUDENT.getAuthority()), new SimpleGrantedAuthority(Role.INSTRUCTOR.getAuthority()))));
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, handlerMethod);
        interceptor.afterCompletion(request, response, handlerMethod, null);

        assertThat(collector.drain(LocalDate.now(ZoneOffset.UTC)).getFirst().callerRole()).isEqualTo(Role.INSTRUCTOR);
    }

    @Test
    void shouldRecordAnUnauthenticatedRequestAsAnonymous() {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, handlerMethod);
        interceptor.afterCompletion(request, response, handlerMethod, null);

        assertThat(collector.drain(LocalDate.now(ZoneOffset.UTC)).getFirst().callerRole()).isEqualTo(Role.ANONYMOUS);
    }

    @Test
    void shouldIgnoreARequestThatDidNotResolveToAHandlerMethod() {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        Object staticResourceHandler = new Object();

        interceptor.preHandle(request, response, staticResourceHandler);
        interceptor.afterCompletion(request, response, staticResourceHandler, null);

        assertThat(collector.drain(LocalDate.now(ZoneOffset.UTC))).isEmpty();
    }

    @Test
    void shouldIgnoreAHandlerThatIsNotPartOfTheInventory() {
        when(registry.restFeatureId(any(Method.class))).thenReturn(null);
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, handlerMethod);
        interceptor.afterCompletion(request, response, handlerMethod, null);

        assertThat(collector.drain(LocalDate.now(ZoneOffset.UTC))).isEmpty();
    }

    @Test
    void shouldIgnoreARequestWhoseStartWasNeverRecorded() {
        // afterCompletion without a preceding preHandle, which is what happens when tracking is switched on mid flight
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        interceptor.afterCompletion(request, response, handlerMethod, null);

        assertThat(collector.drain(LocalDate.now(ZoneOffset.UTC))).isEmpty();
    }

    @Test
    void shouldDoNothingWhenTrackingIsDisabled() {
        var disabledCollector = new FeatureUsageCollector(registry, new FeatureUsageProperties(false, 400, new FeatureUsageProperties.Digest(false, List.of())));
        var disabledInterceptor = new FeatureUsageInterceptor(registry, disabledCollector);
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        assertThat(disabledInterceptor.preHandle(request, response, handlerMethod)).isTrue();
        disabledInterceptor.afterCompletion(request, response, handlerMethod, null);

        assertThat(disabledCollector.drain(LocalDate.now(ZoneOffset.UTC))).isEmpty();
        verifyNoInteractions(registry);
    }

    static class DummyResource {

        public void handle() {
            // only its signature matters
        }
    }
}
