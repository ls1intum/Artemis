package de.tum.cit.aet.artemis.config;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_BUILDAGENT;
import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;

import java.util.Arrays;
import java.util.Collection;

import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Custom condition that checks for the presence of the 'buildagent' profile and the absence of the 'core' profile.
 * This condition will be used to conditionally exclude certain configurations based on the active Spring profiles.
 */
public class BuildAgentWithoutCoreCondition extends AllNestedConditions {

    /**
     * Constructor for BuildAgentWithoutCoreCondition.
     * Specifies that the conditions should be evaluated during the configuration parsing phase.
     */
    public BuildAgentWithoutCoreCondition() {
        super(ConfigurationPhase.PARSE_CONFIGURATION);
    }

    /**
     * Inner class representing the condition that the 'buildagent' profile is active.
     */
    @Conditional(BuildAgentProfileCondition.class)
    @SuppressWarnings("unused")
    static class OnBuildAgentProfile {
    }

    /**
     * Inner class representing the condition that the 'core' profile is not active.
     */
    @Conditional(NotCoreProfileCondition.class)
    @SuppressWarnings("unused")
    static class OnNotCoreProfile {
    }

    /**
     * Condition implementation that checks if the 'buildagent' profile is active.
     */
    static class BuildAgentProfileCondition implements Condition {

        /**
         * Evaluates whether the 'buildagent' profile is active.
         *
         * @param context  the condition context
         * @param metadata the metadata of the {@link Conditional} annotation
         * @return true if 'buildagent' profile is active, false otherwise
         */
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            final Collection<String> activeProfiles = Arrays.asList(context.getEnvironment().getActiveProfiles());
            return activeProfiles.contains(PROFILE_BUILDAGENT);
        }
    }

    /**
     * Condition implementation that checks if the 'core' profile is *not* active.
     */
    static class NotCoreProfileCondition implements Condition {

        /**
         * Evaluates whether the 'core' profile is not active.
         *
         * @param context  the condition context
         * @param metadata the metadata of the {@link Conditional} annotation
         * @return true if 'core' profile is not active, false otherwise
         */
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            final Collection<String> activeProfiles = Arrays.asList(context.getEnvironment().getActiveProfiles());
            return !activeProfiles.contains(PROFILE_CORE);
        }
    }
}
