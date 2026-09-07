package de.tum.cit.aet.artemis.core.util;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;

/**
 * Comparison of single IP addresses that arrive as text from two different components.
 * <p>
 * Exists because the two sides of every origin check in the build agent paths are formatted independently: the
 * middleware reports whatever Hazelcast or Redis produced for a socket, while the request side comes from the servlet
 * container or the ssh server. The same host therefore shows up in different notations, and the two that matter are
 * <ul>
 * <li>the two textual forms of an IPv6 address, {@code ::1} and {@code 0:0:0:0:0:0:0:1};</li>
 * <li>the IPv4-mapped IPv6 form a dual-stack socket reports for an IPv4 client, {@code ::ffff:10.0.0.5} against
 * {@code 10.0.0.5}.</li>
 * </ul>
 * The first is handled by parsing at all. The second needs an explicit conversion, which is easy to promise in a comment
 * and forget in the code - a string comparison and even {@link IPAddress#equals} both answer "different" - and getting
 * it wrong refuses a legitimate build agent rather than admitting an illegitimate one, which makes it a failure that
 * shows up as a broken build rather than as a security alert.
 * <p>
 * Separate from {@link IpRangeSet}, which answers a different question - whether an address falls inside a configured
 * range - and converts towards the family of that range. Here neither side is a range and there is nothing to convert
 * towards, so both are reduced to a canonical form instead.
 */
public final class IpAddresses {

    private static final Logger log = LoggerFactory.getLogger(IpAddresses.class);

    private IpAddresses() {
    }

    /**
     * Checks whether two textual addresses denote the same host.
     *
     * @param first  one address, may be null, blank or unparsable
     * @param second the other address, may be null, blank or unparsable
     * @return {@code false} unless both parse as a single address and denote the same host. Never throws: this is
     *         reached from inside authorization decisions, where an exception would deny a legitimate build or escape
     *         into an ssh handshake.
     */
    public static boolean sameHost(@Nullable String first, @Nullable String second) {
        IPAddress firstAddress = canonical(first);
        IPAddress secondAddress = canonical(second);
        return firstAddress != null && secondAddress != null && firstAddress.equals(secondAddress);
    }

    /**
     * Reduces an address to the form used for comparison, mapping an IPv4-mapped IPv6 address to its IPv4 equivalent.
     *
     * @param ipAddress the address as it was reported, may be null, blank or unparsable
     * @return the canonical address, or {@code null} if the value is not a single usable address
     */
    @Nullable
    public static IPAddress canonical(@Nullable String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return null;
        }
        // No try/catch here on purpose. getAddress() reports a malformed value by returning null rather than by
        // throwing - that is what distinguishes it from toAddress(), which declares AddressStringException - and the
        // conversion below is guarded by isIPv4Convertible(). Checked against the parser rather than assumed: sixteen
        // malformed and pathological inputs, "/8", "::ffff:", "1:2:3:4:5:6:7:8:9", "10.0.0.5:80", "[10.0.0.5]" and
        // "%eth0" among them, all return null and none throws. A catch here would therefore be untested code on an
        // authorization path, and it could not do anything the null branch does not already do.
        IPAddress address = new IPAddressString(ipAddress.trim()).getAddress();
        if (address == null || address.isPrefixed() || address.isMultiple()) {
            // Null is a malformed value. A prefix or a multi-valued address is well formed but denotes a range, and a
            // range is not a host: without this a caller able to influence one side could pass a subnet and have it
            // compare equal to an identical subnet.
            log.debug("'{}' does not denote a single IP address, so it matches no host", ipAddress);
            return null;
        }
        if (address.isIPv6() && address.toIPv6().isIPv4Convertible()) {
            return address.toIPv6().toIPv4();
        }
        return address;
    }
}
