package de.tum.cit.aet.artemis.notification.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Set;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.notification.domain.NotificationChannelOption;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;

/** Describes enum-keyed channel maps as closed objects, retaining the different read and partial-write contracts. */
@Component
@Profile(PROFILE_CORE)
@Lazy
public class CourseNotificationSettingOpenApiCustomizer implements OpenApiCustomizer {

    @Override
    public void customise(OpenAPI openApi) {
        if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
            return;
        }
        // Support either side of the shared DTO-suffix transformation without ordering assumptions.
        for (String name : List.of("CourseNotificationSettingInfoDTO", "CourseNotificationSettingInfo", "CourseNotificationSettingSpecificationRequestDTO",
                "CourseNotificationSettingSpecificationRequest")) {
            Schema<?> schema = openApi.getComponents().getSchemas().get(name);
            if (schema != null && schema.getProperties() != null) {
                Schema<?> settings = schema.getProperties().get("notificationTypeChannels");
                if (settings != null) {
                    boolean response = name.startsWith("CourseNotificationSettingInfo");
                    var channels = new ObjectSchema();
                    channels.setAdditionalProperties(false);
                    channels.setDescription(response ? "All effective channel flags" : "Partial channel overrides; omitted or null values leave existing flags unchanged");
                    for (NotificationChannelOption option : NotificationChannelOption.values()) {
                        Schema<?> flag = new BooleanSchema();
                        if (response) {
                            channels.addRequiredItem(option.name());
                        }
                        else {
                            flag.setType(null);
                            flag.setTypes(Set.of("boolean", "null"));
                        }
                        channels.addProperty(option.name(), flag);
                    }
                    settings.setDescription("Notification type ids (decimal Short values) as JSON object keys");
                    settings.setAdditionalProperties(channels);
                }
            }
        }
    }
}
