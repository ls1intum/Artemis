package de.tum.cit.aet.artemis.core.security.annotations;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import de.tum.cit.aet.artemis.core.config.InternalAccessConfiguration;

class InternalAspectTest {

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void allowsARequestFromAnAllowedAddress() {
        InternalAspect aspect = aspectAllowing("10.0.0.0/8");
        bind(request("10.1.2.3", null));

        assertThatCode(aspect::checkAccess).doesNotThrowAnyException();
    }

    @Test
    void refusesARequestFromAnAddressOutsideTheAllowedRanges() {
        InternalAspect aspect = aspectAllowing("10.0.0.0/8");
        bind(request("203.0.113.9", null));

        assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(aspect::checkAccess);
    }

    @Test
    void ignoresAddressHeadersOfTheRequest() {
        InternalAspect aspect = aspectAllowing("10.0.0.0/8");
        bind(request("203.0.113.9", "10.1.2.3"));

        assertThatExceptionOfType(AccessDeniedException.class).isThrownBy(aspect::checkAccess);
    }

    private static InternalAspect aspectAllowing(String cidr) {
        InternalAccessConfiguration configuration = new InternalAccessConfiguration();
        configuration.setAllowedCidrs(List.of(cidr));
        return new InternalAspect(configuration);
    }

    private static MockHttpServletRequest request(String remoteAddress, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        return request;
    }

    private static void bind(MockHttpServletRequest request) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
