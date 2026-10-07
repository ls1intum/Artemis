package de.tum.cit.aet.artemis.core.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link IpAddresses}.
 * <p>
 * This exists because a build agent's observed address and the address of its git request are produced by different
 * components and legitimately differ in notation. The failure it prevents is an availability one - a working build agent
 * refused because a dual-stack socket reported it as {@code ::ffff:10.0.0.5} while the servlet container said
 * {@code 10.0.0.5} - so the cases below are notation pairs rather than parser edge cases.
 */
class IpAddressesTest {

    @ParameterizedTest
    @CsvSource({ "10.0.0.5,10.0.0.5", "::1,0:0:0:0:0:0:0:1", "0:0:0:0:0:0:0:1,::1", "2001:0db8:0000::1,2001:db8::1", "  10.0.0.5  ,10.0.0.5" })
    void shouldTreatEquivalentNotationsAsTheSameHost(String first, String second) {
        assertThat(IpAddresses.sameHost(first, second)).as("%s and %s are the same host", first, second).isTrue();
    }

    /**
     * The pair this class was added for. A dual-stack server reports an IPv4 client in the mapped form, and neither
     * string equality nor {@code IPAddress.equals} bridges it.
     */
    @ParameterizedTest
    @CsvSource({ "::ffff:10.0.0.5,10.0.0.5", "10.0.0.5,::ffff:10.0.0.5", "::ffff:192.168.1.7,192.168.1.7" })
    void shouldTreatAnIpv4MappedIpv6AddressAsItsIpv4Form(String first, String second) {
        assertThat(IpAddresses.sameHost(first, second)).as("%s and %s are the same host", first, second).isTrue();
    }

    @ParameterizedTest
    @CsvSource({ "10.0.0.5,10.0.0.6", "::1,::2", "::ffff:10.0.0.5,10.0.0.6", "2001:db8::1,10.0.0.5" })
    void shouldNotConflateDifferentHosts(String first, String second) {
        assertThat(IpAddresses.sameHost(first, second)).isFalse();
    }

    /**
     * Reached from inside authorization decisions, so an unusable value is an ordinary "no" and must never throw. A
     * range must not compare equal to anything either: a caller able to influence one side could otherwise pass a subnet
     * and have it match an identical subnet.
     *
     * @param unusable a value that cannot denote a single host
     */
    @ParameterizedTest
    @ValueSource(strings = { "", "   ", "not-an-address", "10.0.0.256", "10.0.0.0/8", "::ffff:garbage" })
    void shouldNotMatchAnUnusableValue(String unusable) {
        assertThatCode(() -> {
            assertThat(IpAddresses.sameHost(unusable, "10.0.0.5")).isFalse();
            assertThat(IpAddresses.sameHost("10.0.0.5", unusable)).isFalse();
            assertThat(IpAddresses.sameHost(unusable, unusable)).as("not even against itself, since it denotes no host").isFalse();
            assertThat(IpAddresses.canonical(unusable)).isNull();
        }).doesNotThrowAnyException();
    }

    @Test
    void shouldNotMatchNull() {
        assertThatCode(() -> {
            assertThat(IpAddresses.sameHost(null, "10.0.0.5")).isFalse();
            assertThat(IpAddresses.sameHost("10.0.0.5", null)).isFalse();
            assertThat(IpAddresses.sameHost(null, null)).isFalse();
            assertThat(IpAddresses.canonical(null)).isNull();
        }).doesNotThrowAnyException();
    }

    @Test
    void shouldReduceAMappedAddressToItsIpv4Form() {
        assertThat(IpAddresses.canonical("::ffff:10.0.0.5")).hasToString("10.0.0.5");
        assertThat(IpAddresses.canonical("10.0.0.5")).hasToString("10.0.0.5");
        assertThat(IpAddresses.canonical("::1")).isNotNull();
        assertThat(IpAddresses.canonical("2001:db8::1").isIPv6()).as("an address with no IPv4 equivalent stays IPv6").isTrue();
    }
}
