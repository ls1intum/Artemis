package de.tum.cit.aet.artemis.core.service.featureusage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
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

    /** Mirrors the attribute the interceptor stashes the start time under, which is private to it. */
    private static final String START_NANOS_ATTRIBUTE = FeatureUsageInterceptor.class.getName() + ".startNanos";

    private FeatureUsageRegistry registry;

    private FeatureUsageCollector collector;

    private FeatureUsageInterceptor interceptor;

    private HandlerMethod handlerMethod;

    @BeforeEach
    void init() throws NoSuchMethodException {
        registry = mock(FeatureUsageRegistry.class);
        collector = newCollector(enabledProperties());
        interceptor = interceptor(enabledProperties(), collector);
        handlerMethod = new HandlerMethod(new DummyResource(), DummyResource.class.getDeclaredMethod("handle"));
        when(registry.restFeatureId(any(Method.class))).thenReturn(FEATURE_ID);
    }

    /**
     * The stated invariant of the whole write path: a usage counter must not be able to break the request it is measuring.
     * The collector guards its own accumulation, but the two beans are resolved from the context on the first request, and
     * that happens outside it.
     */
    @Test
    void shouldNotLetAFailingBeanResolutionBreakTheRequest() {
        var applicationContext = mock(ApplicationContext.class);
        when(applicationContext.getBean(FeatureUsageRegistry.class)).thenThrow(new IllegalStateException("context not usable"));
        var brokenInterceptor = new FeatureUsageInterceptor(enabledProperties(), applicationContext);
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        brokenInterceptor.preHandle(request, response, handlerMethod);

        assertThatCode(() -> brokenInterceptor.afterCompletion(request, response, handlerMethod, null)).doesNotThrowAnyException();
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

    /**
     * Spring calls {@code preHandle} once more on the ASYNC dispatch of an asynchronous request. Taking the second call's
     * values would report only that dispatch as the duration and re-read the role from a security context the async
     * dispatch does not necessarily carry, which is the opposite of what reading them in {@code preHandle} is for.
     */
    @Test
    void shouldKeepTheRoleAndTheClockOfTheFirstDispatch() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("instructor", "credentials", List.of(new SimpleGrantedAuthority(Role.INSTRUCTOR.getAuthority()))));
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, handlerMethod);
        Object startOfTheFirstDispatch = request.getAttribute(START_NANOS_ATTRIBUTE);
        // the async dispatch runs on a pooled thread that carries no authentication
        SecurityContextHolder.clearContext();
        interceptor.preHandle(request, response, handlerMethod);
        interceptor.afterCompletion(request, response, handlerMethod, null);

        assertThat(request.getAttribute(START_NANOS_ATTRIBUTE)).isEqualTo(startOfTheFirstDispatch);
        assertThat(collector.drain(LocalDate.now(ZoneOffset.UTC)).getFirst().callerRole()).isEqualTo(Role.INSTRUCTOR);
    }

    /**
     * The other half of "recording never propagates a failure". {@code preHandle} runs synchronously on the request
     * thread and before the handler, so a throw here would fail the request outright rather than after its work was
     * already done.
     */
    @Test
    void shouldNotLetAFailingMeasurementStartBreakTheRequest() {
        var request = mock(HttpServletRequest.class);
        when(request.getAttribute(START_NANOS_ATTRIBUTE)).thenReturn(null);
        doThrow(new IllegalStateException("no attributes on this request")).when(request).setAttribute(anyString(), any());

        assertThatCode(() -> interceptor.preHandle(request, new MockHttpServletResponse(), handlerMethod)).doesNotThrowAnyException();
        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), handlerMethod)).isTrue();
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
        var disabledProperties = new FeatureUsageProperties(false, 400, new FeatureUsageProperties.Digest(false, List.of()));
        var disabledCollector = newCollector(disabledProperties);
        var disabledInterceptor = interceptor(disabledProperties, disabledCollector);
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        assertThat(disabledInterceptor.preHandle(request, response, handlerMethod)).isTrue();
        disabledInterceptor.afterCompletion(request, response, handlerMethod, null);

        assertThat(disabledCollector.drain(LocalDate.now(ZoneOffset.UTC))).isEmpty();
        verifyNoInteractions(registry);
    }

    /**
     * The interceptor resolves the registry and the collector from the context on first use, so that constructing it does not
     * put the repository behind them on the startup dependency path.
     */
    private FeatureUsageInterceptor interceptor(FeatureUsageProperties properties, FeatureUsageCollector usageCollector) {
        var applicationContext = mock(ApplicationContext.class);
        when(applicationContext.getBean(FeatureUsageRegistry.class)).thenReturn(registry);
        when(applicationContext.getBean(FeatureUsageCollector.class)).thenReturn(usageCollector);
        return new FeatureUsageInterceptor(properties, applicationContext);
    }

    private static FeatureUsageProperties enabledProperties() {
        return new FeatureUsageProperties(true, 400, new FeatureUsageProperties.Digest(false, List.of()));
    }

    static class DummyResource {

        public void handle() {
            // only its signature matters
        }
    }

    /**
     * The collector resolves the registry from the context on first use, so constructing it here has to supply a context that
     * hands back the mocked registry.
     */
    private FeatureUsageCollector newCollector(FeatureUsageProperties properties) {
        var applicationContext = mock(ApplicationContext.class);
        when(applicationContext.getBean(FeatureUsageRegistry.class)).thenReturn(registry);
        // applied inline, so the assertions do not have to wait for the recording thread
        return new FeatureUsageCollector(properties, applicationContext, Runnable::run);
    }

}
