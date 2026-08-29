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
        assertThat(deserialized.customInstructions()).isEqualTo("trimmed text");
        assertThat(deserialized.variant()).isEqualTo(IrisPipelineVariant.ADVANCED);
        assertThat(deserialized.supportLevel()).isEqualTo(IrisSupportLevel.LOW);
        assertThat(deserialized.rateLimit()).isEqualTo(new IrisRateLimitConfiguration(10, 5));
    }

    /**
     * The three states of the legacy-trigger switch, and why the third one exists.
     * <p>
     * A settings row written before the field existed has no key, and a full PUT from one of the two clients that do
     * not edit the field omits it. Both deserialize to null, which must NOT be read as "off": every course behaved as
     * "on" before the field existed, and silently disabling Artemis' own proactive events for the whole installation
     * on upgrade would be the opposite of a no-op. This is the inverse of proactiveStruggleEnabled above, where absent
     * genuinely means off.
     */
    @Test
    void legacyBuildTriggers_absentKeyReadsAsOn() throws JsonProcessingException {
        var withoutKey = objectMapper.readValue("{\"enabled\":true}", IrisCourseSettings.class);

        assertThat(withoutKey.legacyBuildTriggersEnabled()).isNull();
        assertThat(withoutKey.legacyBuildTriggersEffective()).isTrue();
    }

    @Test
    void legacyBuildTriggers_explicitFalseSurvivesARoundTrip() throws JsonProcessingException {
        var off = IrisCourseSettings.of(true, null, null, null, null, false, false);

        var json = objectMapper.writeValueAsString(off);
        // The value has to reach the JSON: NON_EMPTY drops a null, and dropping an explicit false would turn the
        // admin's opt-out back into the default on the next read.
        assertThat(json).contains("\"legacyBuildTriggersEnabled\":false");

        var read = objectMapper.readValue(json, IrisCourseSettings.class);
        assertThat(read.legacyBuildTriggersEnabled()).isFalse();
        assertThat(read.legacyBuildTriggersEffective()).isFalse();
    }

    @Test
    void legacyBuildTriggers_undecidedIsNotSerialized() throws JsonProcessingException {
        var undecided = IrisCourseSettings.of(true, null, null, null, null, false, null);

        assertThat(objectMapper.writeValueAsString(undecided)).doesNotContain("legacyBuildTriggersEnabled");
    }

    @Test
    void legacyBuildTriggers_explicitNullIsIndistinguishableFromAnAbsentKey() throws JsonProcessingException {
        // Deliberate: both mean "this payload says nothing", and the update path merges the stored value for both.
        var explicitNull = objectMapper.readValue("{\"enabled\":true,\"legacyBuildTriggersEnabled\":null}", IrisCourseSettings.class);

        assertThat(explicitNull.legacyBuildTriggersEnabled()).isNull();
        assertThat(explicitNull.legacyBuildTriggersEffective()).isTrue();
    }

    @Test
    void legacyBuildTriggers_theShortFactoriesLeaveTheDecisionOpen() {
        assertThat(IrisCourseSettings.of(true, null, null, null, null).legacyBuildTriggersEnabled()).isNull();
        assertThat(IrisCourseSettings.of(true, null, null, null, null, true).legacyBuildTriggersEnabled()).isNull();
        assertThat(IrisCourseSettings.defaultSettings().legacyBuildTriggersEnabled()).isNull();
        assertThat(IrisCourseSettings.defaultSettings().legacyBuildTriggersEffective()).isTrue();
    }

    @Test
    void proactiveStruggle_defaultsOff_andRoundtripsWhenEnabled() throws JsonProcessingException {
        assertThat(IrisCourseSettings.of(true, null, null, null, null).proactiveStruggleEnabled()).isFalse();

        var enabled = IrisCourseSettings.of(true, null, IrisPipelineVariant.DEFAULT, null, null, true);
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
    void deserialization_withoutSupportLevel_defaultsToModerate() throws JsonProcessingException {
        var deserialized = objectMapper.readValue("{\"enabled\":true,\"variant\":\"default\"}", IrisCourseSettings.class);

        assertThat(deserialized.supportLevel()).isEqualTo(IrisSupportLevel.MODERATE);
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
