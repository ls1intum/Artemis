package de.tum.cit.aet.artemis.iris.domain.settings;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

class IrisCourseSettingsDTOTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void of_trimsBlankInstructionsAndDefaultsVariant() {
        var dto = IrisCourseSettingsDTO.of(true, "  trimmed text  ", null, null);

        assertThat(dto.customInstructions()).isEqualTo("trimmed text");
        assertThat(dto.variant()).isEqualTo(IrisPipelineVariant.DEFAULT);
        // null rateLimit is preserved (means "use defaults"), not converted to empty()
        assertThat(dto.rateLimit()).isNull();
    }

    @Test
    void of_convertsEmptyInstructionsToNull() {
        var dto = IrisCourseSettingsDTO.of(true, "   ", IrisPipelineVariant.ADVANCED, IrisRateLimitConfiguration.empty());

        assertThat(dto.customInstructions()).isNull();
        assertThat(dto.variant()).isEqualTo(IrisPipelineVariant.ADVANCED);
        assertThat(dto.rateLimit()).isEqualTo(IrisRateLimitConfiguration.empty());
    }

    @Test
    void jsonRoundtrip_preservesSanitizedPayload() throws JsonProcessingException {
        var original = IrisCourseSettingsDTO.of(false, "  trimmed text  ", IrisPipelineVariant.ADVANCED, new IrisRateLimitConfiguration(10, 5));

        String serialized = objectMapper.writeValueAsString(original);
        var deserialized = objectMapper.readValue(serialized, IrisCourseSettingsDTO.class);

        assertThat(deserialized.enabled()).isFalse();
        assertThat(deserialized.customInstructions()).isEqualTo("trimmed text");
        assertThat(deserialized.variant()).isEqualTo(IrisPipelineVariant.ADVANCED);
        assertThat(deserialized.rateLimit()).isEqualTo(new IrisRateLimitConfiguration(10, 5));
    }

    @Test
    void rateLimitConfiguration_detectsOverrides() {
        var empty = IrisRateLimitConfiguration.empty();
        assertThat(empty.hasOverride()).isFalse();

        assertThat(new IrisRateLimitConfiguration(1, null).hasOverride()).isTrue();
        assertThat(new IrisRateLimitConfiguration(null, 4).hasOverride()).isTrue();
    }
}
