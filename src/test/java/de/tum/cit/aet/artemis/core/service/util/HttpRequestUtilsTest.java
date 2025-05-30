package de.tum.cit.aet.artemis.core.service.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.util.ClientEnvironment;
import de.tum.cit.aet.artemis.core.util.HttpRequestUtils;

class HttpRequestUtilsTest {

    @Nested
    class IpAddressTests {

        @Test
        void testIPv4String() {
            HttpServletRequest request = httpRequestMockWithIp("192.0.2.235");
            final var ipAddress = HttpRequestUtils.getIpAddressFromRequest(request).orElseThrow();
            assertThat(ipAddress.toFullString()).isEqualTo("192.000.002.235");
        }

        @Test
        void testIPv6String() {
            HttpServletRequest request = httpRequestMockWithIp("2001:db8:0:0:0:8a2e:370:7334");
            final var ipAddress = HttpRequestUtils.getIpAddressFromRequest(request).orElseThrow();
            assertThat(ipAddress.toFullString()).isEqualTo("2001:0db8:0000:0000:0000:8a2e:0370:7334");
        }

        @Test
        void testIPv6ShortString() {
            HttpServletRequest request = httpRequestMockWithIp("2001:db8::8a2e:370:7334");
            final var ipAddress = HttpRequestUtils.getIpAddressFromRequest(request).orElseThrow();
            assertThat(ipAddress.isIPv6()).isTrue();
            assertThat(ipAddress.toFullString()).isEqualTo("2001:0db8:0000:0000:0000:8a2e:0370:7334");
        }

        @Test
        void testIPv4Loopback() {
            HttpServletRequest request = httpRequestMockWithIp("127.0.0.1");
            final var ipAddress = HttpRequestUtils.getIpAddressFromRequest(request).orElseThrow();
            assertThat(ipAddress.isIPv4()).isTrue();
            assertThat(ipAddress.toFullString()).isEqualTo("127.000.000.001");
        }

        @Test
        void testIPv6Loopback() {
            HttpServletRequest request = httpRequestMockWithIp("0:0:0:0:0:0:0:1");
            final var ipAddress = HttpRequestUtils.getIpAddressFromRequest(request).orElseThrow();
            assertThat(ipAddress.isIPv6()).isTrue();
            assertThat(ipAddress.toFullString()).isEqualTo("0000:0000:0000:0000:0000:0000:0000:0001");
        }

        @Test
        void testIPv6ShortLoopback() {
            HttpServletRequest request = httpRequestMockWithIp("::1");
            final var ipAddress = HttpRequestUtils.getIpAddressFromRequest(request).orElseThrow();
            assertThat(ipAddress.isIPv6()).isTrue();
            assertThat(ipAddress.toFullString()).isEqualTo("0000:0000:0000:0000:0000:0000:0000:0001");
        }

        @Test
        void testInvalidIPv4String() {
            HttpServletRequest request = httpRequestMockWithIp("192.256.2.235");
            final var ipAddress = HttpRequestUtils.getIpAddressFromRequest(request);
            assertThat(ipAddress).isEmpty();
        }

        @Test
        void testRandomString() {
            HttpServletRequest request = httpRequestMockWithIp("foo.example");
            final var ipAddress = HttpRequestUtils.getIpAddressFromRequest(request);
            assertThat(ipAddress).isEmpty();
        }

        @Test
        void testIpInRemoteAddr() {
            HttpServletRequest request = httpRequestMockWithIp("REMOTE_ADDR", "224.14.7.42");
            final var ipAddress = HttpRequestUtils.getIpAddressFromRequest(request).orElseThrow();
            assertThat(ipAddress.toFullString()).isEqualTo("224.014.007.042");
        }

    }

    @Nested
    class GetClientEnvironmentTests {

