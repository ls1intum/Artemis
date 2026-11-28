package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.hyperion.config.HyperionAgentProperties;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEffortProfileDTO;
import de.tum.cit.aet.artemis.hyperion.runtime.agent.HyperionGenerationSettings;

/** The admin-defined half of "admins define, users select": each rule here decides what a run is allowed to cost. */
class HyperionEffortProfileServiceTest {

    private static final String AGENT_PRESET = "generation";

    private static HyperionAgentProperties.EffortProfileProperties profile(String label) {
        HyperionAgentProperties.EffortProfileProperties profile = new HyperionAgentProperties.EffortProfileProperties();
        profile.setLabel(label);
        return profile;
    }

    private static HyperionAgentProperties propertiesWith(Map<String, HyperionAgentProperties.EffortProfileProperties> profiles) {
        HyperionAgentProperties properties = agentProperties();
        properties.setProfiles(new LinkedHashMap<>(profiles));
        return properties;
    }

    private static HyperionAgentProperties agentProperties() {
        HyperionAgentProperties properties = new HyperionAgentProperties();
        properties.setLlmPreset(AGENT_PRESET);
        return properties;
    }

    @Test
    void repairPresetIsInheritedOverriddenAndPreservedWhenBoundsNarrow() {
        var standard = profile("Standard");
        var thorough = profile("Thorough");
        thorough.setRepairLlmPreset("specialist");
        var properties = propertiesWith(Map.of("standard", standard, "thorough", thorough));
        properties.setRepairLlmPreset("deployment-repair");
        var service = new HyperionEffortProfileService(properties);
        assertThat(service.resolve(null).repairLlmPreset()).isEqualTo("deployment-repair");
        assertThat(service.resolve("standard").repairLlmPreset()).isEqualTo("deployment-repair");
        assertThat(service.resolve("thorough").tightenedBy(100_000L, Duration.ofMinutes(5)).repairLlmPreset()).isEqualTo("specialist");

        // Like the profile's own preset, a blank repair preset inherits the agent's; without one the handoff is disabled.
        thorough.setRepairLlmPreset(" ");
        assertThat(new HyperionEffortProfileService(properties).resolve("thorough").repairLlmPreset()).isEqualTo("deployment-repair");
        properties.setRepairLlmPreset(null);
        assertThat(new HyperionEffortProfileService(properties).resolve("thorough").repairLlmPreset()).isNull();
    }

    @Test
    void repairPresetsAreValidatedAtStartupWithoutRetries() {
        var thorough = profile("Thorough");
        thorough.setRepairLlmPreset("specialist");
        var properties = propertiesWith(Map.of("thorough", thorough));
        properties.setRepairLlmPreset("deployment-repair");

        assertThat(properties.llmPresetAssignments()).filteredOn(assignment -> assignment.propertyKey().endsWith("repair-llm-preset"))
                .extracting(assignment -> assignment.propertyKey() + "=" + assignment.preset() + ":" + assignment.requiresNoRetry()).containsExactlyInAnyOrder(
                        "artemis.hyperion.agent.repair-llm-preset=deployment-repair:true", "artemis.hyperion.agent.profiles.thorough.repair-llm-preset=specialist:true");
    }

    @Test
    void withoutConfiguredProfiles_everyRunUsesTheDeploymentDefaultAndNoneIsSelectable() {
        HyperionAgentProperties properties = agentProperties();
        properties.setMaxTurns(60);
        properties.setMaxJobDuration(Duration.ofMinutes(45));

        HyperionEffortProfileService service = new HyperionEffortProfileService(properties);

        assertThat(service.selectableProfiles()).isEmpty();
        HyperionGenerationSettings resolved = service.resolve(null);
        assertThat(resolved.name()).isEmpty();
        assertThat(resolved.maxTurns()).isEqualTo(60);
        assertThat(resolved.maxJobDuration()).isEqualTo(Duration.ofMinutes(45));
        // The engine reuses the shared singletons rather than deriving per-run objects for a deployment that configured nothing.
        assertThat(resolved.engineDefaults()).isTrue();
        assertThat(resolved.chatOptions()).isNull();
        assertThat(resolved.llmPreset()).isEqualTo(AGENT_PRESET);
    }

