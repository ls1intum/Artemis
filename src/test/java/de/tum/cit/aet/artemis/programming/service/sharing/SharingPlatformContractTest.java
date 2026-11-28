package de.tum.cit.aet.artemis.programming.service.sharing;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.core.util.JsonObjectMapper;

class SharingPlatformContractTest {

    private final JsonMapper mapper = JsonObjectMapper.get();

    @Test
    void preservesThePublishedBasketShapeWhenForwardingIt() throws IOException {
        String legacyResponse = fixture("basket.json");
        var basket = mapper.readValue(legacyResponse, ShoppingBasket.class);
        var exercise = basket.exerciseInfo().getFirst();

        assertThat(basket.tokenValidUntil()).isEqualTo(1760000000000L);
        assertThat(exercise.project().id()).isEqualTo(42);
        assertThat(exercise.project().visibility()).isEqualTo(ShoppingBasket.ProjectVisibility.INTERNAL);
        assertThat(exercise.metadata().title()).isEqualTo("Wörter und Listen");
        assertThat(mapper.readTree(mapper.writeValueAsString(basket))).isEqualTo(mapper.readTree(legacyResponse));
    }

    @Test
    void supportsLegacyTimestampsAndOmittedActions() {
        var basket = mapper.readValue("""
                {"exerciseInfo":[
                    {"project":{"last_activity_at":"2025-10-02T11:22:33.123"},"file":{"indexing_date":[2025,10,2,11,22,33,123]}},
                    {"supportedActions":null}
                ]}
                """, ShoppingBasket.class);
        var first = basket.exerciseInfo().getFirst();

        assertThat(first.project().lastActivityAt()).isEqualTo(Instant.parse("2025-10-02T11:22:33.123Z"));
        assertThat(first.file().indexingDate()).isEqualTo(Instant.parse("2025-10-02T11:22:33.000000123Z"));
        assertThat(first.supportedActions()).isEmpty();
        assertThat(basket.exerciseInfo().getLast().supportedActions()).isNull();
    }

    @Test
    void advertisesThePublishedPluginConfigurationShape() throws IOException {
        var config = new SharingPluginConfig("Artemis", List.of(new SharingPluginConfig.Action("import", "https://artemis.example.org/sharingImport", "Export to Artemis", null)));
        assertThat(mapper.readTree(mapper.writeValueAsString(config))).isEqualTo(mapper.readTree(fixture("plugin.json")));
    }

    private static String fixture(String name) throws IOException {
        // These fixtures were serialized with SharingPluginPlatformAPI 1.2.1.
        try (var stream = SharingPlatformContractTest.class.getResourceAsStream("/test-data/sharing-platform/" + name)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
