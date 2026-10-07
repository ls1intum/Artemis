package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;
import static de.tum.cit.aet.artemis.core.config.Constants.REDIS;

import java.util.Arrays;
import java.util.Collection;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Matches when Redis backs the distributed data provider on a node that needs one.
 *
 * <p>
 * See {@link HazelcastDistributedDataCondition} for why {@code core} is included alongside {@code localci} and
 * {@code buildagent}.
 */
public class RedisDistributedDataCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        final Collection<String> activeProfiles = Arrays.asList(context.getEnvironment().getActiveProfiles());
        boolean nodeNeedsProvider = activeProfiles.contains(PROFILE_CORE) || activeProfiles.contains(PROFILE_LOCALCI) || activeProfiles.contains(PROFILE_BUILDAGENT);
        return nodeNeedsProvider && DistributedDataProviderResolver.isProvider(context.getEnvironment(), REDIS);
    }
}
