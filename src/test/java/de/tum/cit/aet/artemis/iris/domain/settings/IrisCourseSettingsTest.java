package de.tum.cit.aet.artemis.iris.domain.settings;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;

class IrisCourseSettingsTest {

    private final ObjectMapper objectMapper = JsonObjectMapper.get();

    @Test
    void of_trimsBlankInstructionsAndDefaultsVariantAndSupportLevel() {
        var dto = IrisCourseSettings.of(true, "  trimmed text  ", null, null, null);

        assertThat(dto.customInstructions()).isEqualTo("trimmed text");
        assertThat(dto.promptingModeEnabled()).isTrue();
        assertThat(dto.promptingModeSettings()).isEqualTo(IrisPromptingModeSettings.defaultSettings());
        assertThat(dto.variant()).isEqualTo(IrisPipelineVariant.DEFAULT);
        assertThat(dto.supportLevel()).isEqualTo(IrisSupportLevel.MODERATE);
        // null rateLimit is preserved (means "use defaults"), not converted to empty()
        assertThat(dto.rateLimit()).isNull();
    }

    @Test
    void of_convertsEmptyInstructionsToNull() {
        var dto = IrisCourseSettings.of(true, "   ", IrisPipelineVariant.ADVANCED, IrisSupportLevel.HIGH, IrisRateLimitConfiguration.empty());

        assertThat(dto.customInstructions()).isNull();
        assertThat(dto.variant()).isEqualTo(IrisPipelineVariant.ADVANCED);
        assertThat(dto.supportLevel()).isEqualTo(IrisSupportLevel.HIGH);
        assertThat(dto.rateLimit()).isEqualTo(IrisRateLimitConfiguration.empty());
    }

    @Test
    void jsonRoundtrip_preservesSanitizedPayload() throws JsonProcessingException {
        var original = IrisCourseSettings.of(false, "  trimmed text  ", IrisPipelineVariant.ADVANCED, IrisSupportLevel.LOW, new IrisRateLimitConfiguration(10, 5));

        String serialized = objectMapper.writeValueAsString(original);
        var deserialized = objectMapper.readValue(serialized, IrisCourseSettings.class);

        assertThat(deserialized.enabled()).isFalse();
        assertThat(deserialized.promptingModeEnabled()).isTrue();
        assertThat(deserialized.promptingModeSettings()).isEqualTo(IrisPromptingModeSettings.defaultSettings());
        assertThat(deserialized.customInstructions()).isEqualTo("trimmed text");
        assertThat(deserialized.variant()).isEqualTo(IrisPipelineVariant.ADVANCED);
        assertThat(deserialized.supportLevel()).isEqualTo(IrisSupportLevel.LOW);
        assertThat(deserialized.rateLimit()).isEqualTo(new IrisRateLimitConfiguration(10, 5));
    }

    @Test
    void deserialization_withoutSupportLevel_defaultsToModerate() throws JsonProcessingException {
        var deserialized = objectMapper.readValue("{\"enabled\":true,\"variant\":\"default\"}", IrisCourseSettings.class);

        assertThat(deserialized.supportLevel()).isEqualTo(IrisSupportLevel.MODERATE);
    }

    @Test
    void deserialization_withoutPromptingModeEnabled_defaultsToEnabled() throws JsonProcessingException {
        var deserialized = objectMapper.readValue("{\"enabled\":true,\"variant\":\"default\"}", IrisCourseSettings.class);

        assertThat(deserialized.promptingModeEnabled()).isTrue();
    }

    @Test
    void deserialization_withoutPromptingModeSettings_defaultsToDefaultQuizSettings() throws JsonProcessingException {
        var deserialized = objectMapper.readValue("{\"enabled\":true,\"variant\":\"default\"}", IrisCourseSettings.class);

        assertThat(deserialized.promptingModeSettings()).isEqualTo(IrisPromptingModeSettings.defaultSettings());
    }

    @Test
    void deserialization_withPromptingModeSettings_isPreserved() throws JsonProcessingException {
        var deserialized = objectMapper.readValue(
                "{\"enabled\":true,\"promptingModeSettings\":{\"minQuestions\":2,\"maxQuestions\":7,\"timeLimitQuestion\":45,\"timeLimitInClass\":20},\"variant\":\"default\"}",
                IrisCourseSettings.class);

        assertThat(deserialized.promptingModeSettings()).isEqualTo(new IrisPromptingModeSettings(2, 7, 45, 20));
    }

    @Test
    void deserialization_withPromptingModeDisabled_isPreserved() throws JsonProcessingException {
        var deserialized = objectMapper.readValue("{\"enabled\":true,\"promptingModeEnabled\":false,\"variant\":\"default\"}", IrisCourseSettings.class);

        assertThat(deserialized.promptingModeEnabled()).isFalse();
    }

    @Test
    void deserialization_withHighSupportLevel_isPreserved() throws JsonProcessingException {
        var deserialized = objectMapper.readValue("{\"enabled\":true,\"variant\":\"default\",\"supportLevel\":\"high\"}", IrisCourseSettings.class);

        assertThat(deserialized.supportLevel()).isEqualTo(IrisSupportLevel.HIGH);
    }

    @Test
    void rateLimitConfiguration_detectsOverrides() {
        var empty = IrisRateLimitConfiguration.empty();
        assertThat(empty.hasOverride()).isFalse();

        assertThat(new IrisRateLimitConfiguration(1, null).hasOverride()).isTrue();
        assertThat(new IrisRateLimitConfiguration(null, 4).hasOverride()).isTrue();
    }
}
