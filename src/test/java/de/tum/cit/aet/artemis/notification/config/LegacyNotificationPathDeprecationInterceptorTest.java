package de.tum.cit.aet.artemis.notification.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import de.tum.cit.aet.artemis.notification.web.CourseNotificationResource;

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
    void shouldSetDeprecationHeadersWhenLegacyPathHitsNotificationController() throws NoSuchMethodException {
        request.setRequestURI("/api/communication/notification/42");
        HandlerMethod handler = notificationHandler();

        interceptor.preHandle(request, response, handler);

        assertThat(response.getHeader("Deprecation")).isEqualTo("true");
        assertThat(response.getHeader("Sunset")).isEqualTo(LegacyNotificationPathDeprecationInterceptor.SUNSET_DATE);
        assertThat(response.getHeader("Link")).isEqualTo("</api/notification/notification/42>; rel=\"successor-version\"");
    }

    @Test
    void shouldSetDeprecationHeadersWhenLegacyPublicSystemNotificationPathIsUsed() throws NoSuchMethodException {
        request.setRequestURI("/api/core/public/system-notifications/active");
        HandlerMethod handler = notificationHandler();

        interceptor.preHandle(request, response, handler);

        assertThat(response.getHeader("Deprecation")).isEqualTo("true");
        assertThat(response.getHeader("Sunset")).isEqualTo(LegacyNotificationPathDeprecationInterceptor.SUNSET_DATE);
        assertThat(response.getHeader("Link")).isEqualTo("</api/notification/public/system-notifications/active>; rel=\"successor-version\"");
    }

    @Test
    void shouldNotSetHeadersWhenNewPathIsUsed() throws NoSuchMethodException {
        request.setRequestURI("/api/notification/notification/42");
        HandlerMethod handler = notificationHandler();

        interceptor.preHandle(request, response, handler);

        assertThat(response.getHeader("Deprecation")).isNull();
        assertThat(response.getHeader("Sunset")).isNull();
        assertThat(response.getHeader("Link")).isNull();
    }

    @Test
    void shouldNotSetHeadersWhenHandlerIsOutsideNotificationModule() {
        request.setRequestURI("/api/communication/messages/42");
        HandlerMethod handler = mock(HandlerMethod.class);
        // Object.class lives in java.lang, which is clearly outside the notification module package.
        when(handler.getBeanType()).thenAnswer(invocation -> Object.class);

        interceptor.preHandle(request, response, handler);

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

    private HandlerMethod notificationHandler() throws NoSuchMethodException {
        Method method = CourseNotificationResource.class.getDeclaredMethods()[0];
        return new HandlerMethod(mock(CourseNotificationResource.class), method);
    }
}
