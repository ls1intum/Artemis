package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.HAZELCAST;
import static de.tum.cit.aet.artemis.core.config.Constants.LOCAL;
import static de.tum.cit.aet.artemis.core.config.Constants.REDIS;
import static de.tum.cit.aet.artemis.core.config.DistributedDataProviderResolver.LEGACY_PROVIDER_PROPERTY;
import static de.tum.cit.aet.artemis.core.config.DistributedDataProviderResolver.PROVIDER_PROPERTY;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * Covers the precedence rules of the provider rename, since getting them wrong would silently change which backend a
 * deployment uses.
 */
class DistributedDataProviderResolverTest {

    @Test
    void testDefaultsToHazelcastWhenNeitherPropertyIsSet() {
        assertThat(DistributedDataProviderResolver.resolveProvider(new MockEnvironment())).isEqualTo(HAZELCAST);
    }

    @Test
    void testUsesLegacyPropertyWhenOnlyItIsSet() {
        MockEnvironment environment = new MockEnvironment().withProperty(LEGACY_PROVIDER_PROPERTY, REDIS);
        assertThat(DistributedDataProviderResolver.resolveProvider(environment)).isEqualTo(REDIS);
    }

    @Test
    void testUsesCurrentPropertyWhenOnlyItIsSet() {
        MockEnvironment environment = new MockEnvironment().withProperty(PROVIDER_PROPERTY, REDIS);
        assertThat(DistributedDataProviderResolver.resolveProvider(environment)).isEqualTo(REDIS);
    }

    /**
     * The shipped profiles still set the legacy key, so the current key has to win wherever an operator sets it.
     */
    @Test
    void testCurrentPropertyTakesPrecedenceOverLegacyProperty() {
        MockEnvironment environment = new MockEnvironment().withProperty(LEGACY_PROVIDER_PROPERTY, HAZELCAST).withProperty(PROVIDER_PROPERTY, REDIS);
        assertThat(DistributedDataProviderResolver.resolveProvider(environment)).isEqualTo(REDIS);
    }

    /**
     * A blank value must not shadow the legacy key, otherwise templating an empty variable would silently reset the
     * provider to the default.
     */
    @Test
    void testBlankCurrentPropertyFallsBackToLegacyProperty() {
        MockEnvironment environment = new MockEnvironment().withProperty(PROVIDER_PROPERTY, "  ").withProperty(LEGACY_PROVIDER_PROPERTY, REDIS);
        assertThat(DistributedDataProviderResolver.resolveProvider(environment)).isEqualTo(REDIS);
    }

    @Test
    void testIsProviderIgnoresCase() {
        MockEnvironment environment = new MockEnvironment().withProperty(PROVIDER_PROPERTY, "rEdIs");
        assertThat(DistributedDataProviderResolver.isProvider(environment, REDIS)).isTrue();
        assertThat(DistributedDataProviderResolver.isProvider(environment, HAZELCAST)).isFalse();
        assertThat(DistributedDataProviderResolver.isProvider(environment, LOCAL)).isFalse();
    }
}
