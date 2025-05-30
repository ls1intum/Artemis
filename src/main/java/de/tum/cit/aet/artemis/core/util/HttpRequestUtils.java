package de.tum.cit.aet.artemis.core.util;

import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;

import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;

public class HttpRequestUtils {

    private static final Logger log = LoggerFactory.getLogger(HttpRequestUtils.class);

    private HttpRequestUtils() {
        // Utility class, no instantiation allowed
    }

    private static final String[] IP_HEADER_CANDIDATES = { "X-Forwarded-For", "Proxy-Client-IP", "WL-Proxy-Client-IP", "HTTP_X_FORWARDED_FOR", "HTTP_X_FORWARDED",
            "HTTP_X_CLUSTER_CLIENT_IP", "HTTP_CLIENT_IP", "HTTP_FORWARDED_FOR", "HTTP_FORWARDED", "HTTP_VIA", "REMOTE_ADDR" };

    /**
     * Extract Client IP Address from Http Request as String
     *
     * @param request Http Request
     * @return String representation of IP Address
     */
    private static String getIpStringFromRequest(@NotNull HttpServletRequest request) {
        for (String header : IP_HEADER_CANDIDATES) {
            String ipList = request.getHeader(header);
            if (ipList != null && !ipList.isEmpty() && !"unknown".equalsIgnoreCase(ipList)) {
                return ipList.split(",")[0];
            }
        }

        return request.getRemoteAddr();
    }

    /**
     * Extract Client IP Address from Http Request as IPAddress Object
     *
     * @param request Http Request
     * @return IPAddress Object
     */
    public static Optional<IPAddress> getIpAddressFromRequest(@NotNull HttpServletRequest request) {
        final String ipString = getIpStringFromRequest(request);
        final IPAddress ipAddress = new IPAddressString(ipString).getAddress();
        return Optional.ofNullable(ipAddress);
    }

    private static Browser getBrowserName(String userAgent) {
        if (userAgent.contains("Edg")) {
            return Browser.MICROSOFT_EDGE;
        }
        else if (userAgent.contains("OPR") || userAgent.contains("Opera")) {
            return Browser.OPERA;
        }
        else if (userAgent.contains("SamsungBrowser")) {
            return Browser.SAMSUNG_INTERNET;
        }
        else if (userAgent.contains("Chrome") && !userAgent.contains("Chromium")) {
            return Browser.GOOGLE_CHROME;
        }
        else if (userAgent.contains("Firefox")) {
            return Browser.MOZILLA_FIREFOX;
        }
        else if (userAgent.contains("Safari") && !userAgent.contains("Chrome")) {
            return Browser.APPLE_SAFARI;
        }
        else if (userAgent.contains("Brave")) {
            // TODO fix as it is currently detected as Chrome
            return Browser.BRAVE;
        }
        else if (userAgent.contains("Vivaldi")) {
            return Browser.VIVALDI;
        }
        else if (userAgent.contains("DuckDuckGo")) {
            return Browser.DUCKDUCKGO;
        }

        log.warn("Could not detect browser name from user agent: {}", userAgent);
        return null;
    }

    private static OperatingSystem getOperatingSystem(String userAgent) {
        if (userAgent.contains("Windows")) {
            return OperatingSystem.WINDOWS;
        }
        else if (userAgent.contains("Macintosh") || userAgent.contains("Mac OS X")) {
            return OperatingSystem.MACOS;
        }
        else if (userAgent.contains("Linux")) {
            return OperatingSystem.LINUX;
        }
        else if (userAgent.contains("Android")) {
            return OperatingSystem.ANDROID;
        }
        else if (userAgent.contains("iPhone") || userAgent.contains("iPad") || userAgent.contains("iOS")) {
            return OperatingSystem.IOS;
        }

        log.warn("Could not detect operating system from user agent: {}", userAgent);
        return null;
    }

    public static ClientEnvironment getClientEnvironment(@NotNull HttpServletRequest request) {

        String userAgent = request.getHeader(HttpHeaders.USER_AGENT);

        log.info("Detecting client type from user agent: {}", userAgent);

        if (userAgent == null || userAgent.isEmpty()) {
            return null;
        }

        boolean hasCFNetwork = userAgent.contains("CFNetwork");
        boolean hasIosAppName = userAgent.contains("Artemis");

        Browser browserName = getBrowserName(userAgent);
        OperatingSystem operatingSystem = getOperatingSystem(userAgent);
        if (browserName != null) {
            return new ClientEnvironment(browserName, operatingSystem, null);
        }

        boolean isIosApp = hasIosAppName && hasCFNetwork;
        if (isIosApp) {
            return new ClientEnvironment(null, null, ArtemisApp.IOS);
        }

        boolean isAndroidApp = userAgent.contains("ktor-client");
        if (isAndroidApp) {
            return new ClientEnvironment(null, null, ArtemisApp.ANROID);
        }

        return null;
    }
}
