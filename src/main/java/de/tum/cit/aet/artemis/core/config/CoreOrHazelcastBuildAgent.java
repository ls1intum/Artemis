package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.HAZELCAST;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Arrays;
import java.util.Collection;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Matches on core nodes, and on build agents that reach the core cluster through Hazelcast.
 *
 * <p>
 * Used for Eureka service discovery, which every core node needs regardless of which distributed data provider is
 * configured, while a Redis build agent does not register at all (see {@link RedisBuildAgentDiscoveryEnvironmentPostProcessor}).
 * For the Hazelcast beans themselves use {@link HazelcastDistributedDataCondition}: those must not load when another
 * provider is selected, otherwise a Redis deployment still runs a second distributed system.
 */
public class CoreOrHazelcastBuildAgent implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        final Collection<String> activeProfiles = Arrays.asList(context.getEnvironment().getActiveProfiles());

        return activeProfiles.contains(PROFILE_CORE)
                || (activeProfiles.contains(PROFILE_BUILDAGENT) && DistributedDataProviderResolver.isProvider(context.getEnvironment(), HAZELCAST));
    }
}
