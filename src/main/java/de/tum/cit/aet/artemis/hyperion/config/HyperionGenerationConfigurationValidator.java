package de.tum.cit.aet.artemis.hyperion.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.config.DistributedDataProviderResolver;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.HyperionEffortProfileService;

/**
 * Makes the documented generation configuration invariants true at startup rather than at an instructor's first Generate.
 * <p>
 * Every bean that enforces one of these rules is {@code @Lazy}, and so is every injector of those beans, so a contradictory pair (a {@code stale-job-timeout} that does not
 * outlast the longest {@code max-job-duration}, or a heartbeat interval longer than a run) would otherwise boot cleanly and only surface as a 500 on the first generation. This
 * bean is therefore <em>not</em> lazy: it costs one properties object and the effort-profile materialisation, and it turns a config typo from a production incident into a failed
 * deploy. Materialising {@link HyperionEffortProfileService} here also validates the profile set itself (names, decoding parameters, staged-context values) at startup.
 * <p>
 * It only holds rules that relate two configuration values. Anything that needs Hazelcast, a repository, or the sandbox stays where it is: forcing that graph eagerly would trade
 * one startup failure mode for several.
 */
@Lazy(false)
@Component
@Conditional(HyperionExerciseGenerationEnabled.class)
public class HyperionGenerationConfigurationValidator {

    /** Ceiling on the configured repair budget; the attempt cap is derived from it, so an unreviewed value would set the loop's whole shape. */
    public static final int MAX_SEMANTIC_REPAIRS = 12;

    public HyperionGenerationConfigurationValidator(HyperionAgentProperties agentProperties, HyperionEffortProfileService effortProfiles,
            @Value("${artemis.hyperion.agent.owner-heartbeat-interval:PT15S}") Duration ownerHeartbeatInterval,
            @Value("${artemis.hyperion.agent.max-semantic-repairs:6}") int maxSemanticRepairs, Environment environment) {
        if (DistributedDataProviderResolver.isProvider(environment, "Redis")) {
            throw new IllegalStateException(
                    "Hyperion whole-exercise generation requires Hazelcast or Local distributed data; Redis cannot yet prove authoritative data-node topology");
        }
        Duration maxJobDuration = agentProperties.getMaxJobDuration();
        HyperionGenerationTimeouts.validateMaxJobDuration(maxJobDuration);
        HyperionGenerationTimeouts.validateOwnerHeartbeatInterval(ownerHeartbeatInterval, effortProfiles.shortestMaxJobDuration());
        HyperionGenerationTimeouts.validateStaleJobTimeout(agentProperties.getStaleJobTimeout(), effortProfiles.longestMaxJobDuration());
        validateMaxSemanticRepairs(maxSemanticRepairs);
    }

    /**
     * Requires a repair budget inside the reviewed range, rejecting rather than substituting the default.
     *
     * @param maxSemanticRepairs the configured number of semantic repair rounds
     * @throws IllegalArgumentException if it is outside 1..{@value #MAX_SEMANTIC_REPAIRS}
     */
    public static void validateMaxSemanticRepairs(int maxSemanticRepairs) {
        if (maxSemanticRepairs <= 0 || maxSemanticRepairs > MAX_SEMANTIC_REPAIRS) {
            throw new IllegalArgumentException("artemis.hyperion.agent.max-semantic-repairs must be between 1 and " + MAX_SEMANTIC_REPAIRS + " but was " + maxSemanticRepairs);
        }
    }

}