        @Test
        void shouldDetectBrowser_whenUsingChrome() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("Sec-Ch-Ua")).thenReturn("\"Chromium\";v=\"136\", \"Google Chrome\";v=\"136\", \"Not.A/Brand\";v=\"99\"");
            ClientEnvironment clientEnvironment = HttpRequestUtils.getClientEnvironment(request);
            assertThat(clientEnvironment.browser().getDisplayName()).isEqualTo("Google Chrome");
        }

        @Test
        void shouldDetectBrowser_whenUsingSafari() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("User-Agent"))
                    .thenReturn("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.5 Safari/605.1.15");
            ClientEnvironment clientEnvironment = HttpRequestUtils.getClientEnvironment(request);
            assertThat(clientEnvironment.browser().getDisplayName()).isEqualTo("Apple Safari");
        }

        @Test
        void shouldDetectBrowser_whenUsingMicrosoftEdge() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("Sec-Ch-Ua")).thenReturn("\"Microsoft Edge\";v=\"137\", \"Chromium\";v=\"137\", \"Not/A)Brand\";v=\"24\"");
            ClientEnvironment clientEnvironment = HttpRequestUtils.getClientEnvironment(request);
            assertThat(clientEnvironment.browser().getDisplayName()).isEqualTo("Microsoft Edge");
        }

        @Test
        void shouldDetectBrowser_whenUsingFirefox() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("User-Agent")).thenReturn("Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:137.0) Gecko/20100101 Firefox/137.0");
            ClientEnvironment clientEnvironment = HttpRequestUtils.getClientEnvironment(request);
            assertThat(clientEnvironment.browser().getDisplayName()).isEqualTo("Mozilla Firefox");
        }

        @Test
        void shouldDetectBrowser_whenUsingSamsungInternet() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("User-Agent")).thenReturn(
                    "Mozilla/5.0 (Linux; Android 13; SAMSUNG SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/18.0 Chrome/109.0.5414.87 Mobile Safari/537.36");
            ClientEnvironment clientEnvironment = HttpRequestUtils.getClientEnvironment(request);
            assertThat(clientEnvironment.browser().getDisplayName()).isEqualTo("Samsung Internet");
        }

        @Test
        void shouldDetectBrowser_whenUsingOpera() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("User-Agent"))
                    .thenReturn("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36 OPR/119.0.0.0");
            ClientEnvironment clientEnvironment = HttpRequestUtils.getClientEnvironment(request);
            assertThat(clientEnvironment.browser().getDisplayName()).isEqualTo("Opera");
        }

        @Test
        void shouldDetectBrowser_whenUsingBrave() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("User-Agent"))
                    .thenReturn("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36");
            when(request.getHeader("Sec-Ch-Ua")).thenReturn("\"Brave\";v=\"137\", \"Chromium\";v=\"137\", \"Not/A)Brand\";v=\"24\"");
            ClientEnvironment clientEnvironment = HttpRequestUtils.getClientEnvironment(request);
            assertThat(clientEnvironment.browser().getDisplayName()).isEqualTo("Brave");
        }

        @Test
        void shouldDetectIosApp() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("User-Agent")).thenReturn("Artemis/20250524013147 CFNetwork/3826.500.131 Darwin/24.5.0");
            ClientEnvironment clientEnvironment = HttpRequestUtils.getClientEnvironment(request);
            assertThat(clientEnvironment.artemisApp().getDisplayName()).isEqualTo("iOS App");
            assertThat(clientEnvironment.browser()).isNull();
            assertThat(clientEnvironment.operatingSystem()).isNull();
        }

        @Test
        void shouldDetectAndroidApp() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("User-Agent")).thenReturn("ktor-client");
            ClientEnvironment clientEnvironment = HttpRequestUtils.getClientEnvironment(request);
            assertThat(clientEnvironment.artemisApp().getDisplayName()).isEqualTo("Android App");
            assertThat(clientEnvironment.browser()).isNull();
            assertThat(clientEnvironment.operatingSystem()).isNull();
        }

        @Test
        void shouldNotFailOnUnknownUserAgent() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("User-Agent")).thenReturn(null);
            ClientEnvironment clientEnvironment = HttpRequestUtils.getClientEnvironment(request);
            assertThat(clientEnvironment).isNull();
        }

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
