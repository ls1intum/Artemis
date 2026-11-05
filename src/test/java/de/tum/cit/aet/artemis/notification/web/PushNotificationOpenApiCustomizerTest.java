package de.tum.cit.aet.artemis.notification.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.notification.config.PushNotificationOpenApiCustomizer;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;

class PushNotificationOpenApiCustomizerTest {

    @ParameterizedTest
    @ValueSource(strings = { "DTO", "" })
    void shouldCompleteOnlyNullablePushBodyEnumsBeforeOrAfterSuffixProcessing(String suffix) {
        var apiType = enumSchema(true, "DEFAULT", "IOS_V2");
        var deviceType = enumSchema(true, "FIREBASE", "APNS");
        var unrelated = enumSchema(true, "OTHER");
        var nonnull = enumSchema(false, "FIREBASE", "APNS");
        var api = new OpenAPI()
                .components(new Components().addSchemas("PushNotificationRegisterBody" + suffix, new Schema<>().addProperty("apiType", apiType).addProperty("deviceType", nonnull))
                        .addSchemas("PushNotificationUnregisterRequest" + suffix, new Schema<>().addProperty("deviceType", deviceType))
                        .addSchemas("UnrelatedDTO", new Schema<>().addProperty("apiType", unrelated)));
        var customizer = new PushNotificationOpenApiCustomizer();
        customizer.customise(api);
        customizer.customise(api);
        assertThat(apiType.getEnum()).containsExactly("DEFAULT", "IOS_V2", null);
        assertThat(deviceType.getEnum()).containsExactly("FIREBASE", "APNS", null);
        assertThat(nonnull.getEnum()).containsExactly("FIREBASE", "APNS");
        assertThat(unrelated.getEnum()).containsExactly("OTHER");
    }

    @Test
    void shouldLeaveAbsentSchemasAndPropertiesAlone() {
        var customizer = new PushNotificationOpenApiCustomizer();
        customizer.customise(new OpenAPI());
        customizer.customise(new OpenAPI().components(new Components().addSchemas("PushNotificationRegisterBody", new Schema<>())));
        customizer.customise(new OpenAPI().components(new Components().addSchemas("PushNotificationUnregisterRequest", new Schema<>().addProperty("deviceType", new Schema<>()))));
    }

    private static Schema<String> enumSchema(boolean nullable, String... values) {
        var schema = new Schema<String>();
        schema.setTypes(nullable ? Set.of("string", "null") : Set.of("string"));
        schema.setEnum(new ArrayList<>(List.of(values)));
        return schema;
    }
}
