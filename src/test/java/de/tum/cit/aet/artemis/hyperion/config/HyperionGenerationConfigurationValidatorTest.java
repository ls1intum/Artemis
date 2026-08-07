package de.tum.cit.aet.artemis.hyperion.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Lazy;

import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.HyperionEffortProfileService;

/**
 * The documented promise that a contradictory generation configuration fails the deploy rather than an instructor's first Generate. Every bean enforcing one of these rules is
 * {@code @Lazy}, as is every injector of those beans, so without an eager validator such a deployment boots cleanly and surfaces as a 500 much later.
 */
class HyperionGenerationConfigurationValidatorTest {

    private static final Duration OWNER_HEARTBEAT_INTERVAL = Duration.ofSeconds(15);

    private static final int VALID_SEMANTIC_REPAIRS = 6;

    private static HyperionGenerationConfigurationValidator validator(HyperionAgentProperties properties, Duration ownerHeartbeatInterval, int maxSemanticRepairs) {
        // An empty model collection is what a node without a configured ChatModel binds.
        return new HyperionGenerationConfigurationValidator(properties, new HyperionEffortProfileService(properties, List.of()), ownerHeartbeatInterval, maxSemanticRepairs);
    }

    private static HyperionAgentProperties withProfileDeadline(Duration profileMaxJobDuration) {
        HyperionAgentProperties.EffortProfileProperties thorough = new HyperionAgentProperties.EffortProfileProperties();
        thorough.setLabel("Thorough");
        thorough.setMaxJobDuration(profileMaxJobDuration);
        HyperionAgentProperties properties = new HyperionAgentProperties();
        properties.setProfiles(new LinkedHashMap<>(Map.of("thorough", thorough)));
        return properties;
    }

    @Test
    void theShippedConfigurationIsAccepted() {
        assertThatCode(() -> validator(new HyperionAgentProperties(), OWNER_HEARTBEAT_INTERVAL, VALID_SEMANTIC_REPAIRS)).doesNotThrowAnyException();
    }

    @Test
    void aStaleJobTimeoutBelowTheLongestProfileDeadline_failsStartup() {
        // The deployment default stays inside the stale timeout; only the profile crosses it, which is the deployment where another node reclaims the slot of a run that is still
        // legitimately going.
        HyperionAgentProperties raisedByProfile = withProfileDeadline(new HyperionAgentProperties().getStaleJobTimeout().plusMinutes(5));

        assertThatIllegalArgumentException().isThrownBy(() -> validator(raisedByProfile, OWNER_HEARTBEAT_INTERVAL, VALID_SEMANTIC_REPAIRS))
                .withMessageContaining("stale-job-timeout").withMessageContaining("profiles");
        // A profile that stays under the timeout is legitimate and must still boot.
        assertThatCode(() -> validator(withProfileDeadline(Duration.ofMinutes(12)), OWNER_HEARTBEAT_INTERVAL, VALID_SEMANTIC_REPAIRS)).doesNotThrowAnyException();
    }

    @Test
    void anOwnerHeartbeatThatCannotFireWithinARun_failsStartup() {
        HyperionAgentProperties properties = new HyperionAgentProperties();

        assertThatIllegalArgumentException().isThrownBy(() -> validator(properties, properties.getMaxJobDuration(), VALID_SEMANTIC_REPAIRS))
                .withMessageContaining("owner-heartbeat-interval");
        assertThatIllegalArgumentException().isThrownBy(() -> validator(properties, properties.getMaxJobDuration().plusMinutes(1), VALID_SEMANTIC_REPAIRS))
                .withMessageContaining("owner-heartbeat-interval");
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, -1, HyperionGenerationConfigurationValidator.MAX_SEMANTIC_REPAIRS + 1 })
    void aRepairBudgetOutsideTheReviewedRange_failsStartup(int maxSemanticRepairs) {
        assertThatIllegalArgumentException().isThrownBy(() -> validator(new HyperionAgentProperties(), OWNER_HEARTBEAT_INTERVAL, maxSemanticRepairs))
                .withMessageContaining("max-semantic-repairs");
    }

    @Test
    void bothEndsOfTheReviewedRepairRangeAreAccepted() {
        // The ceiling is published to administrators as 1..12 and the attempt cap is derived from it, so widening it silently changes the shape of the loop.
        assertThat(HyperionGenerationConfigurationValidator.MAX_SEMANTIC_REPAIRS).isEqualTo(12);
        assertThatCode(() -> validator(new HyperionAgentProperties(), OWNER_HEARTBEAT_INTERVAL, 1)).doesNotThrowAnyException();
        assertThatCode(() -> validator(new HyperionAgentProperties(), OWNER_HEARTBEAT_INTERVAL, HyperionGenerationConfigurationValidator.MAX_SEMANTIC_REPAIRS))
                .doesNotThrowAnyException();
    }

    @Test
    void theValidatorIsEagerSoAContradictoryConfigurationFailsTheDeployRatherThanTheFirstGenerate() {
        // Without this the class could hold every rule above and still validate nothing until an instructor clicks Generate.
        Lazy lazy = HyperionGenerationConfigurationValidator.class.getAnnotation(Lazy.class);

        assertThat(lazy).isNotNull();
        assertThat(lazy.value()).isFalse();
    }
}
