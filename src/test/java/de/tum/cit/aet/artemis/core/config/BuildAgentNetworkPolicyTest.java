package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.mock.env.MockEnvironment;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * Tests for {@link BuildAgentNetworkPolicy}.
 * <p>
 * Two behaviours here are load bearing in opposite directions. An empty allowlist must mean "no restriction", because
 * the property is absent in every installation that upgrades and a deny would stop all builds. A malformed entry must
 * fail startup, because a matcher that silently never matches turns an allowlist into a deny-everything.
 */
class BuildAgentNetworkPolicyTest {

    private static BuildAgentNetworkPolicy policyWith(List<String> allowedRanges, List<String> trustedProxies) {
        BuildAgentNetworkConfiguration configuration = new BuildAgentNetworkConfiguration();
        configuration.setAllowedRanges(allowedRanges);
        configuration.setTrustedProxies(trustedProxies);
        return new BuildAgentNetworkPolicy(configuration, new MockEnvironment());
    }

    @Test
    void shouldNotRestrictAnythingWithoutConfiguredRanges() {
        BuildAgentNetworkPolicy policy = policyWith(List.of(), List.of());

        assertThat(policy.isAllowlistConfigured()).isFalse();
        assertThat(policy.isWithinAllowedRanges("203.0.113.9")).as("an empty allowlist means no restriction, never deny all, or every installation breaks on upgrade").isTrue();
        assertThat(policy.isWithinAllowedRanges(null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "10.0.0.1", "10.255.255.254", "192.168.1.7" })
    void shouldAcceptAddressesInsideTheConfiguredRanges(String address) {
        BuildAgentNetworkPolicy policy = policyWith(List.of("10.0.0.0/8", "192.168.1.7"), List.of());

        assertThat(policy.isWithinAllowedRanges(address)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "203.0.113.9", "11.0.0.1", "192.168.1.8" })
    void shouldRejectAddressesOutsideTheConfiguredRanges(String address) {
        BuildAgentNetworkPolicy policy = policyWith(List.of("10.0.0.0/8", "192.168.1.7"), List.of());

        assertThat(policy.isWithinAllowedRanges(address)).isFalse();
    }

    @Test
    void shouldMatchIpv6Ranges() {
        BuildAgentNetworkPolicy policy = policyWith(List.of("2001:db8::/32"), List.of());

        assertThat(policy.isWithinAllowedRanges("2001:db8::1")).isTrue();
        assertThat(policy.isWithinAllowedRanges("2001:db9::1")).isFalse();
    }

    /**
     * An address of the other family is neither inside nor malformed; it simply does not match, and must not be let
     * through by an exception escaping the matcher.
     */
    @Test
    void shouldRejectAnAddressOfADifferentFamilyThanTheConfiguredRange() {
        BuildAgentNetworkPolicy policy = policyWith(List.of("10.0.0.0/8"), List.of());

        assertThat(policy.isWithinAllowedRanges("2001:db8::1")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "  ", "not-an-address" })
    void shouldRejectAnUnusableAddressWhenAnAllowlistIsConfigured(String address) {
        BuildAgentNetworkPolicy policy = policyWith(List.of("10.0.0.0/8"), List.of());

        assertThat(policy.isWithinAllowedRanges(address)).isFalse();
    }

    @Test
    void shouldFailStartupOnAMalformedRange() {
        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> policyWith(List.of("10.0.0.0/8", "nonsense"), List.of())).withMessageContaining("nonsense")
                .withMessageContaining("artemis.continuous-integration.build-agent-network.allowed-ranges");
    }

