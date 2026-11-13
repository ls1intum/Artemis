package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseSettings;
import de.tum.cit.aet.artemis.iris.dto.IrisChatSessionCountDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisCourseSettingsDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisMessageContentResponseDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisRateLimitInformationDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisStatusDTO;
import de.tum.cit.aet.artemis.iris.service.IrisRateLimitService.IrisRateLimitInformation;

class IrisRestContractJsonTest {

    @Test
    void statusKeepsQuotaGetterAndPrimitiveZeroValues() {
        var mapper = JsonObjectMapper.get();
        var quota = new IrisRateLimitInformation(0, 0, 1);
        var status = new IrisStatusDTO(false, IrisRateLimitInformationDTO.of(quota));

        String quotaJson = mapper.writeValueAsString(quota);
        assertThat(quotaJson).contains("\"rateLimitExceeded\":true");
        assertThat(mapper.writeValueAsString(status)).isEqualTo("{\"active\":false,\"rateLimitInfo\":" + quotaJson + "}");
        assertThat(mapper.readTree(mapper.writeValueAsString(new IrisChatSessionCountDTO(0, 0)))).isEqualTo(mapper.readTree("{\"sessions\":0,\"messages\":0}"));
    }

    @Test
    void messageAttributesAreEmbeddedJson() {
        var mapper = JsonObjectMapper.get();
        var content = new IrisMessageContentResponseDTO(null, "json", null, "{\"selectedIndex\":0}");

        assertThat(mapper.readTree(mapper.writeValueAsString(content))).isEqualTo(mapper.readTree("{\"type\":\"json\",\"attributes\":{\"selectedIndex\":0}}"));
    }

    @Test
    void courseSettingsPreserveLegacyJsonAndDomainDefaults() {
        var mapper = JsonObjectMapper.get();
        String json = """
                {"enabled":false,"customInstructions":"  Instructor note  ","variant":"advanced","rateLimit":{"oldField":42},"proactiveStruggleEnabled":false,"oldField":true}
                """;
        var legacy = mapper.readValue(json, IrisCourseSettings.class);
        var dto = mapper.readValue(json, IrisCourseSettingsDTO.class);
        assertThat(dto.toDomain()).isEqualTo(legacy);
        assertThat(mapper.writeValueAsString(IrisCourseSettingsDTO.of(legacy))).isEqualTo(mapper.writeValueAsString(legacy))
                .contains("\"variant\":\"advanced\"", "\"supportLevel\":\"moderate\"", "\"rateLimit\":{}").doesNotContain("legacyBuildTriggersEnabled", "oldField");
        assertThat(mapper.writeValueAsString(IrisCourseSettingsDTO.of(IrisCourseSettings.defaultSettings()))).doesNotContain("rateLimit");
    }
}
