package de.tum.cit.aet.artemis.notification.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

class LegacyNotificationPathDeprecationInterceptorTest {

    private LegacyNotificationPathDeprecationInterceptor interceptor;

    private MockHttpServletRequest request;

    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        interceptor = new LegacyNotificationPathDeprecationInterceptor();
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @Test
    void shouldSetDeprecationHeadersWhenLegacyPathHitsNotificationController() {
        request.setRequestURI("/api/communication/notification/42");

        interceptor.preHandle(request, response, handlerInPackage(NotificationLegacyRestPaths.class));

        assertThat(response.getHeader("Deprecation")).isEqualTo(LegacyNotificationPathDeprecationInterceptor.DEPRECATION_DATE);
        assertThat(response.getHeader("Sunset")).isEqualTo(LegacyNotificationPathDeprecationInterceptor.SUNSET_DATE);
        assertThat(response.getHeaders("Link")).containsExactly("</api/notification/notification/42>; rel=\"successor-version\"");
    }

    @Test
    void shouldSetDeprecationHeadersWhenLegacyPublicSystemNotificationPathIsUsed() {
        request.setRequestURI("/api/core/public/system-notifications/active");

        interceptor.preHandle(request, response, handlerInPackage(NotificationLegacyRestPaths.class));

        assertThat(response.getHeader("Deprecation")).isEqualTo(LegacyNotificationPathDeprecationInterceptor.DEPRECATION_DATE);
        assertThat(response.getHeader("Sunset")).isEqualTo(LegacyNotificationPathDeprecationInterceptor.SUNSET_DATE);
        assertThat(response.getHeaders("Link")).containsExactly("</api/notification/public/system-notifications/active>; rel=\"successor-version\"");
    }

    @Test
    void shouldNotClobberExistingLinkHeaderWhenLegacyPathHitsNotificationController() {
        // Pre-populate a pagination Link header to model the case where, after preHandle, the controller
        // adds its own Link header (PaginationUtil pattern). The interceptor must not overwrite either side.
        String paginationLink = "</api/communication/system-notifications?page=1&size=50>; rel=\"next\"";
        response.addHeader("Link", paginationLink);
        request.setRequestURI("/api/communication/system-notifications");

        interceptor.preHandle(request, response, handlerInPackage(NotificationLegacyRestPaths.class));

        // Order is order-of-insertion-dependent and not significant per RFC 8288 — assert presence regardless of order.
        assertThat(response.getHeaders("Link")).containsExactlyInAnyOrder(paginationLink, "</api/notification/system-notifications>; rel=\"successor-version\"");
    }

    @Test
    void shouldNotSetHeadersWhenNewPathIsUsed() {
        request.setRequestURI("/api/notification/notification/42");

        interceptor.preHandle(request, response, handlerInPackage(NotificationLegacyRestPaths.class));

        assertThat(response.getHeader("Deprecation")).isNull();
        assertThat(response.getHeader("Sunset")).isNull();
        assertThat(response.getHeader("Link")).isNull();
    }

    @Test
    void shouldNotSetHeadersWhenHandlerIsOutsideNotificationModule() {
        request.setRequestURI("/api/communication/messages/42");
        // Object.class lives in java.lang, which is clearly outside the notification module package.
        interceptor.preHandle(request, response, handlerInPackage(Object.class));

        assertThat(response.getHeader("Deprecation")).isNull();
        assertThat(response.getHeader("Sunset")).isNull();
        assertThat(response.getHeader("Link")).isNull();
    }

    @Test
    void shouldNotSetHeadersWhenHandlerIsNotHandlerMethod() {
        request.setRequestURI("/api/communication/notification/42");

        interceptor.preHandle(request, response, new Object());

        assertThat(response.getHeader("Deprecation")).isNull();
    }

    /**
     * Builds a HandlerMethod stub whose {@code getBeanType()} returns the given class. Used to pin the
     * package check in the interceptor to a chosen package without importing a real {@link org.springframework.web.bind.annotation.RestController}
     * (which would trigger the project-wide {@code testNoRestControllersImported} architecture rule).
     */
    private static HandlerMethod handlerInPackage(Class<?> beanType) {
        HandlerMethod handler = mock(HandlerMethod.class);
        when(handler.getBeanType()).thenAnswer(invocation -> beanType);
        return handler;
    }
}
