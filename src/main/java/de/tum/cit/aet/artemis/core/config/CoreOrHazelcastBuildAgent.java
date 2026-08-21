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
 * Used for the Eureka client. A Redis build agent does not register at all (see
 * {@link RedisBuildAgentDiscoveryEnvironmentPostProcessor}); a core node still does, whichever provider is configured.
 *
 * <p>
 * That is deliberate but not load-bearing: the only code that <em>reads</em> the registry is Hazelcast member
 * discovery, so on a Redis deployment the registration has no consumer and the JHipster registry could be dropped
 * from the topology entirely. Narrowing this condition is left out of the change that introduced it, because it
 * removes a service from every Redis deployment rather than merely rewiring one inside the application.
 *
 * <p>
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
