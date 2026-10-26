package de.tum.cit.aet.artemis.notification.web;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import de.tum.cit.aet.artemis.notification.domain.GlobalNotificationType;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Checks the notification contracts exported by springdoc before client-input pruning can hide a missing schema.
 */
@TestPropertySource(properties = { "springdoc.api-docs.enabled=true", "springdoc.packages-to-scan=de.tum.cit.aet.artemis.notification.web",
        // Keep the remaining notification families excluded until their contracts and callers are migrated.
        "springdoc.paths-to-exclude=/api/notification/courses/**,/api/notification/system-notifications,/api/notification/system-notifications/**,/api/notification/admin/**,/api/notification/public/**,/api/notification/push_notification/**" })
class NotificationOpenApiContractTest extends AbstractSpringIntegrationIndependentTest {

    @Test
    void shouldExportBothGlobalSettingsOperationsWithAccurateSchemas() throws Exception {
        String read = "$.paths['/api/notification/global-notification-settings'].get";
        String update = "$.paths['/api/notification/global-notification-settings/{notificationType}'].put";
        String settings = "$.components.schemas.GlobalNotificationSetting";
        String input = "$.components.schemas.UpdateGlobalNotificationSetting";
        String[] notificationTypes = Arrays.stream(GlobalNotificationType.values()).map(Enum::name).toArray(String[]::new);

        request.performMvcRequest(MockMvcRequestBuilders.get("/api-docs")).andExpect(status().isOk()).andExpect(jsonPath("$.paths", aMapWithSize(2)))
                .andExpect(jsonPath(read + ".operationId").value("getGlobalNotificationSettings"))
                .andExpect(jsonPath(read + ".responses['200'].content['application/json'].schema.additionalProperties.type").value("boolean"))
                .andExpect(jsonPath(update + ".operationId").value("updateGlobalNotificationSetting")).andExpect(jsonPath(update + ".parameters[0].name").value("notificationType"))
                .andExpect(jsonPath(update + ".parameters[0].in").value("path")).andExpect(jsonPath(update + ".parameters[0].required").value(true))
                .andExpect(jsonPath(update + ".parameters[0].schema.enum", containsInAnyOrder(notificationTypes))).andExpect(jsonPath(update + ".requestBody.required").value(true))
                .andExpect(jsonPath(update + ".requestBody.content['application/json'].schema['$ref']").value("#/components/schemas/UpdateGlobalNotificationSetting"))
                .andExpect(jsonPath(update + ".responses['200'].content['application/json'].schema['$ref']").value("#/components/schemas/GlobalNotificationSetting"))
                .andExpect(jsonPath(settings + ".required", containsInAnyOrder("id", "userId", "notificationType", "enabled")))
                .andExpect(jsonPath(settings + ".properties.notificationType.enum", containsInAnyOrder(notificationTypes)))
                .andExpect(jsonPath(settings + ".properties.enabled.type").value("boolean")).andExpect(jsonPath(input + ".required").doesNotExist())
                .andExpect(jsonPath(input + ".properties.enabled.type", containsInAnyOrder("boolean", "null")))
                .andExpect(jsonPath(input + ".properties.enabled.default").value(false));
    }
}
