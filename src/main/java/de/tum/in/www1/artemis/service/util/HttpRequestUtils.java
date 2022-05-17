package de.tum.in.www1.artemis.service.util;

import java.util.Optional;

import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.NotNull;

import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;

public class HttpRequestUtils {

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
}
