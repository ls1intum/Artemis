package de.tum.cit.aet.artemis.iris.domain.settings;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;

class IrisCourseSettingsTest {

    private final ObjectMapper objectMapper = JsonObjectMapper.get();

    @Test
    void of_trimsBlankInstructionsAndDefaultsVariant() {
        var dto = IrisCourseSettings.of(true, "  trimmed text  ", null, null);

        assertThat(dto.customInstructions()).isEqualTo("trimmed text");
        assertThat(dto.variant()).isEqualTo(IrisPipelineVariant.DEFAULT);
        // null rateLimit is preserved (means "use defaults"), not converted to empty()
        assertThat(dto.rateLimit()).isNull();
    }

    @Test
    void of_convertsEmptyInstructionsToNull() {
        var dto = IrisCourseSettings.of(true, "   ", IrisPipelineVariant.ADVANCED, IrisRateLimitConfiguration.empty());

        assertThat(dto.customInstructions()).isNull();
        assertThat(dto.variant()).isEqualTo(IrisPipelineVariant.ADVANCED);
        assertThat(dto.rateLimit()).isEqualTo(IrisRateLimitConfiguration.empty());
    }

    @Test
    void jsonRoundtrip_preservesSanitizedPayload() throws JsonProcessingException {
        var original = IrisCourseSettings.of(false, "  trimmed text  ", IrisPipelineVariant.ADVANCED, new IrisRateLimitConfiguration(10, 5));

        String serialized = objectMapper.writeValueAsString(original);
        var deserialized = objectMapper.readValue(serialized, IrisCourseSettings.class);

        assertThat(deserialized.enabled()).isFalse();
        assertThat(deserialized.customInstructions()).isEqualTo("trimmed text");
        assertThat(deserialized.variant()).isEqualTo(IrisPipelineVariant.ADVANCED);
        assertThat(deserialized.rateLimit()).isEqualTo(new IrisRateLimitConfiguration(10, 5));
    }

    @Test
    void proactiveStruggle_defaultsOff_andRoundtripsWhenEnabled() throws JsonProcessingException {
        assertThat(IrisCourseSettings.of(true, null, null, null).proactiveStruggleEnabled()).isFalse();

        var enabled = IrisCourseSettings.of(true, null, IrisPipelineVariant.DEFAULT, null, true);
        var json = objectMapper.writeValueAsString(enabled);
        assertThat(objectMapper.readValue(json, IrisCourseSettings.class).proactiveStruggleEnabled()).isTrue();
    }

    @Test
    void proactiveStruggle_legacyRowWithoutKey_deserializesOff() throws JsonProcessingException {
        // A course persisted before this field existed has no proactiveStruggleEnabled key; the primitive boolean
        // must deserialize to false so existing courses stay off until an admin opts them in (spec §13). This is the
        // actual default-off guarantee (independent of how a false is serialized on the way back out).
        var legacyJson = "{\"enabled\":true,\"variant\":\"default\"}";
        assertThat(objectMapper.readValue(legacyJson, IrisCourseSettings.class).proactiveStruggleEnabled()).isFalse();
    }

    @Test
    void rateLimitConfiguration_detectsOverrides() {
        var empty = IrisRateLimitConfiguration.empty();
        assertThat(empty.hasOverride()).isFalse();

        assertThat(new IrisRateLimitConfiguration(1, null).hasOverride()).isTrue();
        assertThat(new IrisRateLimitConfiguration(null, 4).hasOverride()).isTrue();
    }
}
