package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.hyperion.config.HyperionAgentProperties;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEffortProfileDTO;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.profile.HyperionGenerationSettings;

/**
 * The admin-defined half of "admins define, users select". Every test here pins a rule that decides what a run costs, so a silent regression in any of them is a billing or
 * comparability defect rather than a cosmetic one.
 */
class HyperionEffortProfileServiceTest {

    private static final OpenAiChatOptions MODEL_DEFAULTS = OpenAiChatOptions.builder().model("deployment-model").temperature(1.0).maxCompletionTokens(8_192).build();

    private static HyperionAgentProperties.EffortProfileProperties profile(String label) {
        HyperionAgentProperties.EffortProfileProperties profile = new HyperionAgentProperties.EffortProfileProperties();
        profile.setLabel(label);
        return profile;
    }

    private static HyperionAgentProperties propertiesWith(Map<String, HyperionAgentProperties.EffortProfileProperties> profiles) {
        HyperionAgentProperties properties = new HyperionAgentProperties();
        properties.setProfiles(new LinkedHashMap<>(profiles));
        return properties;
    }

    @Test
    void withoutConfiguredProfiles_everyRunUsesTheDeploymentDefaultAndNoneIsSelectable() {
        HyperionAgentProperties properties = new HyperionAgentProperties();
        properties.setMaxTurns(60);
        properties.setMaxJobDuration(Duration.ofMinutes(45));

        HyperionEffortProfileService service = new HyperionEffortProfileService(properties, MODEL_DEFAULTS);

        assertThat(service.selectableProfiles()).isEmpty();
        HyperionGenerationSettings resolved = service.resolve(null);
        assertThat(resolved.name()).isEmpty();
        assertThat(resolved.maxTurns()).isEqualTo(60);
        assertThat(resolved.maxJobDuration()).isEqualTo(Duration.ofMinutes(45));
        // The engine must reuse the shared singletons, or a deployment that configured nothing would silently start deriving per-run objects.
        assertThat(resolved.engineDefaults()).isTrue();
        assertThat(resolved.chatOptions()).isNull();
    }

