package de.tum.cit.aet.artemis.notification.web;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
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
        "springdoc.paths-to-exclude=/api/notification/courses/**,/api/notification/push_notification/**" })
class NotificationOpenApiContractTest extends AbstractSpringIntegrationIndependentTest {

    @Test
    void shouldExportBothGlobalSettingsOperationsWithAccurateSchemas() throws Exception {
        String read = "$.paths['/api/notification/global-notification-settings'].get";
        String update = "$.paths['/api/notification/global-notification-settings/{notificationType}'].put";
        String settings = "$.components.schemas.GlobalNotificationSetting";
        String input = "$.components.schemas.UpdateGlobalNotificationSetting";
        String[] notificationTypes = Arrays.stream(GlobalNotificationType.values()).map(Enum::name).toArray(String[]::new);

        request.performMvcRequest(MockMvcRequestBuilders.get("/api-docs")).andExpect(status().isOk()).andExpect(jsonPath("$.paths", aMapWithSize(8)))
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

    @Test
    void shouldExportAllSevenSystemOperationsWithRealHeadersStatusesAndPageableParameters() throws Exception {
        String list = "$.paths['/api/notification/system-notifications'].get";
        String find = "$.paths['/api/notification/system-notifications/{notificationId}'].get";
        String active = "$.paths['/api/notification/public/system-notifications/active'].get";
        String create = "$.paths['/api/notification/admin/system-notifications'].post";
        String update = "$.paths['/api/notification/admin/system-notifications'].put";
        String delete = "$.paths['/api/notification/admin/system-notifications/{notificationId}'].delete";
        String count = "$.paths['/api/notification/admin/system-notifications/maintenance-recipients-count'].get";
        String response = "$.components.schemas.SystemNotification";
        String input = "$.components.schemas.SystemNotificationUpdate";
        request.performMvcRequest(MockMvcRequestBuilders.get("/api-docs")).andExpect(status().isOk()).andExpect(jsonPath("$.paths", aMapWithSize(8)))
                .andExpect(jsonPath(list + ".operationId").value("getAllSystemNotifications"))
                .andExpect(jsonPath(list + ".parameters[*].name", containsInAnyOrder("page", "size", "sort"))).andExpect(jsonPath(list + ".parameters[0].schema.default").value(0))
                .andExpect(jsonPath(list + ".parameters[1].schema.default").value(20)).andExpect(jsonPath(list + ".parameters[2].schema.type").value("array"))
                .andExpect(jsonPath(list + ".responses['200'].content['application/json'].schema.type").value("array"))
                .andExpect(jsonPath(list + ".responses['200'].content['application/json'].schema.items['$ref']").value("#/components/schemas/SystemNotification"))
                .andExpect(jsonPath(list + ".responses['200'].headers.Link.schema.type").value("string"))
                .andExpect(jsonPath(list + ".responses['200'].headers['X-Total-Count'].schema.type").value("integer"))
                .andExpect(jsonPath(find + ".operationId").value("getSystemNotification"))
                .andExpect(jsonPath(find + ".responses['200'].content['application/json'].schema['$ref']").value("#/components/schemas/SystemNotification"))
                .andExpect(jsonPath(find + ".responses['404']").exists()).andExpect(jsonPath(find + ".responses['200'].headers").doesNotExist())
                .andExpect(jsonPath(active + ".operationId").value("getActiveAndFutureSystemNotifications"))
                .andExpect(jsonPath(active + ".responses['200'].content['application/json'].schema.items['$ref']").value("#/components/schemas/SystemNotification"))
                .andExpect(jsonPath(active + ".responses['404']").doesNotExist()).andExpect(jsonPath(active + ".security").doesNotExist())
                .andExpect(jsonPath(create + ".operationId").value("createSystemNotification")).andExpect(jsonPath(create + ".parameters[0].name").value("sendMaintenanceEmail"))
                .andExpect(jsonPath(create + ".parameters[0].required").value(false)).andExpect(jsonPath(create + ".parameters[0].schema.default").value(false))
                .andExpect(jsonPath(create + ".requestBody.content['application/json'].schema['$ref']").value("#/components/schemas/SystemNotificationUpdate"))
                .andExpect(jsonPath(create + ".responses['201'].content['application/json'].schema['$ref']").value("#/components/schemas/SystemNotification"))
                .andExpect(jsonPath(create + ".responses['201'].headers.Location.schema.type").value("string"))
                .andExpect(jsonPath(create + ".responses['201'].headers['X-artemisApp-alert'].schema.type").value("string"))
                .andExpect(jsonPath(create + ".responses['201'].headers['X-artemisApp-params'].schema.type").value("string"))
                .andExpect(jsonPath(create + ".responses['200']").doesNotExist()).andExpect(jsonPath(create + ".responses['400']").exists())
                .andExpect(jsonPath(update + ".operationId").value("updateSystemNotification"))
                .andExpect(jsonPath(update + ".responses['200'].content['application/json'].schema['$ref']").value("#/components/schemas/SystemNotification"))
                .andExpect(jsonPath(update + ".responses['200'].headers['X-artemisApp-alert'].schema.type").value("string"))
                .andExpect(jsonPath(update + ".responses['200'].headers['X-artemisApp-params'].schema.type").value("string"))
                .andExpect(jsonPath(update + ".responses['400']").exists()).andExpect(jsonPath(delete + ".operationId").value("deleteSystemNotification"))
                .andExpect(jsonPath(delete + ".responses['200'].content").doesNotExist()).andExpect(jsonPath(delete + ".responses['204']").doesNotExist())
                .andExpect(jsonPath(delete + ".responses['200'].headers['X-artemisApp-alert'].schema.type").value("string"))
                .andExpect(jsonPath(delete + ".responses['200'].headers['X-artemisApp-params'].schema.type").value("string"))
                .andExpect(jsonPath(count + ".operationId").value("getMaintenanceRecipientsCount"))
                .andExpect(jsonPath(count + ".responses['200'].content['application/json'].schema.type").value("integer"))
                .andExpect(jsonPath(response + ".required", containsInAnyOrder("id")))
                .andExpect(jsonPath(response + ".properties.type.enum", containsInAnyOrder("WARNING", "INFO")))
                .andExpect(jsonPath(response + ".properties.notificationDate.format").value("date-time"))
                .andExpect(jsonPath(response + ".properties.expireDate.format").value("date-time")).andExpect(jsonPath(response + ".properties.author").doesNotExist())
                .andExpect(jsonPath(input + ".required", containsInAnyOrder("notificationDate", "expireDate")))
                .andExpect(jsonPath(input + ".properties.id.type", containsInAnyOrder("integer", "null")))
                .andExpect(jsonPath(input + ".properties.title.type", containsInAnyOrder("string", "null")))
                .andExpect(jsonPath(input + ".properties.notificationDate.type").value("string")).andExpect(jsonPath(input + ".properties.type.enum", hasItem(nullValue())));
    }
}
