package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.HAZELCAST;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;

import java.util.Arrays;
import java.util.Collection;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Matches when Hazelcast backs the distributed data provider on a node that needs one.
 *
 * <p>
 * Includes {@code core} and not only {@code localci}/{@code buildagent}: the provider started out carrying just the
 * build job queue, but it now also carries cross-node state that exists on every core node (scheduling messages, feature
 * toggles, node metrics), so a core node without local CI needs a provider too.
 */
public class HazelcastDistributedDataCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        final Collection<String> activeProfiles = Arrays.asList(context.getEnvironment().getActiveProfiles());
        boolean nodeNeedsProvider = activeProfiles.contains(PROFILE_CORE) || activeProfiles.contains(PROFILE_LOCALCI) || activeProfiles.contains(PROFILE_BUILDAGENT);
        return nodeNeedsProvider && DistributedDataProviderResolver.isProvider(context.getEnvironment(), HAZELCAST);
    }
}
