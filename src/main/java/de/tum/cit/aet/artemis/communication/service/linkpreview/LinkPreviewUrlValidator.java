package de.tum.cit.aet.artemis.communication.service.linkpreview;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validates URLs used to retrieve link previews.
 */
final class LinkPreviewUrlValidator {

    private static final Pattern VALID_DOMAIN_PATTERN = Pattern.compile("^(?!-)([a-zA-Z0-9-]{1,63}\\.)+[a-zA-Z]{2,20}$");

    private final HostAddressResolver hostAddressResolver;

    LinkPreviewUrlValidator() {
        this(InetAddress::getAllByName);
    }

    LinkPreviewUrlValidator(HostAddressResolver hostAddressResolver) {
        this.hostAddressResolver = hostAddressResolver;
    }

    ValidatedUrl validateAndResolve(URI uri) throws IOException {
        if (!isValidUri(uri)) {
            throw new IOException("Invalid link preview URL");
        }

        String host = uri.getHost();
        InetAddress[] addresses = hostAddressResolver.resolve(host);
        // Link previews are intentionally restricted to public IPv4 destinations. IPv6 records are ignored so that ordinary dual-stack domains remain usable.
        List<InetAddress> ipv4Addresses = Arrays.stream(addresses).filter(Inet4Address.class::isInstance).toList();
        if (ipv4Addresses.isEmpty() || ipv4Addresses.stream().anyMatch(address -> !isPublicIpv4Address(address))) {
            throw new UnknownHostException("The link preview host did not resolve to a public IPv4 address");
        }
        return new ValidatedUrl(uri, ipv4Addresses);
    }

    private boolean isValidUri(URI uri) {
        String scheme = uri.getScheme();
        String host = uri.getHost();
        int port = uri.getPort();

        return scheme != null && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) && host != null && uri.getUserInfo() == null && port <= 65535
                && !isIpAddress(host) && VALID_DOMAIN_PATTERN.matcher(host).matches();
    }

    private boolean isIpAddress(String host) {
        if (host.startsWith("[") && host.endsWith("]")) {
            return true;
        }
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            if (part.isEmpty()) {
                return false;
            }
            try {
                int value = Integer.parseInt(part);
                if (value < 0 || value > 255) {
                    return false;
                }
            }
            catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    private boolean isPublicIpv4Address(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] addressBytes = address.getAddress();
        int first = Byte.toUnsignedInt(addressBytes[0]);
        int second = Byte.toUnsignedInt(addressBytes[1]);
        int third = Byte.toUnsignedInt(addressBytes[2]);

        return first != 0 && !(first == 100 && second >= 64 && second <= 127) && !(first == 192 && second == 0 && third == 0) && !(first == 192 && second == 0 && third == 2)
                && !(first == 192 && second == 88 && third == 99) && !(first == 198 && (second == 18 || second == 19)) && !(first == 198 && second == 51 && third == 100)
                && !(first == 203 && second == 0 && third == 113) && first < 224;
    }

    @FunctionalInterface
    interface HostAddressResolver {

        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    record ValidatedUrl(URI uri, List<InetAddress> addresses) {

        ValidatedUrl {
            addresses = List.copyOf(addresses);
        }
    }
}
