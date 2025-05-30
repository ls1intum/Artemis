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

    private static String getBrowserName(String userAgent) {
        if (userAgent.contains("Edg")) {
            return "Microsoft Edge";
        }
        else if (userAgent.contains("OPR") || userAgent.contains("Opera")) {
            return "Opera";
        }
        else if (userAgent.contains("SamsungBrowser")) {
            return "Samsung Internet";
        }
        else if (userAgent.contains("Chrome") && !userAgent.contains("Chromium")) {
            return "Google Chrome";
        }
        else if (userAgent.contains("Firefox")) {
            return "Mozilla Firefox";
        }
        else if (userAgent.contains("Safari") && !userAgent.contains("Chrome")) {
            return "Apple Safari";
        }
        else if (userAgent.contains("Brave")) {
            // TODO fix brave detection, as it is currently detected as Chrome
            return "Brave";
        }
        else if (userAgent.contains("Vivaldi")) {
            return "Vivaldi";
        }
        else if (userAgent.contains("DuckDuckGo")) {
            return "DuckDuckGo";
        }

        return null;
    }

    public static String detectClientType(@NotNull HttpServletRequest request) {

        String userAgent = request.getHeader(HttpHeaders.USER_AGENT);

        log.info("Detecting client type from user agent: {}", userAgent);

        if (userAgent == null || userAgent.isEmpty()) {
            return "Unknown";
        }

        boolean hasCFNetwork = userAgent.contains("CFNetwork");
        boolean hasIosAppName = userAgent.contains("Artemis");

        String browserName = getBrowserName(userAgent);
        if (browserName != null) {
            return browserName;
        }

        boolean isIosApp = hasIosAppName && hasCFNetwork;
        if (isIosApp) {
            return "iOS App";
        }

        boolean isAndroidApp = userAgent.contains("ktor-client");
        if (isAndroidApp) {
            return "Android App";
        }

        return "Unknown";
    }
}
