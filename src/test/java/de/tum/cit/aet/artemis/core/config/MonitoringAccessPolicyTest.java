package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link MonitoringAccessPolicy}.
 * <p>
 * The list is what the SpEL expression in the constructor produces from a comma-separated property, so the cases below
 * pass the elements as they come out of {@code split(",")}. What matters is that a block admits the whole range of a
 * Prometheus pod address, that a single address still works exactly as before, and that an empty value keeps meaning
 * "nobody", instead of turning into the loopback address or into a startup failure.
 */
class MonitoringAccessPolicyTest {

    private static MonitoringAccessPolicy policyWith(String... configuredAddresses) {
        return new MonitoringAccessPolicy(List.of(configuredAddresses));
    }

    @ParameterizedTest
    @ValueSource(strings = { "10.42.1.15", "10.42.0.1", "10.42.255.254" })
    void shouldAdmitAnAddressInsideAConfiguredBlock(String address) {
        assertThat(policyWith("10.42.0.0/16").isAllowed(address)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "10.43.0.1", "192.168.1.7", "203.0.113.9" })
    void shouldDenyAnAddressOutsideEveryConfiguredBlock(String address) {
        assertThat(policyWith("10.42.0.0/16").isAllowed(address)).isFalse();
    }

    @Test
    void shouldStillAdmitASingleConfiguredAddressAndOnlyThat() {
        MonitoringAccessPolicy policy = policyWith("10.42.1.15");

        assertThat(policy.isAllowed("10.42.1.15")).isTrue();
        assertThat(policy.isAllowed("10.42.1.16")).as("a single address is not a block").isFalse();
    }

    @Test
    void shouldAdmitAnyOfSeveralEntriesAndIgnoreSpacesAroundThem() {
        MonitoringAccessPolicy policy = policyWith("127.0.0.1", " 10.42.0.0/16", "2001:db8::/32 ");

        assertThat(policy.isAllowed("127.0.0.1")).isTrue();
        assertThat(policy.isAllowed("10.42.7.7")).isTrue();
        assertThat(policy.isAllowed("2001:db8:0:1::5")).isTrue();
        assertThat(policy.isAllowed("2001:db9::1")).isFalse();
    }

    @Test
    void shouldRecognizeTheTwoTextualFormsOfTheLoopbackAddress() {
        // The servlet container reports the IPv6 loopback address in its long form. A plain string comparison against
        // "::1" never matched it, which silently denied a scrape from the same host on a dual-stack node.
        assertThat(policyWith("::1").isAllowed("0:0:0:0:0:0:0:1")).isTrue();
    }

    @Test
    void shouldAdmitAnIpv4PeerThatTheServerReportsAsIpv4MappedIpv6() {
        assertThat(policyWith("10.42.0.0/16").isAllowed("::ffff:10.42.1.15")).isTrue();
    }

    @Test
    void shouldAdmitNobodyForAnEmptyValue() {
        // "".split(",") yields one empty element. It must neither fail the start nor become the loopback address.
        MonitoringAccessPolicy policy = policyWith("");

        assertThat(policy.isAllowed("127.0.0.1")).isFalse();
        assertThat(policy.isAllowed("10.42.1.15")).isFalse();
    }

    @Test
    void shouldDenyANullOrUnparsablePeerWithoutThrowing() {
        MonitoringAccessPolicy policy = policyWith("10.42.0.0/16");

        assertThat(policy.isAllowed(null)).isFalse();
        assertThat(policy.isAllowed("")).isFalse();
        assertThat(policy.isAllowed("not-an-address")).isFalse();
    }

    @Test
    void shouldNotLetACallerPassABlockAsThePeer() {
        // A caller who can influence the checked value must not match a block entry by naming that block.
        assertThat(policyWith("10.42.0.0/16").isAllowed("10.42.0.0/16")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = { "10.42.0.0/-1", "10.42.0.0/8/16", "not-an-address", "10.42.0.256" })
    void shouldRefuseToStartWithAMalformedEntryAndNameTheProperty(String malformed) {
        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> policyWith("127.0.0.1", malformed)).withMessageContaining("spring.prometheus.monitoringIp");
    }
}
