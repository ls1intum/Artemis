package de.tum.cit.aet.artemis.notification.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;

/** Completes nullable JSON enum metadata without changing native request validation or query binding. */
@Component
@Profile(PROFILE_CORE)
@Lazy
public class PushNotificationOpenApiCustomizer implements OpenApiCustomizer {

    @Override
    public void customise(OpenAPI openApi) {
        if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
            return;
        }
        // Work on either side of DTO suffix processing. Query enums are intentionally not nullable JSON values.
        for (String suffix : List.of("DTO", "")) {
            addNullEnumValue(openApi.getComponents().getSchemas().get("PushNotificationRegisterBody" + suffix), "apiType");
            addNullEnumValue(openApi.getComponents().getSchemas().get("PushNotificationUnregisterRequest" + suffix), "deviceType");
        }
    }

    private static void addNullEnumValue(Schema<?> schema, String property) {
        if (schema == null || schema.getProperties() == null) {
            return;
        }
        Schema<?> value = schema.getProperties().get(property);
        if (value != null && value.getTypes() != null && value.getTypes().contains("null") && value.getEnum() != null && !value.getEnum().contains(null)) {
            value.addEnumItemObject(null);
        }
    }
}
