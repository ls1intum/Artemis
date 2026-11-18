package de.tum.cit.aet.artemis.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.config.RateLimitingProperties;
import inet.ipaddr.IPAddressString;

class RateLimitExemptAddressTest {

    private static RateLimitConfigurationService serviceExempting(String... addresses) {
        var properties = new RateLimitingProperties();
        properties.setExemptAddresses(List.of(addresses));
        return new RateLimitConfigurationService(properties);
    }

    private static boolean isExempt(RateLimitConfigurationService service, String address) throws Exception {
        return service.isExempt(new IPAddressString(address).toAddress());
    }

    @Test
    void noAddressIsExemptByDefault() throws Exception {
        var service = new RateLimitConfigurationService(new RateLimitingProperties());

        assertThat(isExempt(service, "203.0.113.10")).isFalse();
    }

    @Test
    void exemptsALiteralIpv4Address() throws Exception {
        var service = serviceExempting("203.0.113.10");

        assertThat(isExempt(service, "203.0.113.10")).isTrue();
        assertThat(isExempt(service, "203.0.113.11")).isFalse();
    }

    @Test
    void exemptsALiteralIpv6Address() throws Exception {
        var service = serviceExempting("2001:db8::112");

        assertThat(isExempt(service, "2001:db8::112")).isTrue();
        assertThat(isExempt(service, "2001:db8::113")).isFalse();
    }

    @Test
    void exemptsEveryAddressInsideACidrBlock() throws Exception {
        var service = serviceExempting("203.0.113.0/24");

        assertThat(isExempt(service, "203.0.113.1")).isTrue();
        assertThat(isExempt(service, "203.0.113.254")).isTrue();
        assertThat(isExempt(service, "203.0.114.1")).isFalse();
    }

    /**
     * The same host presents itself differently depending on the connector, so an IPv4 address listed in
     * the configuration has to match its IPv4-mapped IPv6 form as well.
     */
    @Test
    void matchesAnIpv4AddressArrivingInItsIpv6MappedForm() throws Exception {
        var service = serviceExempting("203.0.113.10");

        assertThat(isExempt(service, "::ffff:203.0.113.10")).isTrue();
    }

    /**
     * The reverse of the case above: an administrator who writes the exempt entry in IPv4-mapped form has
     * to exempt the same host arriving as a plain IPv4 address. {@link inet.ipaddr.IPAddress#contains}
     * only matches within one address version, so the configured entry has to be normalised too.
     */
    @Test
    void matchesAnIpv4ClientAgainstAnIpv6MappedConfiguredAddress() throws Exception {
        var service = serviceExempting("::ffff:203.0.113.10");

        assertThat(isExempt(service, "203.0.113.10")).isTrue();
        assertThat(isExempt(service, "::ffff:203.0.113.10")).isTrue();
        assertThat(isExempt(service, "203.0.113.11")).isFalse();
    }

    @Test
    void ignoresAnUnparseableEntryRatherThanFailingToStart() throws Exception {
        var service = serviceExempting("not-an-address", "203.0.113.10");

        assertThat(isExempt(service, "203.0.113.10")).isTrue();
    }

    @Test
    void ignoresBlankEntries() throws Exception {
        var service = serviceExempting("", "   ", "203.0.113.10");

        assertThat(isExempt(service, "203.0.113.10")).isTrue();
    }

    @Test
    void handlesAnAbsentClientAddress() {
        var service = serviceExempting("203.0.113.10");

        assertThat(service.isExempt(null)).isFalse();
    }
}