    @Test
    void unknownProfileName_isRejectedRatherThanSilentlyDefaulted() {
        HyperionEffortProfileService service = new HyperionEffortProfileService(propertiesWith(Map.of("standard", profile("Standard"))));

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> service.resolve("thorough"))
                .satisfies(exception -> assertThat(exception.getBody().getProperties()).containsEntry("message", "error.unknownEffortProfile"));
    }

    @Test
    void profileWithNoConfiguredName_isRejectedEvenWhenAnotherProfileExists() {
        // The accepted set must equal the published set, so a name outside it is rejected even after trimming.
        HyperionEffortProfileService service = new HyperionEffortProfileService(propertiesWith(Map.of("draft", profile("Quick draft"))));

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> service.resolve("   draft-typo "));
        assertThat(service.resolve(" draft ").name()).isEqualTo("draft");
    }

    @Test
    void unsetProfileValues_inheritTheDeploymentDefault() {
        HyperionAgentProperties properties = agentProperties();
        properties.setMaxTurns(60);
        properties.setMaxJobDuration(Duration.ofMinutes(45));
        properties.setMaxTokensPerJob(3_000_000L);
        properties.setContextWindowTokens(128_000);
        properties.setProfiles(new LinkedHashMap<>(Map.of("standard", profile("Standard"))));

        HyperionGenerationSettings standard = new HyperionEffortProfileService(properties).resolve("standard");

        assertThat(standard.maxTurns()).isEqualTo(60);
        assertThat(standard.maxJobDuration()).isEqualTo(Duration.ofMinutes(45));
        assertThat(standard.maxTokensPerJob()).isEqualTo(3_000_000L);
        assertThat(standard.contextWindowTokens()).isEqualTo(128_000);
        // A profile that changes nothing stays recognisable as the deployment default, so it costs no per-run object graph.
        assertThat(standard.engineDefaults()).isTrue();
    }

    @Test
    void profileWithItsOwnPreset_runsThatPresetAndLeavesTheDeploymentSingletons() {
        HyperionAgentProperties.EffortProfileProperties thorough = profile("Thorough");
        thorough.setLlmPreset(" generationthorough ");
        thorough.setMaxTurns(90);

        HyperionGenerationSettings settings = new HyperionEffortProfileService(propertiesWith(Map.of("thorough", thorough))).resolve("thorough");

        assertThat(settings.llmPreset()).isEqualTo("generationthorough");
        assertThat(settings.providerOptionsOverride()).isTrue();
        assertThat(settings.engineDefaults()).isFalse();
        // Core only names the preset; the worker builds the model options from its own catalog.
        assertThat(settings.chatOptions()).isNull();
        assertThat(settings.maxTurns()).isEqualTo(90);
    }

    @Test
    void profileNamingTheAgentPreset_isNoProviderOverride() {
        HyperionAgentProperties.EffortProfileProperties standard = profile("Standard");
        standard.setLlmPreset(AGENT_PRESET);

        HyperionGenerationSettings settings = new HyperionEffortProfileService(propertiesWith(Map.of("standard", standard))).resolve("standard");

        assertThat(settings.llmPreset()).isEqualTo(AGENT_PRESET);
        assertThat(settings.providerOptionsOverride()).isFalse();
        assertThat(settings.engineDefaults()).isTrue();
    }

    @Test
    void selectableProfiles_exposeNameAndLabelOnlyAndKeepConfiguredOrder() {
        // Six entries rather than three: Map.copyOf randomizes iteration order per JVM via ImmutableCollections' salt, so a short list can match the configured order by chance.
        LinkedHashMap<String, HyperionAgentProperties.EffortProfileProperties> profiles = new LinkedHashMap<>();
        HyperionAgentProperties.EffortProfileProperties draft = profile("Quick draft");
        draft.setLlmPreset("secretprocurementpreset");
        profiles.put("draft", draft);
        profiles.put("standard", profile("Standard"));
        profiles.put("thorough", profile("Thorough"));
        profiles.put("exhaustive", profile("Exhaustive"));
        profiles.put("experimental", profile("Experimental"));
        profiles.put("unlabelled", new HyperionAgentProperties.EffortProfileProperties());

        List<ExerciseGenerationEffortProfileDTO> selectable = new HyperionEffortProfileService(propertiesWith(profiles)).selectableProfiles();

        assertThat(selectable).extracting(ExerciseGenerationEffortProfileDTO::name).containsExactlyElementsOf(profiles.keySet());
        // No preset or model may reach an instructor through this endpoint, and an unlabelled profile falls back to its key rather than to null.
        assertThat(selectable).extracting(ExerciseGenerationEffortProfileDTO::label).contains("Quick draft", "unlabelled").doesNotContain("secretprocurementpreset");
        assertThat(JsonObjectMapper.get().valueToTree(selectable).toString()).doesNotContain("preset", "model", "reasoning", "maxTokens", "maxTurns");
    }

    @Test
    void defaultProfile_selectsTheNamedProfileForRequestsThatNameNone() {
        HyperionAgentProperties.EffortProfileProperties thorough = profile("Thorough");
        thorough.setMaxTurns(90);
        HyperionAgentProperties properties = propertiesWith(Map.of("thorough", thorough));
        properties.setDefaultProfile("thorough");

        assertThat(new HyperionEffortProfileService(properties).resolve(null).maxTurns()).isEqualTo(90);
    }

    @Test
    void defaultProfileNamingAnUnconfiguredProfile_failsStartup() {
        HyperionAgentProperties properties = propertiesWith(Map.of("draft", profile("Quick draft")));
        properties.setDefaultProfile("thorough");

        assertThatIllegalArgumentException().isThrownBy(() -> new HyperionEffortProfileService(properties)).withMessageContaining("default-profile");
    }

    @Test
    void invalidStagedContext_failsStartupRatherThanTheFirstRunThatUsesIt() {
        HyperionAgentProperties.EffortProfileProperties draft = profile("Quick draft");
        draft.setStagedContext("SOMETIMES");

        assertThatIllegalArgumentException().isThrownBy(() -> new HyperionEffortProfileService(propertiesWith(Map.of("draft", draft)))).withMessageContaining("staged-context");
    }

    @Test
    void nonPositiveProfileBudgets_failStartup() {
        HyperionAgentProperties.EffortProfileProperties broken = profile("Broken");
        broken.setMaxTurns(0);

        assertThatIllegalArgumentException().isThrownBy(() -> new HyperionEffortProfileService(propertiesWith(Map.of("broken", broken)))).withMessageContaining("max-turns");
    }

    @Test
    void largestMaxTokensPerJob_isTheMaximumOverTheDefaultAndEveryProfile() {
        HyperionAgentProperties.EffortProfileProperties draft = profile("Quick draft");
        draft.setMaxTokensPerJob(600_000L);
        HyperionAgentProperties.EffortProfileProperties thorough = profile("Thorough");
        thorough.setMaxTokensPerJob(6_000_000L);
        HyperionAgentProperties properties = propertiesWith(Map.of("draft", draft, "thorough", thorough));
        properties.setMaxTokensPerJob(3_000_000L);

        assertThat(new HyperionEffortProfileService(properties).largestMaxTokensPerJob()).isEqualTo(6_000_000L);
    }

    @Test
    void largestMaxTokensPerJob_fallsBackToTheDeploymentDefaultWhenEveryProfileIsSmaller() {
        HyperionAgentProperties.EffortProfileProperties draft = profile("Quick draft");
        draft.setMaxTokensPerJob(600_000L);
        HyperionAgentProperties properties = propertiesWith(Map.of("draft", draft));
        properties.setMaxTokensPerJob(3_000_000L);

        assertThat(new HyperionEffortProfileService(properties).largestMaxTokensPerJob()).isEqualTo(3_000_000L);
    }

    @Test
    void longestMaxJobDuration_withoutConfiguredProfiles_isTheDeploymentDefault() {
        HyperionAgentProperties properties = agentProperties();
        properties.setMaxJobDuration(Duration.ofMinutes(45));

        assertThat(new HyperionEffortProfileService(properties).longestMaxJobDuration()).isEqualTo(Duration.ofMinutes(45));
    }

    @Test
    void longestMaxJobDuration_isTheLongestDeadlineAnyProfileCanHandARun() {
        // Startup validates the stale-slot timeout against this value, so a profile that raises the deadline past that timeout fails the deploy instead of losing its slot at run
        // time.
        HyperionAgentProperties.EffortProfileProperties draft = profile("Quick draft");
        draft.setMaxJobDuration(Duration.ofMinutes(12));
        HyperionAgentProperties.EffortProfileProperties thorough = profile("Thorough");
        thorough.setMaxJobDuration(Duration.ofMinutes(48));
        HyperionAgentProperties properties = propertiesWith(Map.of("draft", draft, "thorough", thorough));
        properties.setMaxJobDuration(Duration.ofMinutes(45));

        assertThat(new HyperionEffortProfileService(properties).longestMaxJobDuration()).isEqualTo(Duration.ofMinutes(48));
    }

    @Test
    void longestMaxJobDuration_isNotLoweredByAProfileWithAShorterDeadline() {
        // A profile narrows what one run is given, never what the deployment must tolerate, so the timeout validated against this must not shrink.
        HyperionAgentProperties.EffortProfileProperties draft = profile("Quick draft");
        draft.setMaxJobDuration(Duration.ofMinutes(12));
        HyperionAgentProperties properties = propertiesWith(Map.of("draft", draft));
        properties.setMaxJobDuration(Duration.ofMinutes(45));

        assertThat(new HyperionEffortProfileService(properties).longestMaxJobDuration()).isEqualTo(Duration.ofMinutes(45));
    }

    @Test
    void nonPositiveProfileJobDuration_failsStartup() {
        HyperionAgentProperties.EffortProfileProperties zero = profile("Zero");
        zero.setMaxJobDuration(Duration.ZERO);
        HyperionAgentProperties.EffortProfileProperties negative = profile("Negative");
        negative.setMaxJobDuration(Duration.ofMinutes(-5));

        assertThatIllegalArgumentException().isThrownBy(() -> new HyperionEffortProfileService(propertiesWith(Map.of("zero", zero)))).withMessageContaining("max-job-duration");
        assertThatIllegalArgumentException().isThrownBy(() -> new HyperionEffortProfileService(propertiesWith(Map.of("negative", negative))))
                .withMessageContaining("max-job-duration");
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = { "staged", "context", "window", "duration" })
    void changingAnEngineSettingPreventsReusingDeploymentSingletons(String field) {
        var custom = profile("Custom");
        switch (field) {
            case "staged" -> custom.setStagedGeneration(false);
            case "context" -> custom.setStagedContext("FRESH");
            case "window" -> custom.setContextWindowTokens(64000);
            case "duration" -> custom.setMaxJobDuration(Duration.ofMinutes(20));
            default -> throw new AssertionError(field);
        }
        var settings = new HyperionEffortProfileService(propertiesWith(Map.of("custom", custom))).resolve("custom");
        assertThat(settings.engineDefaults()).isFalse();
        assertThat(settings.providerOptionsOverride()).isFalse();
        assertThat(settings.chatOptions()).isNull();
        switch (field) {
            case "staged" -> assertThat(settings.stagedGeneration()).isFalse();
            case "context" -> assertThat(settings.stagedContext()).isEqualTo("FRESH");
            case "window" -> assertThat(settings.contextWindowTokens()).isEqualTo(64000);
            case "duration" -> assertThat(settings.maxJobDuration()).isEqualTo(Duration.ofMinutes(20));
            default -> throw new AssertionError(field);
        }
    }

    @Test
    void trimmedLabel_isPublished() {
        var custom = profile("  Detailed  ");
        assertThat(new HyperionEffortProfileService(propertiesWith(Map.of("custom", custom))).resolve("custom").label()).isEqualTo("Detailed");
    }

    @Test
    void blankDefaultNameUsesTheDeploymentDefaultAndShortestProfileDeadlineIsReported() {
        var shortProfile = profile(null);
        shortProfile.setMaxJobDuration(Duration.ofMinutes(5));
        var properties = propertiesWith(Map.of("quick", shortProfile));
        properties.setDefaultProfile(null);
        var service = new HyperionEffortProfileService(properties);
        assertThat(service.resolve(" ").name()).isEmpty();
        assertThat(service.shortestMaxJobDuration()).isEqualTo(Duration.ofMinutes(5));
        assertThat(service.selectableProfiles()).containsExactly(new ExerciseGenerationEffortProfileDTO("quick", "quick"));
        shortProfile.setMaxJobDuration(Duration.ofHours(2));
        assertThat(new HyperionEffortProfileService(properties).shortestMaxJobDuration()).isEqualTo(properties.getMaxJobDuration());
        assertThat(new HyperionEffortProfileService(agentProperties()).shortestMaxJobDuration()).isEqualTo(Duration.ofMinutes(45));
    }

}
