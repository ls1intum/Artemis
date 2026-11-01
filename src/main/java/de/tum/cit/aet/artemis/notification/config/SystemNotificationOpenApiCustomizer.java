package de.tum.cit.aet.artemis.notification.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;

/** Completes system notification schema metadata that cannot be expressed by static annotations alone. */
@Component
@Profile(PROFILE_CORE)
@Lazy
public class SystemNotificationOpenApiCustomizer implements OpenApiCustomizer {

    private final String applicationName;

    public SystemNotificationOpenApiCustomizer(@Value("${jhipster.clientApp.name}") String applicationName) {
        this.applicationName = applicationName;
    }

    @Override
    public void customise(OpenAPI openApi) {
        if (openApi.getComponents() != null && openApi.getComponents().getSchemas() != null) {
            // Work before or after the shared DTO-suffix transformation; both retain the same schema objects.
            for (String name : List.of("SystemNotificationUpdateDTO", "SystemNotificationUpdate")) {
                Schema<?> input = openApi.getComponents().getSchemas().get(name);
                if (input != null && input.getProperties() != null) {
                    Schema<?> type = input.getProperties().get("type");
                    // OpenAPI 3.1 requires null in the enum as well as in the type union to permit explicit null.
                    if (type != null && type.getTypes() != null && type.getTypes().contains("null") && type.getEnum() != null && !type.getEnum().contains(null)) {
                        type.addEnumItemObject(null);
                    }
                }
            }
        }
        if (openApi.getPaths() == null) {
            return;
        }
        openApi.getPaths().forEach((path, item) -> {
            if (path.equals("/api/notification/admin/system-notifications") || path.startsWith("/api/notification/admin/system-notifications/")) {
                item.readOperations().forEach(operation -> {
                    if (operation.getResponses() != null) {
                        operation.getResponses().values().forEach(response -> {
                            if (response.getHeaders() != null) {
                                for (String suffix : List.of("alert", "params")) {
                                    var header = response.getHeaders().remove("X-artemisApp-" + suffix);
                                    if (header != null) {
                                        response.addHeaderObject("X-" + applicationName + "-" + suffix, header);
                                    }
                                }
                            }
                        });
                    }
                });
            }
        });
    }
}
