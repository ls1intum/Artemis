package de.tum.cit.aet.artemis.core.util;

import java.util.Optional;
import java.util.function.Predicate;

import jakarta.servlet.http.HttpServletRequest;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;

import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;

public final class HttpRequestUtils {

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
    public static String getIpStringFromRequest(@NonNull HttpServletRequest request) {
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
    public static Optional<IPAddress> getIpAddressFromRequest(@NonNull HttpServletRequest request) {
        final String ipString = getIpStringFromRequest(request);
        final IPAddress ipAddress = new IPAddressString(ipString).getAddress();
        return Optional.ofNullable(ipAddress);
    }

    /**
     * Resolves the address a request actually came from, believing forwarding headers only from a trusted proxy.
     * <p>
     * Prefer this over {@link #getIpStringFromRequest} wherever the address decides an authorization outcome. That
     * method returns the first {@code X-Forwarded-For} value whenever the header is present, so a caller names whatever
     * address it likes. Here the header is consulted only when {@link HttpServletRequest#getRemoteAddr()} is a reverse
     * proxy the installation operates, and the list is then walked from the right, taking the last entry that did not
     * come from a trusted proxy: entries further left were supplied by the client, because a proxy appends to the
     * header rather than replacing it.
     * <p>
     * <b>This is a second filter, not the only one, and it cannot repair the first.</b> Artemis runs with
     * {@code server.forward-headers-strategy: native}, so Tomcat's {@code RemoteIpValve} has already rewritten
     * {@code getRemoteAddr()} from {@code X-Forwarded-For} before this method sees anything, for any peer matching
     * {@code server.tomcat.remoteip.internal-proxies} - which defaults to the private ranges and loopback. The starting
     * point is therefore not the TCP peer but Tomcat's conclusion about it, and a caller connecting <em>from</em> one
     * of those ranges can put an arbitrary private address in that header and have it become the value returned here.
     * <p>
     * So the effective set of addresses whose forwarding headers are believed is the union of that regex and
     * {@code trustedProxies}, and an origin check built on this value is only as strong as the narrower of the two. An
     * installation that relies on the address for authorization has to narrow {@code server.tomcat.remoteip.internal-proxies}
     * to its own proxies; {@link de.tum.cit.aet.artemis.core.config.BuildAgentNetworkPolicy} says so at startup when it
     * sees an allowlist configured against the default.
     *
     * @param request        the HTTP request
     * @param isTrustedProxy tells whether an address is a reverse proxy this installation operates
     * @return the resolved client address, never null; falls back to the TCP peer whenever no forwarded entry can be
     *         trusted
     */
    @NonNull
    public static String getPeerIpString(@NonNull HttpServletRequest request, @NonNull Predicate<String> isTrustedProxy) {
        String peer = request.getRemoteAddr();
        if (peer == null || !isTrustedProxy.test(peer)) {
            // Either the request reached this node directly, or it came through a proxy we do not operate. Both mean
            // any forwarding header is unverified input, so the peer itself is the most that can be established.
            return peer == null ? "" : peer;
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return peer;
        }

        String[] entries = forwardedFor.split(",");
        for (int index = entries.length - 1; index >= 0; index--) {
            String candidate = entries[index].trim();
            if (candidate.isEmpty()) {
                continue;
            }
            if (!isTrustedProxy.test(candidate)) {
                return candidate;
            }
        }

        // Every entry named a trusted proxy, so the original client is not recorded in the header.
        return peer;
    }

    /**
     * Resolves the address a request actually came from, as an {@link IPAddress}.
     *
     * @param request        the HTTP request
     * @param isTrustedProxy tells whether an address is a reverse proxy this installation operates
     * @return the resolved client address, or empty if it could not be parsed
     * @see #getPeerIpString
     */
    public static Optional<IPAddress> getPeerIpAddress(@NonNull HttpServletRequest request, @NonNull Predicate<String> isTrustedProxy) {
        return Optional.ofNullable(new IPAddressString(getPeerIpString(request, isTrustedProxy)).getAddress());
    }

    /**
     * Retrieves the value of the specified header from the given {@link HttpServletRequest}.
     *
     * @param request    the HTTP request
     * @param headerName to be retrieved
     * @return the header value as a {@link String}, or an empty string if the header is not present
     */
    @NonNull
    private static String getHeaderValue(HttpServletRequest request, String headerName) {
        String headerValue = request.getHeader(headerName);
        return headerValue == null || headerValue.isEmpty() ? "" : headerValue;
    }

    /**
     * Determines the browser name based on the {@code User-Agent} and {@code Sec-Ch-Ua} headers in the given {@link HttpServletRequest}.
     * <p>
     * Logs a warning if the browser name cannot be detected.
     *
     * @param request the HTTP request
     * @return the detected {@link Browser}, or {@code null} if the browser cannot be determined
     */
    private static Browser getBrowserName(@NonNull HttpServletRequest request) {
        String userAgent = getHeaderValue(request, HttpHeaders.USER_AGENT);
        String secureClientHintsUserAgent = getHeaderValue(request, "Sec-Ch-Ua");

        if (secureClientHintsUserAgent.contains("Microsoft Edge")) {
            return Browser.MICROSOFT_EDGE;
        }
        else if (userAgent.contains("OPR") || userAgent.contains("Opera")) {
            return Browser.OPERA;
        }
        else if (userAgent.contains("SamsungBrowser")) {
            return Browser.SAMSUNG_INTERNET;
        }
        else if (secureClientHintsUserAgent.contains("Google Chrome")) {
            return Browser.GOOGLE_CHROME;
        }
        else if (userAgent.contains("Firefox")) {
            return Browser.MOZILLA_FIREFOX;
        }
        else if (userAgent.contains("Safari") && !userAgent.contains("Chrome")) {
            return Browser.APPLE_SAFARI;
        }
        else if (secureClientHintsUserAgent.contains("Brave")) {
            return Browser.BRAVE;
        }
        else if (userAgent.contains("Vivaldi")) {
            return Browser.VIVALDI;
        }
        else if (userAgent.contains("DuckDuckGo")) {
            return Browser.DUCKDUCKGO;
        }

        log.debug("Could not detect browser name from user agent: {}, secure user agent: {}", userAgent, secureClientHintsUserAgent);
        return null;
    }

    /**
     * Determines the operating system based on the {@code User-Agent} and {@code Sec-Ch-Ua-Platform} headers in the given {@link HttpServletRequest}.
     * <p>
     * Logs a warning if the operating system cannot be detected.
     *
     * @param request the HTTP request
     * @return the detected {@link OperatingSystem}, or {@code null} if the operating system cannot be determined
     */
    private static OperatingSystem getOperatingSystem(@NonNull HttpServletRequest request) {
        String userAgent = getHeaderValue(request, HttpHeaders.USER_AGENT);
        String secureClientHintsUserAgentPlatform = getHeaderValue(request, "Sec-Ch-Ua-Platform");

        if (userAgent.contains("Windows")) {
            return OperatingSystem.WINDOWS;
        }
        else if (userAgent.contains("iPhone") || userAgent.contains("iPad") || userAgent.contains("iOS")) {
            return OperatingSystem.IOS;
        }
        else if (userAgent.contains("Macintosh") || userAgent.contains("Mac OS X")) {
            return OperatingSystem.MACOS;
        }
        else if (userAgent.contains("Android")) {
            return OperatingSystem.ANDROID;
        }
        else if (userAgent.contains("Linux")) {
            return OperatingSystem.LINUX;
        }

        log.warn("Could not detect operating system from user agent: {}, secure user agent platform: {}", userAgent, secureClientHintsUserAgentPlatform);
        return null;
    }

    /**
     * Extracts the client environment from the given {@link HttpServletRequest}.
     * <p>
     * The method determines the browser, operating system, or application type (iOS or Android) based on the request headers.
     *
     * @param request the HTTP request
     * @return a {@link ClientEnvironment} object containing the detected client environment, or {@code null} if no environment can be determined
     */
    public static ClientEnvironment getClientEnvironment(@NonNull HttpServletRequest request) {
        Browser browserName = getBrowserName(request);
        OperatingSystem operatingSystem = getOperatingSystem(request);
        if (browserName != null) {
            return new ClientEnvironment(browserName, operatingSystem, null);
        }

        String userAgent = getHeaderValue(request, HttpHeaders.USER_AGENT);

        if (userAgent.isEmpty()) {
            return null;
        }

        boolean hasCFNetwork = userAgent.contains("CFNetwork");
        boolean hasIosAppName = userAgent.contains("Artemis");
        // expecting a format like "Artemis/20250524013147 CFNetwork/3826.500.131 Darwin/24.5.0" for the iOS app user agent
        boolean isIosApp = hasIosAppName && hasCFNetwork;
        if (isIosApp) {
            return new ClientEnvironment(null, null, ArtemisApp.IOS);
        }

        // the Android app appears to use https://ktor.io/
        boolean isAndroidApp = userAgent.contains("ktor-client");
        if (isAndroidApp) {
            return new ClientEnvironment(null, null, ArtemisApp.ANDROID);
        }

        return null;
    }
}
