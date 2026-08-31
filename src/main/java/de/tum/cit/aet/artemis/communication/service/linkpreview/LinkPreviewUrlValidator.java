package de.tum.cit.aet.artemis.communication.service.linkpreview;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
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
        if (addresses.length == 0 || Arrays.stream(addresses).anyMatch(address -> !isPublicAddress(address))) {
            throw new UnknownHostException("The link preview host did not resolve to a public address");
        }
        return new ValidatedUrl(uri, List.copyOf(Arrays.asList(addresses)));
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

    private boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        if (address instanceof Inet4Address) {
            return isPublicIpv4Address(address.getAddress());
        }
        if (address instanceof Inet6Address) {
            return isPublicIpv6Address(address.getAddress());
        }
        return false;
    }

    private boolean isPublicIpv4Address(byte[] address) {
        int first = Byte.toUnsignedInt(address[0]);
        int second = Byte.toUnsignedInt(address[1]);
        int third = Byte.toUnsignedInt(address[2]);

        return first != 0 && !(first == 100 && second >= 64 && second <= 127) && !(first == 192 && second == 0 && third == 0) && !(first == 192 && second == 0 && third == 2)
                && !(first == 192 && second == 88 && third == 99) && !(first == 198 && (second == 18 || second == 19)) && !(first == 198 && second == 51 && third == 100)
                && !(first == 203 && second == 0 && third == 113) && first < 224;
    }

    private boolean isPublicIpv6Address(byte[] address) {
        int first = Byte.toUnsignedInt(address[0]);
        int second = Byte.toUnsignedInt(address[1]);
        int third = Byte.toUnsignedInt(address[2]);
        int fourth = Byte.toUnsignedInt(address[3]);

        boolean uniqueLocalAddress = (first & 0xFE) == 0xFC;
        boolean documentationAddress = first == 0x20 && second == 0x01 && third == 0x0D && fourth == 0xB8;
        boolean embeddedIpv4Address = isEmbeddedIpv4Address(address);
        boolean ipv4Ipv6TranslationAddress = isIpv4Ipv6TranslationAddress(address);
        return !uniqueLocalAddress && !documentationAddress && !embeddedIpv4Address && !ipv4Ipv6TranslationAddress;
    }

    private boolean isIpv4Ipv6TranslationAddress(byte[] address) {
        // Both standardized IPv4/IPv6 translation prefixes start with 64:ff9b.
        if (address[0] != 0 || address[1] != 0x64 || address[2] != (byte) 0xFF || address[3] != (byte) 0x9B) {
            return false;
        }

        // 64:ff9b:1::/48 is reserved for local-use translation.
        if (address[4] == 0 && address[5] == 1) {
            return true;
        }

        // 64:ff9b::/96 is the well-known translation prefix.
        for (int i = 4; i < 12; i++) {
            if (address[i] != 0) {
                return false;
            }
        }
        return true;
    }

    private boolean isEmbeddedIpv4Address(byte[] address) {
        for (int i = 0; i < 10; i++) {
            if (address[i] != 0) {
                return false;
            }
        }
        return (address[10] == 0 && address[11] == 0) || (address[10] == (byte) 0xFF && address[11] == (byte) 0xFF);
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