    @Test
    void shouldFailStartupOnAMalformedTrustedProxy() {
        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> policyWith(List.of(), List.of("300.1.2.3/8")))
                .withMessageContaining("artemis.continuous-integration.build-agent-network.trusted-proxies");
    }

    @Test
    void shouldRecogniseConfiguredTrustedProxies() {
        BuildAgentNetworkPolicy policy = policyWith(List.of(), List.of("10.0.0.0/8"));

        assertThat(policy.isTrustedProxy("10.1.2.3")).isTrue();
        assertThat(policy.isTrustedProxy("203.0.113.9")).isFalse();
        assertThat(policy.isTrustedProxy(null)).isFalse();
    }

    @Test
    void shouldTrustNoProxyByDefault() {
        BuildAgentNetworkPolicy policy = policyWith(List.of(), List.of());

        assertThat(policy.isTrustedProxy("10.1.2.3")).as("without configured proxies no forwarding header may be believed").isFalse();
    }

    /**
     * An unrestricted node and a restricted one behave identically until something is refused, so the startup log is
     * where a deployment that meant to bound its build agents finds out that it did not. Exercised for both states,
     * because the misleading one is the state where the property was set but did not take effect.
     */
    @Test
    void shouldLogWhatIsEnforced() {
        assertThatCode(() -> {
            policyWith(List.of(), List.of()).logConfiguredPolicy();
            policyWith(List.of("10.0.0.0/8"), List.of("192.168.1.0/24")).logConfiguredPolicy();
        }).doesNotThrowAnyException();

        assertThat(policyWith(List.of(), List.of()).isAllowlistConfigured()).isFalse();
        assertThat(policyWith(List.of("10.0.0.0/8"), List.of()).isAllowlistConfigured()).isTrue();
    }

    /**
     * The allowlist is read from an address Tomcat may have taken out of {@code X-Forwarded-For}: Artemis runs with
     * {@code forward-headers-strategy: native}, and {@code server.tomcat.remoteip.internal-proxies} defaults to the
     * private ranges, so a caller reaching the node from any private address can name whichever address it likes.
     * <p>
     * That leaves an operator who configured an allowlist believing in a restriction that is not effective, with
     * nothing anywhere saying so. The startup warning is that signal, so it is pinned here - and it must stay quiet in
     * the two cases that are not a mistake: no allowlist (a decision not to restrict) and a narrowed Tomcat property.
     */
    @Test
    void shouldWarnWhenAnAllowlistRestsOnTheDefaultForwardedHeaderTrust() {
        assertThat(startupLogOf(List.of("10.0.0.0/8"), new MockEnvironment())).anyMatch(message -> message.contains("server.tomcat.remoteip.internal-proxies"));
    }

    @Test
    void shouldNotWarnWhenTheForwardedHeaderTrustIsNarrowed() {
        MockEnvironment narrowed = new MockEnvironment().withProperty("server.tomcat.remoteip.internal-proxies", "10\\.0\\.0\\.1");

        assertThat(startupLogOf(List.of("10.0.0.0/8"), narrowed)).noneMatch(message -> message.contains("server.tomcat.remoteip.internal-proxies is not set"));
    }

    @Test
    void shouldNotWarnWithoutAnAllowlist() {
        assertThat(startupLogOf(List.of(), new MockEnvironment())).noneMatch(message -> message.contains("server.tomcat.remoteip.internal-proxies is not set"));
    }

    /**
     * @param allowedRanges the configured build agent networks
     * @param environment   the environment the policy reads the Tomcat property from
     * @return the warnings the policy emits while stating what it enforces
     */
    private static List<String> startupLogOf(List<String> allowedRanges, MockEnvironment environment) {
        BuildAgentNetworkConfiguration configuration = new BuildAgentNetworkConfiguration();
        configuration.setAllowedRanges(allowedRanges);
        BuildAgentNetworkPolicy policy = new BuildAgentNetworkPolicy(configuration, environment);

        var logger = (Logger) LoggerFactory.getLogger(BuildAgentNetworkPolicy.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            policy.logConfiguredPolicy();
        }
        finally {
            logger.detachAppender(appender);
            appender.stop();
        }
        return appender.list.stream().filter(event -> event.getLevel() == Level.WARN).map(ILoggingEvent::getFormattedMessage).toList();
    }

}
