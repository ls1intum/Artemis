package de.tum.cit.aet.artemis.notification.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.notification.domain.GlobalNotificationType;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Checks the notification contracts exported by springdoc before client-input pruning can hide a missing schema.
 */
@TestPropertySource(properties = { "springdoc.api-docs.enabled=true", "springdoc.packages-to-scan=de.tum.cit.aet.artemis.notification.web", "springdoc.paths-to-exclude=" })
class NotificationOpenApiContractTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private JsonMapper objectMapper;

    @Test
    void shouldExportBothGlobalSettingsOperationsWithAccurateSchemas() throws Exception {
        String read = "$.paths['/api/notification/global-notification-settings'].get";
        String update = "$.paths['/api/notification/global-notification-settings/{notificationType}'].put";
        String settings = "$.components.schemas.GlobalNotificationSetting";
        String input = "$.components.schemas.UpdateGlobalNotificationSetting";
        String[] notificationTypes = Arrays.stream(GlobalNotificationType.values()).map(Enum::name).toArray(String[]::new);

        request.performMvcRequest(MockMvcRequestBuilders.get("/api-docs")).andExpect(status().isOk()).andExpect(jsonPath("$.paths", aMapWithSize(18)))
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
        request.performMvcRequest(MockMvcRequestBuilders.get("/api-docs")).andExpect(status().isOk()).andExpect(jsonPath("$.paths", aMapWithSize(18)))
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

    @Test
    void shouldExportTheThreePreferenceOperationsWithScalarAndNestedMapContracts() throws Exception {
        String read = "$.paths['/api/notification/courses/{courseId}/settings'].get";
        String preset = "$.paths['/api/notification/courses/{courseId}/setting-preset'].put";
        String specification = "$.paths['/api/notification/courses/{courseId}/setting-specification'].put";
        String info = "$.components.schemas.CourseNotificationSettingInfo";
        String input = "$.components.schemas.CourseNotificationSettingSpecificationRequest";
        String channels = ".properties.notificationTypeChannels.additionalProperties";
        request.performMvcRequest(MockMvcRequestBuilders.get("/api-docs")).andExpect(status().isOk()).andExpect(jsonPath("$.paths", aMapWithSize(18)))
                .andExpect(jsonPath(read + ".operationId").value("getUserCourseNotificationSettings")).andExpect(jsonPath(read + ".parameters[0].name").value("courseId"))
                .andExpect(jsonPath(read + ".parameters[0].in").value("path"))
                .andExpect(jsonPath(read + ".responses['200'].content['application/json'].schema['$ref']").value("#/components/schemas/CourseNotificationSettingInfo"))
                .andExpect(jsonPath(preset + ".operationId").value("setUserCourseNotificationPreset")).andExpect(jsonPath(preset + ".requestBody.required").value(true))
                .andExpect(jsonPath(preset + ".requestBody.content['application/json'].schema.type").value("integer"))
                .andExpect(jsonPath(preset + ".requestBody.content['application/json'].schema.minimum").value(-32768))
                .andExpect(jsonPath(preset + ".requestBody.content['application/json'].schema.maximum").value(32767))
                .andExpect(jsonPath(preset + ".responses['200'].content").doesNotExist()).andExpect(jsonPath(preset + ".responses['400']").exists())
                .andExpect(jsonPath(specification + ".operationId").value("setUserCourseNotificationSpecification"))
                .andExpect(jsonPath(specification + ".requestBody.content['application/json'].schema['$ref']")
                        .value("#/components/schemas/CourseNotificationSettingSpecificationRequest"))
                .andExpect(jsonPath(specification + ".responses['200'].content").doesNotExist()).andExpect(jsonPath(info + ".required", containsInAnyOrder("selectedPreset")))
                .andExpect(jsonPath(info + channels + ".required", containsInAnyOrder("EMAIL", "WEBAPP", "PUSH")))
                .andExpect(jsonPath(info + channels + ".additionalProperties").value(false)).andExpect(jsonPath(info + channels + ".properties.EMAIL.type").value("boolean"))
                .andExpect(jsonPath(input + ".required", containsInAnyOrder("notificationTypeChannels"))).andExpect(jsonPath(input + channels + ".required").doesNotExist())
                .andExpect(jsonPath(input + channels + ".additionalProperties").value(false))
                .andExpect(jsonPath(input + channels + ".properties.EMAIL.type", containsInAnyOrder("boolean", "null")));
    }

    @Test
    void shouldExportFiveFeedAndStateOperationsWithNullableDiscriminatedItems() throws Exception {
        String feed = "$.paths['/api/notification/courses/{courseId}'].get";
        String info = "$.paths['/api/notification/courses/info'].get";
        String state = "$.paths['/api/notification/courses/{courseId}/status'].put";
        String seen = "$.paths['/api/notification/courses/{courseId}/seen'].put";
        String archive = "$.paths['/api/notification/courses/{courseId}/archive-all'].put";
        request.performMvcRequest(MockMvcRequestBuilders.get("/api-docs")).andExpect(status().isOk()).andExpect(jsonPath("$.paths", aMapWithSize(18)))
                .andExpect(jsonPath(feed + ".operationId").value("getCourseNotifications"))
                .andExpect(jsonPath(feed + ".parameters[*].name", containsInAnyOrder("courseId", "page", "size", "sort")))
                .andExpect(jsonPath(feed + ".responses['200'].content['application/json'].schema['$ref']").value("#/components/schemas/CourseNotificationPage"))
                .andExpect(jsonPath(info + ".operationId").value("getCourseNotificationInfo"))
                .andExpect(jsonPath(info + ".responses['200'].content['application/json'].schema['$ref']").value("#/components/schemas/CourseNotificationInfo"))
                .andExpect(jsonPath(state + ".operationId").value("updateNotificationStatus"))
                .andExpect(jsonPath(state + ".requestBody.content['application/json'].schema['$ref']").value("#/components/schemas/UserCourseNotificationStatusUpdateRequest"))
                .andExpect(jsonPath(seen + ".operationId").value("markDisplayedNotificationsAsSeen"))
                .andExpect(jsonPath(seen + ".requestBody.content['application/json'].schema['$ref']").value("#/components/schemas/UserCourseNotificationSeenRequest"))
                .andExpect(jsonPath(archive + ".operationId").value("archiveAll")).andExpect(jsonPath(archive + ".requestBody").doesNotExist())
                .andExpect(jsonPath(state + ".responses['200'].content").doesNotExist()).andExpect(jsonPath(seen + ".responses['200'].content").doesNotExist())
                .andExpect(jsonPath(archive + ".responses['200'].content").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.UserCourseNotificationStatusUpdateRequest.required", containsInAnyOrder("notificationIds", "statusType")))
                .andExpect(jsonPath("$.components.schemas.UserCourseNotificationStatusUpdateRequest.properties.statusType.enum", containsInAnyOrder("UNSEEN", "SEEN", "ARCHIVED")))
                .andExpect(jsonPath("$.components.schemas.UserCourseNotificationSeenRequest.required", containsInAnyOrder("notificationIds")))
                .andExpect(jsonPath("$.components.schemas.CourseNotification.oneOf", hasSize(28)))
                .andExpect(jsonPath("$.components.schemas.CourseNotification.discriminator.propertyName").value("notificationType"))
                .andExpect(jsonPath("$.components.schemas.NullableCourseNotification.oneOf", hasSize(29)))
                .andExpect(jsonPath("$.components.schemas.NullableCourseNotification.oneOf[28].type").value("null"))
                .andExpect(jsonPath("$.components.schemas.CourseNotificationPage.properties.content.items['$ref']").value("#/components/schemas/NullableCourseNotification"))
                .andExpect(jsonPath("$.components.schemas.CourseNotificationPage.required", containsInAnyOrder("pageNumber", "pageSize", "totalElements", "totalPages")))
                .andExpect(jsonPath("$.components.schemas.UserCourseNotificationSettingPreset.required", containsInAnyOrder("identifier", "typeId")))
                .andExpect(jsonPath("$.components.schemas.UserCourseNotificationSettingPreset.properties.presetMap.additionalProperties.required",
                        containsInAnyOrder("EMAIL", "WEBAPP", "PUSH")));
    }

    @Test
    void shouldExportPushInputsDefaultsStatusAndOptionalLegacyDeleteBody() throws Exception {
        String register = "$.paths['/api/notification/push_notification/register'].post";
        String unregister = "$.paths['/api/notification/push_notification/unregister'].delete";
        String input = "$.components.schemas.PushNotificationRegisterBody";
        String legacy = "$.components.schemas.PushNotificationUnregisterRequest";
        request.performMvcRequest(MockMvcRequestBuilders.get("/api-docs")).andExpect(status().isOk()).andExpect(jsonPath("$.paths", aMapWithSize(18)))
                .andExpect(jsonPath(register + ".operationId").value("registerPushNotificationDevice")).andExpect(jsonPath(register + ".requestBody.required").value(true))
                .andExpect(jsonPath(register + ".requestBody.content['application/json'].schema['$ref']").value("#/components/schemas/PushNotificationRegisterBody"))
                .andExpect(jsonPath(register + ".responses['200'].content['application/json'].schema['$ref']").value("#/components/schemas/PushNotificationRegister"))
                .andExpect(jsonPath(register + ".responses['401'].content").doesNotExist()).andExpect(jsonPath(register + ".responses['400']").exists())
                .andExpect(jsonPath(register + ".responses['500']").exists()).andExpect(jsonPath(input + ".properties.deviceType.enum", containsInAnyOrder("FIREBASE", "APNS")))
                .andExpect(jsonPath(input + ".properties.apiType.enum", containsInAnyOrder("DEFAULT", "IOS_V2", null)))
                .andExpect(jsonPath(input + ".properties.apiType.type", containsInAnyOrder("string", "null")))
                .andExpect(jsonPath(input + ".properties.apiType.default").value("DEFAULT"))
                .andExpect(jsonPath(input + ".properties.versionCode.type", containsInAnyOrder("string", "null")))
                .andExpect(jsonPath(input + ".properties.versionCode.pattern").value("^\\d+\\.\\d+\\.\\d+$"))
                .andExpect(jsonPath("$.components.schemas.PushNotificationRegister.required", containsInAnyOrder("secretKey", "algorithm")))
                .andExpect(jsonPath(unregister + ".operationId").value("unregisterPushNotificationDevice")).andExpect(jsonPath(unregister + ".deprecated").doesNotExist())
                .andExpect(jsonPath(unregister + ".parameters[*].name", containsInAnyOrder("token", "deviceType")))
                .andExpect(jsonPath(unregister + ".parameters[*].required", containsInAnyOrder(false, false)))
                .andExpect(jsonPath(unregister + ".requestBody.required").doesNotExist())
                .andExpect(jsonPath(unregister + ".requestBody.description").value(org.hamcrest.Matchers.containsString("Deprecated")))
                .andExpect(jsonPath(unregister + ".requestBody.content['application/json'].schema['$ref']").value("#/components/schemas/PushNotificationUnregisterRequest"))
                .andExpect(jsonPath(unregister + ".responses['200'].content").doesNotExist())
                .andExpect(jsonPath(unregister + ".responses['400'].content['application/problem+json'].schema.type").value("object"))
                .andExpect(jsonPath(unregister + ".responses['404'].content").doesNotExist()).andExpect(jsonPath(legacy + ".required").doesNotExist())
                .andExpect(jsonPath(legacy + ".properties.deviceType.enum", containsInAnyOrder("FIREBASE", "APNS", null)))
                .andExpect(jsonPath(legacy + ".properties.token.type", containsInAnyOrder("string", "null")));
    }

    @Test
    void shouldExportExactlyAllNineteenCanonicalNotificationOperations() throws Exception {
        var result = request.performMvcRequest(MockMvcRequestBuilders.get("/api-docs")).andExpect(status().isOk()).andReturn();
        var paths = objectMapper.readTree(result.getResponse().getContentAsString()).path("paths");
        Set<String> expected = Set.of("GET /api/notification/global-notification-settings", "PUT /api/notification/global-notification-settings/{notificationType}",
                "GET /api/notification/system-notifications", "GET /api/notification/system-notifications/{notificationId}",
                "GET /api/notification/public/system-notifications/active", "POST /api/notification/admin/system-notifications", "PUT /api/notification/admin/system-notifications",
                "DELETE /api/notification/admin/system-notifications/{notificationId}", "GET /api/notification/admin/system-notifications/maintenance-recipients-count",
                "GET /api/notification/courses/info", "GET /api/notification/courses/{courseId}", "GET /api/notification/courses/{courseId}/settings",
                "PUT /api/notification/courses/{courseId}/setting-preset", "PUT /api/notification/courses/{courseId}/setting-specification",
                "PUT /api/notification/courses/{courseId}/status", "PUT /api/notification/courses/{courseId}/seen", "PUT /api/notification/courses/{courseId}/archive-all",
                "POST /api/notification/push_notification/register", "DELETE /api/notification/push_notification/unregister");
        Set<String> actual = paths.properties().stream()
                .flatMap(path -> path.getValue().properties().stream().filter(operation -> Set.of("get", "post", "put", "delete", "patch").contains(operation.getKey()))
                        .map(operation -> operation.getKey().toUpperCase(java.util.Locale.ROOT) + " " + path.getKey()))
                .collect(Collectors.toSet());
        assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
    }

}
