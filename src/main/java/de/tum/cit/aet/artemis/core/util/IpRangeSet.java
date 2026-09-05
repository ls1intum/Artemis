package de.tum.cit.aet.artemis.core.util;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;

/**
 * An immutable set of IP address ranges, parsed once and matched many times.
 * <p>
 * Exists so that every place deciding something from a configured range list behaves identically, and so that the two
 * failure modes that matter for an allowlist are handled in one place rather than per caller:
 * <ul>
 * <li><b>A malformed range must not parse.</b> A range that silently never matches turns an allowlist into a deny of
 * everything, so {@link #parse} rejects the configuration at startup instead. Validation is strict: a truncated or
 * doubled prefix length such as {@code 10.0.0.0/-1} or {@code 10.0.0.0/8/16} is refused rather than partially read.</li>
 * <li><b>A mismatch must not throw.</b> Comparing an IPv4 address against an IPv6 range, or matching a value that is not
 * an address at all, is an ordinary "no" and must never propagate out of an authorization check.</li>
 * </ul>
 * Built on {@code inet.ipaddr}, which is already the address library on the git request paths, rather than on
 * {@code IpAddressMatcher}: this keeps parsing strict and comparison total, and it normalises the two textual forms of
 * the same address (for example {@code ::1} and {@code 0:0:0:0:0:0:0:1}, or an IPv4-mapped {@code ::ffff:10.0.0.5}).
 */
public final class IpRangeSet {

    private static final Logger log = LoggerFactory.getLogger(IpRangeSet.class);

    private static final IpRangeSet EMPTY = new IpRangeSet(List.of(), List.of());

    private final List<String> configuredRanges;

    private final List<IPAddress> ranges;

    private IpRangeSet(List<String> configuredRanges, List<IPAddress> ranges) {
        this.configuredRanges = configuredRanges;
        this.ranges = ranges;
    }

    /**
     * Parses a configured list of CIDR blocks and single addresses.
     *
     * @param configuredRanges the values as configured, may be null or empty
     * @param propertyName     the property the values came from, named in the error so a misconfiguration is
     *                             immediately traceable
     * @return the parsed set, empty if nothing was configured
     * @throws IllegalStateException if any value is not a valid address or CIDR block
     */
    public static IpRangeSet parse(@Nullable List<String> configuredRanges, String propertyName) {
        if (configuredRanges == null || configuredRanges.isEmpty()) {
            return EMPTY;
        }
        List<IPAddress> parsed = configuredRanges.stream().map(range -> {
            if (range == null || range.isBlank()) {
                // The parser reads an empty string as the loopback address rather than rejecting it, so a stray entry -
                // a trailing comma in an environment variable is the easy way to get one - would silently add 127.0.0.1
                // to the set. On a trusted-proxy or proxy-protocol list that quietly grants the local host the right to
                // name an arbitrary client, which is the opposite of what an operator writing an empty value intends.
                throw new IllegalStateException(malformedRangeMessage(String.valueOf(range), propertyName));
            }
            IPAddressString candidate = new IPAddressString(range.trim());
            try {
                candidate.validate();
            }
            // RuntimeException as well as the declared AddressStringException: some malformed values, "/8" among them,
            // surface as a NullPointerException from the parser rather than as a validation error, and an operator
            // deserves the message below rather than a stack trace with no property name in it.
            catch (inet.ipaddr.AddressStringException | RuntimeException e) {
                throw new IllegalStateException(malformedRangeMessage(range, propertyName), e);
            }
            IPAddress parsedRange = candidate.getAddress();
            if (parsedRange == null) {
                // validate() accepts a few strings it cannot then turn into an address, "/8" among them. Without this
                // the list would hold a null and every later match would fail with a NullPointerException instead.
                throw new IllegalStateException(malformedRangeMessage(range, propertyName));
            }
            return parsedRange;
        }).toList();
        return new IpRangeSet(List.copyOf(configuredRanges), parsed);
    }

    private static String malformedRangeMessage(String range, String propertyName) {
        return "Cannot parse '" + range + "' in " + propertyName
                + " as an IP address or CIDR block. Use a single address such as 192.168.1.7 or a block such as 10.0.0.0/8 or 2001:db8::/32. Refusing to start rather than "
                + "silently never matching, which on an allowlist would refuse everything.";
    }

    /**
     * @return whether no range is configured, in which case {@link #contains} is always {@code false}
     */
    public boolean isEmpty() {
        return ranges.isEmpty();
    }

    /**
     * Checks whether an address lies in one of these ranges.
     *
     * @param ipAddress the address to check, may be null or unparsable
     * @return {@code false} for a null, blank or unparsable address, and for an address of a different family than
     *         every configured range; otherwise whether some range contains it
     */
    public boolean contains(@Nullable String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank() || ranges.isEmpty()) {
            return false;
        }
        IPAddress candidate;
        try {
            candidate = new IPAddressString(ipAddress.trim()).getAddress();
        }
        catch (RuntimeException e) {
            log.debug("Cannot parse '{}' as an IP address, so it matches no configured range", ipAddress, e);
            return false;
        }
        if (candidate == null) {
            log.debug("Cannot parse '{}' as an IP address, so it matches no configured range", ipAddress);
            return false;
        }
        // The thing being checked has to be one address. A CIDR block such as 10.0.0.0/8 parses happily and is
        // "contained" by an identical configured range, so without this a caller who can influence the value being
        // checked - an X-Forwarded-For entry, say - could pass a subnet and match an allowlist entry.
        if (candidate.isPrefixed() || candidate.isMultiple()) {
            log.debug("'{}' denotes a range rather than a single address, so it matches no configured range", ipAddress);
            return false;
        }

        for (IPAddress range : ranges) {
            if (containsSameFamily(range, candidate)) {
                return true;
            }
            // A dual-stack server reports an IPv4 client as an IPv4-mapped IPv6 address, so ::ffff:10.0.0.5 has to be
            // comparable against 10.0.0.0/8. The library converts, but only if asked.
            IPAddress converted = toOtherFamily(candidate, range);
            if (converted != null && containsSameFamily(range, converted)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsSameFamily(IPAddress range, IPAddress candidate) {
        return range.getIPVersion() == candidate.getIPVersion() && range.contains(candidate);
    }

    /**
     * Converts an address to the family of the given range where the two are equivalent, so that an IPv4-mapped IPv6
     * address can be compared against an IPv4 range and the reverse.
     *
     * @param candidate the address being checked
     * @param range     the range it is being checked against
     * @return the converted address, or {@code null} if no equivalent exists in that family
     */
    @Nullable
    private static IPAddress toOtherFamily(IPAddress candidate, IPAddress range) {
        try {
            if (candidate.isIPv6() && range.isIPv4() && candidate.toIPv6().isIPv4Convertible()) {
                return candidate.toIPv6().toIPv4();
            }
            if (candidate.isIPv4() && range.isIPv6() && candidate.toIPv4().isIPv6Convertible()) {
                return candidate.toIPv4().toIPv6();
            }
        }
        catch (RuntimeException e) {
            log.debug("Cannot convert {} to the family of {}", candidate, range, e);
        }
        return null;
    }

    /**
     * @return the ranges exactly as configured, for logging and for display in the admin UI
     */
    public List<String> getConfiguredRanges() {
        return configuredRanges;
    }

    @Override
    public String toString() {
        return configuredRanges.toString();
    }
}
