package de.tum.cit.aet.artemis.core.service.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.stream.Stream;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;

import de.tum.cit.aet.artemis.core.util.ArtemisApp;
import de.tum.cit.aet.artemis.core.util.Browser;
import de.tum.cit.aet.artemis.core.util.ClientEnvironment;
import de.tum.cit.aet.artemis.core.util.HttpRequestUtils;
import de.tum.cit.aet.artemis.core.util.OperatingSystem;

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

        /**
         * We are not using @CsvSource because it would require many escaped quotes in the User-Agent strings.
         *
         * @return a stream of arguments for testing browser detection with various User-Agent and Sec-Ch-Ua headers.
         */
        private static Stream<Arguments> provideBrowserTestData() {
            return Stream.of(Arguments.of("'Chromium';v='136', 'Google Chrome';v='136', 'Not.A/Brand';v='99'", null, Browser.GOOGLE_CHROME),
                    Arguments.of("'Microsoft Edge';v='137', 'Chromium';v='137', 'Not.A/Brand';v='24'", null, Browser.MICROSOFT_EDGE),
                    Arguments.of("'Brave';v='137', 'Chromium';v='137', 'Not/A)Brand';v='24'",
                            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36", Browser.BRAVE),
                    Arguments.of("'Chromium';v='136', 'Opera';v='136', 'Not.A/Brand';v='99'",
                            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36 OPR/119.0.0.0", Browser.OPERA),
                    Arguments.of("'Chromium';v='136', 'Samsung Internet';v='136', 'Not.A/Brand';v='99'",
                            "Mozilla/5.0 (Linux; Android 13; SAMSUNG SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) SamsungBrowser/18.0 Chrome/109.0.5414.87 Mobile Safari/537.36",
                            Browser.SAMSUNG_INTERNET),
                    Arguments.of("'Chromium';v='136', 'Mozilla Firefox';v='136', 'Not.A/Brand';v='99'",
                            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:137.0) Gecko/20100101 Firefox/137.0", Browser.MOZILLA_FIREFOX),
                    Arguments.of("'Chromium';v='136', 'Apple Safari';v='136', 'Not.A/Brand';v='99'",
                            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.5 Safari/605.1.15", Browser.APPLE_SAFARI),
                    Arguments.of("'Chromium';v='136', 'DuckDuckGo';v='136', 'Not.A/Brand';v='99'",
                            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36 DuckDuckGo/7.0",
                            Browser.DUCKDUCKGO),
                    Arguments.of("'Chromium';v='136', 'Vivaldi';v='136', 'Not.A/Brand';v='99'",
                            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36 Vivaldi/5.7", Browser.VIVALDI));
        }

        @ParameterizedTest
        @MethodSource("provideBrowserTestData")
        void shouldDetectBrowser(String secChUa, String userAgent, Browser expectedBrowser) {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader(HttpHeaders.USER_AGENT)).thenReturn(userAgent);
            when(request.getHeader("Sec-Ch-Ua")).thenReturn(secChUa);

            ClientEnvironment clientEnvironment = HttpRequestUtils.getClientEnvironment(request);

            assertThat(clientEnvironment).isNotNull();
            assertThat(clientEnvironment.browser()).isEqualTo(expectedBrowser);
            assertThat(clientEnvironment.artemisApp()).isNull();
        }

        /**
         * We are not using @CsvSource because it would require many escaped quotes in the User-Agent strings.
         *
         * @return a stream of arguments for app detection with various User-Agent headers.
         */
        private static Stream<Arguments> provideAppTestData() {
            return Stream.of(Arguments.of("Artemis/20250524013147 CFNetwork/3826.500.131 Darwin/24.5.0", ArtemisApp.IOS), Arguments.of("ktor-client", ArtemisApp.ANDROID));
        }

        @ParameterizedTest
        @MethodSource("provideAppTestData")
        void shouldDetectArtemisApp(String userAgent, ArtemisApp expectedArtemisApp) {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader(HttpHeaders.USER_AGENT)).thenReturn(userAgent);

            ClientEnvironment clientEnvironment = HttpRequestUtils.getClientEnvironment(request);

            assertThat(clientEnvironment).isNotNull();
            assertThat(clientEnvironment.artemisApp()).isEqualTo(expectedArtemisApp);
            assertThat(clientEnvironment.browser()).isNull();
            assertThat(clientEnvironment.operatingSystem()).isNull();
        }

        @Test
        void shouldNotFailOnUnknownUserAgent() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader(HttpHeaders.USER_AGENT)).thenReturn(null);
            ClientEnvironment clientEnvironment = HttpRequestUtils.getClientEnvironment(request);
            assertThat(clientEnvironment).isNull();
        }

        /**
         * We are not using @CsvSource because it would require many escaped quotes in the User-Agent strings.
         *
         * @return a stream of arguments for testing operating system detection with various User-Agent headers.
         */
        private static Stream<Arguments> provideOperatingSystemTestData() {
            return Stream.of(Arguments.of("Mozilla/5.0 (Windows NT 10.0; Win64; x64)", OperatingSystem.WINDOWS),
                    Arguments.of("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)", OperatingSystem.MACOS), Arguments.of("Mozilla/5.0 (X11; Linux x86_64)", OperatingSystem.LINUX),
                    Arguments.of("Mozilla/5.0 (Linux; Android 13; SM-G991B Mobile)", OperatingSystem.ANDROID),
                    Arguments.of("Mozilla/5.0 (iPhone; CPU iPhone OS 15_5 like Mac OS X)", OperatingSystem.IOS));
        }

        @ParameterizedTest
        @MethodSource("provideOperatingSystemTestData")
        void shouldDetectOperatingSystem(String userAgent, OperatingSystem expectedOperatingSystem) {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader("Sec-Ch-Ua")).thenReturn("\"Chromium\";v=\"136\", \"Google Chrome\";v=\"136\", \"Not.A/Brand\";v=\"99\"");
            when(request.getHeader(HttpHeaders.USER_AGENT)).thenReturn(userAgent);

            ClientEnvironment clientEnvironment = HttpRequestUtils.getClientEnvironment(request);

            assertThat(clientEnvironment).isNotNull();
            assertThat(clientEnvironment.operatingSystem()).isNotNull();
            assertThat(clientEnvironment.operatingSystem()).isEqualTo(expectedOperatingSystem);
        }

        @Test
        void shouldReturnNullForOperatingSystem_whenUsingUnknownUserAgent() {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getHeader(HttpHeaders.USER_AGENT)).thenReturn("Unknown User-Agent");
            ClientEnvironment operatingSystem = HttpRequestUtils.getClientEnvironment(request);
            assertThat(operatingSystem).isNull();
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
