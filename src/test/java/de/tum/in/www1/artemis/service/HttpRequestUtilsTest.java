package de.tum.in.www1.artemis.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

import javax.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.service.util.HttpRequestUtils;

public class HttpRequestUtilsTest {

    @Test
    public void testIPv4String() {
        HttpServletRequest request = httpRequestMockWithIp("192.0.2.235");
        final var ipAddress = HttpRequestUtils.getIpAddressFromRequest(request).get();
        assertThat(ipAddress.toFullString(), is(equalTo("192.000.002.235")));
    }

    @Test
    void testIPv6String() {
        HttpServletRequest request = httpRequestMockWithIp("2001:db8:0:0:0:8a2e:370:7334");
        final var ipAddress = HttpRequestUtils.getIpAddressFromRequest(request).get();
        assertThat(ipAddress.toFullString(), is(equalTo("2001:0db8:0000:0000:0000:8a2e:0370:7334")));
    }

    @Test
    void testIPv6ShortString() {
        HttpServletRequest request = httpRequestMockWithIp("2001:db8::8a2e:370:7334");
        final var ipAddress = HttpRequestUtils.getIpAddressFromRequest(request).get();
        assertThat(ipAddress.isIPv6(), is(true));
        assertThat(ipAddress.toFullString(), is(equalTo("2001:0db8:0000:0000:0000:8a2e:0370:7334")));
    }

    @Test
    void testIPv4Lopback() {
        HttpServletRequest request = httpRequestMockWithIp("127.0.0.1");
        final var ipAddress = HttpRequestUtils.getIpAddressFromRequest(request).get();
        assertThat(ipAddress.isIPv4(), is(true));
        assertThat(ipAddress.toFullString(), is(equalTo("127.000.000.001")));
    }

    @Test
    void testIPv6Loopback() {
        HttpServletRequest request = httpRequestMockWithIp("0:0:0:0:0:0:0:1");
        final var ipAddress = HttpRequestUtils.getIpAddressFromRequest(request).get();
        assertThat(ipAddress.isIPv6(), is(true));
        assertThat(ipAddress.toFullString(), is(equalTo("0000:0000:0000:0000:0000:0000:0000:0001")));
    }

    @Test
    void testIPv6ShortLoopback() {
        HttpServletRequest request = httpRequestMockWithIp("::1");
        final var ipAddress = HttpRequestUtils.getIpAddressFromRequest(request).get();
        assertThat(ipAddress.isIPv6(), is(true));
        assertThat(ipAddress.toFullString(), is(equalTo("0000:0000:0000:0000:0000:0000:0000:0001")));
    }

    @Test()
    void testInvalidIPv4String() {
        HttpServletRequest request = httpRequestMockWithIp("192.256.2.235");
        final var ipAddress = HttpRequestUtils.getIpAddressFromRequest(request);
        assertThat(ipAddress.isEmpty(), is(true));
    }

    @Test
    void testRandomString() {
        HttpServletRequest request = httpRequestMockWithIp("foo.example");
        final var ipAddress = HttpRequestUtils.getIpAddressFromRequest(request);
        assertThat(ipAddress.isEmpty(), is(true));
    }

    @Test
    void testIpInRemoteAddr() {
        HttpServletRequest request = httpRequestMockWithIp("REMOTE_ADDR", "224.14.7.42");
        final var ipAddress = HttpRequestUtils.getIpAddressFromRequest(request).get();
        assertThat(ipAddress.toFullString(), is(equalTo("224.014.007.042")));
    }

    private HttpServletRequest httpRequestMockWithIp(String ipAddress) {
        return httpRequestMockWithIp("X-Forwarded-For", ipAddress);
    }

    private HttpServletRequest httpRequestMockWithIp(String headerName, String ipAddress) {
        final HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader(anyString())).thenReturn(null);
        when(request.getHeader(headerName)).thenReturn(ipAddress);
        return request;
    }
}