    @Test
    void unknownProfileName_isRejectedRatherThanSilentlyDefaulted() {
        HyperionEffortProfileService service = new HyperionEffortProfileService(propertiesWith(Map.of("standard", profile("Standard"))), MODEL_DEFAULTS);

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> service.resolve("thorough"))
                .satisfies(exception -> assertThat(exception.getBody().getProperties()).containsEntry("message", "error.unknownEffortProfile"));
    }

    @Test
    void profileWithNoConfiguredName_isRejectedEvenWhenAnotherProfileExists() {
        // A deployment that configures profiles at all must not accept a name outside the set it published; otherwise the endpoint's set and the accepted set diverge.
        HyperionEffortProfileService service = new HyperionEffortProfileService(propertiesWith(Map.of("draft", profile("Quick draft"))), MODEL_DEFAULTS);

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> service.resolve("   draft-typo "));
        assertThat(service.resolve(" draft ").name()).isEqualTo("draft");
    }

    @Test
    void unsetProfileValues_inheritTheDeploymentDefault() {
        HyperionAgentProperties properties = new HyperionAgentProperties();
        properties.setMaxTurns(60);
        properties.setMaxJobDuration(Duration.ofMinutes(45));
        properties.setMaxTokensPerJob(3_000_000L);
        properties.setContextWindowTokens(128_000);
        properties.setProfiles(new LinkedHashMap<>(Map.of("standard", profile("Standard"))));

        HyperionGenerationSettings standard = new HyperionEffortProfileService(properties, MODEL_DEFAULTS).resolve("standard");

        assertThat(standard.maxTurns()).isEqualTo(60);
        assertThat(standard.maxJobDuration()).isEqualTo(Duration.ofMinutes(45));
        assertThat(standard.maxTokensPerJob()).isEqualTo(3_000_000L);
        assertThat(standard.contextWindowTokens()).isEqualTo(128_000);
        // A profile that changes nothing must still be recognisable as the deployment default, so it costs no per-run object graph.
        assertThat(standard.engineDefaults()).isTrue();
    }

    @Test
    void profileWithAModel_prebuildsOpenAiOptionsThatOverlayTheModelBeanDefaults() {
        HyperionAgentProperties.EffortProfileProperties draft = profile("Quick draft");
        draft.setModel("draft-model");
        draft.setReasoningEffort("low");
        draft.setMaxTurns(20);
        draft.setMaxJobDuration(Duration.ofMinutes(12));
        draft.setMaxTokensPerJob(600_000L);

        HyperionGenerationSettings settings = new HyperionEffortProfileService(propertiesWith(Map.of("draft", draft)), MODEL_DEFAULTS).resolve("draft");

        // Must be OpenAiChatOptions and not a generic ChatOptions: OpenAiChatModel#buildRequestPrompt casts the runtime options.
        assertThat(settings.chatOptions()).isInstanceOf(OpenAiChatOptions.class);
        assertThat(settings.chatOptions().getModel()).isEqualTo("draft-model");
        assertThat(settings.chatOptions().getReasoningEffort()).isEqualTo("low");
        // Unstated decoding parameters are inherited, not reset: a profile states only what it changes.
        assertThat(settings.chatOptions().getTemperature()).isEqualTo(1.0);
        assertThat(settings.chatOptions().getMaxCompletionTokens()).isEqualTo(8_192);
        assertThat(settings.providerOptionsOverride()).isTrue();
        assertThat(settings.engineDefaults()).isFalse();
        assertThat(settings.maxTurns()).isEqualTo(20);
        assertThat(settings.maxTokensPerJob()).isEqualTo(600_000L);
    }

    @Test
    void withoutAConfiguredChatModel_aProfileStillBuildsUsableOptions() {
        HyperionAgentProperties.EffortProfileProperties draft = profile("Quick draft");
        draft.setModel("draft-model");

        HyperionGenerationSettings settings = new HyperionEffortProfileService(propertiesWith(Map.of("draft", draft)), (ChatOptions) null).resolve("draft");

        assertThat(settings.chatOptions()).isNotNull();
        assertThat(settings.chatOptions().getModel()).isEqualTo("draft-model");
    }

    @Test
    void reasoningEffortNone_isAcceptedByTheOpenAiOptionsBuilder() {
        HyperionAgentProperties.EffortProfileProperties draft = profile("Quick draft");
        draft.setReasoningEffort("none");

        HyperionGenerationSettings settings = new HyperionEffortProfileService(propertiesWith(Map.of("draft", draft)), MODEL_DEFAULTS).resolve("draft");

        assertThat(((OpenAiChatOptions) settings.chatOptions()).getReasoningEffort()).isEqualTo("none");
    }

    @Test
    void selectableProfiles_exposeNameAndLabelOnlyAndKeepConfiguredOrder() {
        // Six entries rather than three: the order-losing implementation (Map.copyOf) randomizes its iteration order per JVM via ImmutableCollections' salt, so a short list
        // can agree with the configured order by chance. The order matters because this list is instructor-facing and must not reshuffle itself between restarts.
        LinkedHashMap<String, HyperionAgentProperties.EffortProfileProperties> profiles = new LinkedHashMap<>();
        HyperionAgentProperties.EffortProfileProperties draft = profile("Quick draft");
        draft.setModel("secret-procurement-model");
        profiles.put("draft", draft);
        profiles.put("standard", profile("Standard"));
        profiles.put("thorough", profile("Thorough"));
        profiles.put("exhaustive", profile("Exhaustive"));
        profiles.put("experimental", profile("Experimental"));
        profiles.put("unlabelled", new HyperionAgentProperties.EffortProfileProperties());

        List<ExerciseGenerationEffortProfileDTO> selectable = new HyperionEffortProfileService(propertiesWith(profiles), MODEL_DEFAULTS).selectableProfiles();

        assertThat(selectable).extracting(ExerciseGenerationEffortProfileDTO::name).containsExactlyElementsOf(profiles.keySet());
        // No model id may reach an instructor through this endpoint, and an unlabelled profile falls back to its key rather than to null.
        assertThat(selectable).extracting(ExerciseGenerationEffortProfileDTO::label).contains("Quick draft", "unlabelled").doesNotContain("secret-procurement-model");
        assertThat(new ObjectMapper().valueToTree(selectable).toString()).doesNotContain("model", "reasoning", "maxTokens", "maxTurns");
    }

    @Test
    void defaultProfile_selectsTheNamedProfileForRequestsThatNameNone() {
        HyperionAgentProperties.EffortProfileProperties thorough = profile("Thorough");
        thorough.setMaxTurns(90);
        HyperionAgentProperties properties = propertiesWith(Map.of("thorough", thorough));
        properties.setDefaultProfile("thorough");

        assertThat(new HyperionEffortProfileService(properties, MODEL_DEFAULTS).resolve(null).maxTurns()).isEqualTo(90);
    }

    @Test
    void defaultProfileNamingAnUnconfiguredProfile_failsStartup() {
        HyperionAgentProperties properties = propertiesWith(Map.of("draft", profile("Quick draft")));
        properties.setDefaultProfile("thorough");

        assertThatIllegalArgumentException().isThrownBy(() -> new HyperionEffortProfileService(properties, MODEL_DEFAULTS)).withMessageContaining("default-profile");
    }

    @Test
    void invalidStagedContext_failsStartupRatherThanTheFirstRunThatUsesIt() {
        HyperionAgentProperties.EffortProfileProperties draft = profile("Quick draft");
        draft.setStagedContext("SOMETIMES");

        assertThatIllegalArgumentException().isThrownBy(() -> new HyperionEffortProfileService(propertiesWith(Map.of("draft", draft)), MODEL_DEFAULTS))
                .withMessageContaining("staged-context");
    }

    @Test
    void nonPositiveProfileBudgets_failStartup() {
        HyperionAgentProperties.EffortProfileProperties broken = profile("Broken");
        broken.setMaxTurns(0);

        assertThatIllegalArgumentException().isThrownBy(() -> new HyperionEffortProfileService(propertiesWith(Map.of("broken", broken)), MODEL_DEFAULTS))
                .withMessageContaining("max-turns");
    }

    static Stream<Arguments> invalidProviderOptions() {
        return Stream.of(Arguments.of("blank model", (Consumer<HyperionAgentProperties.EffortProfileProperties>) profile -> profile.setModel(" "), "model"),
                Arguments.of("completion tokens", (Consumer<HyperionAgentProperties.EffortProfileProperties>) profile -> profile.setMaxCompletionTokens(0),
                        "max-completion-tokens"),
                Arguments.of("temperature", (Consumer<HyperionAgentProperties.EffortProfileProperties>) profile -> profile.setTemperature(Double.NaN), "temperature"),
                Arguments.of("top-p", (Consumer<HyperionAgentProperties.EffortProfileProperties>) profile -> profile.setTopP(1.1), "top-p"),
                Arguments.of("reasoning", (Consumer<HyperionAgentProperties.EffortProfileProperties>) profile -> profile.setReasoningEffort("unbounded"), "reasoning-effort"),
                Arguments.of("verbosity", (Consumer<HyperionAgentProperties.EffortProfileProperties>) profile -> profile.setVerbosity("verbose"), "verbosity"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidProviderOptions")
    void invalidProviderOptionsFailAtStartup(String scenario, Consumer<HyperionAgentProperties.EffortProfileProperties> configure, String expectedMessage) {
        HyperionAgentProperties.EffortProfileProperties broken = profile("Broken");
        configure.accept(broken);

        assertThatIllegalArgumentException().as(scenario).isThrownBy(() -> new HyperionEffortProfileService(propertiesWith(Map.of("broken", broken)), MODEL_DEFAULTS))
                .withMessageContaining(expectedMessage);
    }

    @Test
    void largestMaxTokensPerJob_isTheMaximumOverTheDefaultAndEveryProfile() {
        HyperionAgentProperties.EffortProfileProperties draft = profile("Quick draft");
        draft.setMaxTokensPerJob(600_000L);
        HyperionAgentProperties.EffortProfileProperties thorough = profile("Thorough");
        thorough.setMaxTokensPerJob(6_000_000L);
        HyperionAgentProperties properties = propertiesWith(Map.of("draft", draft, "thorough", thorough));
        properties.setMaxTokensPerJob(3_000_000L);

        assertThat(new HyperionEffortProfileService(properties, MODEL_DEFAULTS).largestMaxTokensPerJob()).isEqualTo(6_000_000L);
    }

    @Test
    void largestMaxTokensPerJob_fallsBackToTheDeploymentDefaultWhenEveryProfileIsSmaller() {
        HyperionAgentProperties.EffortProfileProperties draft = profile("Quick draft");
        draft.setMaxTokensPerJob(600_000L);
        HyperionAgentProperties properties = propertiesWith(Map.of("draft", draft));
        properties.setMaxTokensPerJob(3_000_000L);

        assertThat(new HyperionEffortProfileService(properties, MODEL_DEFAULTS).largestMaxTokensPerJob()).isEqualTo(3_000_000L);
    }
}
