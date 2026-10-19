package de.tum.cit.aet.artemis.core.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link IpRangeSet}.
 * <p>
 * This type exists because both of its failure modes are load bearing where it is used, and both are the opposite of
 * what a naive matcher does:
 * <ul>
 * <li>Parsing must be <b>strict</b>. A range that is accepted but can never match turns an allowlist into a deny of
 * everything, which is why a malformed value has to fail startup rather than be tolerated.</li>
 * <li>Matching must be <b>total</b>. Comparing addresses of different families, or a value that is not an address at
 * all, is an ordinary "no" and must never throw out of an authorization check.</li>
 * </ul>
 */
class IpRangeSetTest {

    private static final String PROPERTY = "artemis.test.ranges";

    @Test
    void shouldMatchNothingWhenEmpty() {
        assertThat(IpRangeSet.parse(List.of(), PROPERTY).isEmpty()).isTrue();
        assertThat(IpRangeSet.parse(List.of(), PROPERTY).contains("10.0.0.5")).isFalse();
        assertThat(IpRangeSet.parse(null, PROPERTY).isEmpty()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({ "10.0.0.0/8,10.0.0.5,true", "10.0.0.0/8,10.255.255.254,true", "10.0.0.0/8,11.0.0.1,false", "192.168.1.7,192.168.1.7,true", "192.168.1.7,192.168.1.8,false",
            "2001:db8::/32,2001:db8::1,true", "2001:db8::/32,2001:db9::1,false", "127.0.0.0/8,127.0.0.1,true" })
    void shouldMatchAddressesAgainstRanges(String range, String address, boolean expected) {
        assertThat(IpRangeSet.parse(List.of(range), PROPERTY).contains(address)).isEqualTo(expected);
    }

    @Test
    void shouldMatchAnyOfSeveralRanges() {
        IpRangeSet ranges = IpRangeSet.parse(List.of("10.0.0.0/8", "192.168.1.0/24", "2001:db8::/32"), PROPERTY);

        assertThat(ranges.contains("10.1.2.3")).isTrue();
        assertThat(ranges.contains("192.168.1.9")).isTrue();
        assertThat(ranges.contains("2001:db8::5")).isTrue();
        assertThat(ranges.contains("203.0.113.9")).isFalse();
    }

    /**
     * The strictness claim. Each of these is either meaningless or silently truncated by a lenient parser, and a range
     * that parses but never matches is worse than one that fails loudly.
     *
     * @param malformed a value that must be refused
     */
    @ParameterizedTest
    // A blank entry is in the list because the parser reads it as the loopback address rather than rejecting it, so
    // accepting it would silently trust 127.0.0.1 - a trailing comma in an environment variable is enough to produce one
    @ValueSource(strings = { "nonsense", "10.0.0.0/-1", "10.0.0.0/8/16", "10.0.0.0/33", "2001:db8::/129", "300.1.2.3", "10.0.0.0/", "/8", "10.0.0.256", "", "   " })
    void shouldRefuseAMalformedRangeAtParseTime(String malformed) {
        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> IpRangeSet.parse(List.of(malformed), PROPERTY)).withMessageContaining(malformed)
                .withMessageContaining(PROPERTY);
    }

    @Test
    void shouldRefuseAMalformedRangeAmongValidOnes() {
        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> IpRangeSet.parse(List.of("10.0.0.0/8", "nonsense", "192.168.1.0/24"), PROPERTY))
                .withMessageContaining("nonsense");
    }

    /**
     * The totality claim. None of these may throw, because they are reached from inside an authorization decision where
     * an exception would either deny a legitimate build or escape into the ssh handshake.
     *
     * @param address a value that must produce a plain false
     */
    @ParameterizedTest
    @ValueSource(strings = { "", "   ", "not-an-address", "10.0.0.256", "10.0.0.0/8", "::ffff:garbage" })
    void shouldNotMatchAnUnusableAddress(String address) {
        IpRangeSet ranges = IpRangeSet.parse(List.of("10.0.0.0/8"), PROPERTY);

        assertThatCode(() -> assertThat(ranges.contains(address)).isFalse()).doesNotThrowAnyException();
    }

    @Test
    void shouldNotMatchNull() {
        assertThatCode(() -> assertThat(IpRangeSet.parse(List.of("10.0.0.0/8"), PROPERTY).contains(null)).isFalse()).doesNotThrowAnyException();
    }

    /**
     * Comparing across address families is the case a naive matcher throws on, and it is reached in practice by any
     * installation that configures IPv4 ranges while a client connects over IPv6, or the reverse.
     */
    @Test
    void shouldNotThrowWhenComparingAcrossAddressFamilies() {
        IpRangeSet ipv4 = IpRangeSet.parse(List.of("10.0.0.0/8"), PROPERTY);
        IpRangeSet ipv6 = IpRangeSet.parse(List.of("2001:db8::/32", "::1/128"), PROPERTY);

        assertThatCode(() -> {
            assertThat(ipv4.contains("2001:db8::1")).isFalse();
            assertThat(ipv6.contains("10.0.0.5")).isFalse();
        }).doesNotThrowAnyException();
    }

    /**
     * The two sides of a comparison are formatted by different components - the middleware on one side, the servlet
     * container or ssh server on the other - so the same address arrives in different notations.
     *
     * @param range   the configured range
     * @param address the address in another notation
     */
    @ParameterizedTest
    @CsvSource({ "::1/128,0:0:0:0:0:0:0:1", "0:0:0:0:0:0:0:1/128,::1", "2001:0db8:0000::/32,2001:db8::1", "10.0.0.0/8,::ffff:10.0.0.5" })
    void shouldNormaliseEquivalentNotations(String range, String address) {
        assertThat(IpRangeSet.parse(List.of(range), PROPERTY).contains(address)).as("%s should contain %s", range, address).isTrue();
    }

    @Test
    void shouldTolerateSurroundingWhitespaceInConfiguration() {
        IpRangeSet ranges = IpRangeSet.parse(List.of("  10.0.0.0/8  "), PROPERTY);

        assertThat(ranges.contains("10.0.0.5")).isTrue();
        assertThat(ranges.contains(" 10.0.0.5 ")).isTrue();
    }

    @Test
    void shouldReportTheRangesAsConfiguredForDisplay() {
        assertThat(IpRangeSet.parse(List.of("10.0.0.0/8", "192.168.1.7"), PROPERTY).getConfiguredRanges()).containsExactly("10.0.0.0/8", "192.168.1.7");
    }

    /**
     * The startup log lines that tell an operator what is enforced interpolate this set directly, so it has to render
     * the ranges rather than an object identity.
     */
    @Test
    void shouldRenderTheConfiguredRanges() {
        assertThat(IpRangeSet.parse(List.of("10.0.0.0/8", "192.168.1.7"), PROPERTY)).hasToString("[10.0.0.0/8, 192.168.1.7]");
        assertThat(IpRangeSet.parse(List.of(), PROPERTY)).hasToString("[]");
    }
}
