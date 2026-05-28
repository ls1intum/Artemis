package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.method.HandlerMethod;

class LegacyApiPathDeprecationInterceptorTest {

    private LegacyApiPathDeprecationInterceptor interceptor;

    private MockHttpServletRequest request;

    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        interceptor = new LegacyApiPathDeprecationInterceptor();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    void shouldSetDeprecationHeadersWhenRequestArrivesOnLegacyAlias() {
        request.setRequestURI("/api/core/users/42");

        interceptor.preHandle(request, response, handlerOf(AccountControllerStub.class));

        assertThat(response.getHeader("Deprecation")).isEqualTo(LegacyApiPathDeprecationInterceptor.DEPRECATION_DATE);
        assertThat(response.getHeader("Sunset")).isEqualTo(LegacyApiPathDeprecationInterceptor.SUNSET_DATE);
        assertThat(response.getHeaders("Link")).containsExactly("</api/account/users/42>; rel=\"successor-version\"");
    }

    @Test
    void shouldNotSetHeadersWhenRequestArrivesOnCanonicalPrefix() {
        request.setRequestURI("/api/account/users/42");

        interceptor.preHandle(request, response, handlerOf(AccountControllerStub.class));

        assertThat(response.getHeader("Deprecation")).isNull();
        assertThat(response.getHeader("Sunset")).isNull();
        assertThat(response.getHeader("Link")).isNull();
    }

    @Test
    void shouldNotClobberExistingLinkHeaderEmittedByController() {
        String paginationLink = "</api/core/users?page=1&size=50>; rel=\"next\"";
        response.addHeader("Link", paginationLink);
        request.setRequestURI("/api/core/users");

        interceptor.preHandle(request, response, handlerOf(AccountControllerStub.class));

        // Per RFC 8288, Link is multi-valued. Both fields must remain.
        assertThat(response.getHeaders("Link")).containsExactlyInAnyOrder(paginationLink, "</api/account/users>; rel=\"successor-version\"");
    }

    @Test
    void shouldStripServletContextPathBeforeMatchingLegacyPrefixAndPrependItToTheSuccessorLink() {
        // Under a non-root deployment (server.servlet.context-path=/artemis), HttpServletRequest#getRequestURI()
        // returns the context-prefixed path. The interceptor must strip it before comparing legacy prefixes —
        // and prepend it again when building the successor URI so the Link still points inside the deployment.
        request.setContextPath("/artemis");
        request.setRequestURI("/artemis/api/core/users/42");

        interceptor.preHandle(request, response, handlerOf(AccountControllerStub.class));

        assertThat(response.getHeader("Deprecation")).isEqualTo(LegacyApiPathDeprecationInterceptor.DEPRECATION_DATE);
        assertThat(response.getHeaders("Link")).containsExactly("</artemis/api/account/users/42>; rel=\"successor-version\"");
    }

    @Test
    void shouldBeNoOpForSinglePathController() {
        request.setRequestURI("/api/exam/exams/1");

        interceptor.preHandle(request, response, handlerOf(SinglePathControllerStub.class));

        assertThat(response.getHeader("Deprecation")).isNull();
        assertThat(response.getHeader("Link")).isNull();
    }

    @Test
    void shouldBeNoOpForControllerWithoutRequestMappingAnnotation() {
        request.setRequestURI("/api/core/anything");

        interceptor.preHandle(request, response, handlerOf(NoMappingControllerStub.class));

        assertThat(response.getHeader("Deprecation")).isNull();
    }

    @Test
    void shouldBeNoOpForNonHandlerMethodHandlers() {
        request.setRequestURI("/api/core/users/42");

        interceptor.preHandle(request, response, new Object());

        assertThat(response.getHeader("Deprecation")).isNull();
    }

    @Test
    void shouldSwapEachLegacyAliasIndependentlyWhenMultipleLegacyEntriesExist() {
        request.setRequestURI("/api/legacy-b/foo");

        interceptor.preHandle(request, response, handlerOf(TwoLegacyAliasControllerStub.class));

        assertThat(response.getHeaders("Link")).containsExactly("</api/canonical/foo>; rel=\"successor-version\"");
    }

    private static HandlerMethod handlerOf(Class<?> beanType) {
        HandlerMethod handler = mock(HandlerMethod.class);
        when(handler.getBeanType()).thenAnswer(invocation -> beanType);
        return handler;
    }

    // --- Test fixture stubs ---

    @RequestMapping({ "api/account/", "api/core/" })
    private static final class AccountControllerStub {
    }

    @RequestMapping({ "api/exam/" })
    private static final class SinglePathControllerStub {
    }

    private static final class NoMappingControllerStub {
    }

    @RequestMapping({ "api/canonical/", "api/legacy-a/", "api/legacy-b/" })
    private static final class TwoLegacyAliasControllerStub {
    }
}
