package de.tum.cit.aet.artemis.notification.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.hamcrest.Matcher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.JsonPathExpectationsHelper;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import de.tum.cit.aet.artemis.notification.domain.GlobalNotificationType;

/**
 * Checks notification contracts in the committed application specification consumed by the generated client.
 * <p>
 * Regenerate the specification after server contract changes; these assertions verify its contents, not generation freshness.
 */
class NotificationOpenApiContractTest {

    private static final Path SPECIFICATION = Path.of("config", "openapi", "openapi.yaml");

    private static final Set<String> HTTP_METHODS = Set.of("get", "post", "put", "delete", "patch", "head", "options", "trace");

    private static JsonNode specification;

    private static String specificationJson;

    @BeforeAll
    static void loadSpecification() throws IOException {
        specification = YAMLMapper.builder().build().readTree(SPECIFICATION.toFile());
        specificationJson = JsonMapper.builder().build().writeValueAsString(specification);
    }

    @Test
    void shouldDescribeBothGlobalSettingsOperationsWithAccurateSchemas() {
        String read = "$.paths['/api/notification/global-notification-settings'].get";
        String update = "$.paths['/api/notification/global-notification-settings/{notificationType}'].put";
        String settings = "$.components.schemas.GlobalNotificationSetting";
        String input = "$.components.schemas.UpdateGlobalNotificationSetting";
        String[] notificationTypes = Arrays.stream(GlobalNotificationType.values()).map(Enum::name).toArray(String[]::new);

        assertValue(read + ".operationId", "getGlobalNotificationSettings");
        assertValue(read + ".responses['200'].content['application/json'].schema.additionalProperties.type", "boolean");
        assertValue(update + ".operationId", "updateGlobalNotificationSetting");
        assertValue(update + ".parameters[0].name", "notificationType");
        assertValue(update + ".parameters[0].in", "path");
        assertValue(update + ".parameters[0].required", true);
        assertValue(update + ".parameters[0].schema.enum", containsInAnyOrder(notificationTypes));
        assertValue(update + ".requestBody.required", true);
        assertValue(update + ".requestBody.content['application/json'].schema['$ref']", "#/components/schemas/UpdateGlobalNotificationSetting");
        assertValue(update + ".responses['200'].content['application/json'].schema['$ref']", "#/components/schemas/GlobalNotificationSetting");
        assertValue(settings + ".required", containsInAnyOrder("id", "userId", "notificationType", "enabled"));
        assertValue(settings + ".properties.notificationType.enum", containsInAnyOrder(notificationTypes));
        assertValue(settings + ".properties.enabled.type", "boolean");
        assertDoesNotExist(input + ".required");
        assertValue(input + ".properties.enabled.type", containsInAnyOrder("boolean", "null"));
        assertValue(input + ".properties.enabled.default", false);
    }

    @Test
    void shouldDescribeAllSevenSystemOperationsWithRealHeadersStatusesAndPageableParameters() {
        String list = "$.paths['/api/notification/admin/system-notifications'].get";
        String find = "$.paths['/api/notification/admin/system-notifications/{notificationId}'].get";
        String active = "$.paths['/api/notification/public/system-notifications/active'].get";
        String create = "$.paths['/api/notification/admin/system-notifications'].post";
        String update = "$.paths['/api/notification/admin/system-notifications'].put";
        String delete = "$.paths['/api/notification/admin/system-notifications/{notificationId}'].delete";
        String count = "$.paths['/api/notification/admin/system-notifications/maintenance-recipients-count'].get";
        String response = "$.components.schemas.SystemNotification";
        String input = "$.components.schemas.SystemNotificationUpdate";

        assertValue(list + ".operationId", "getAllSystemNotifications");
        assertValue(list + ".tags", hasItem("admin-system-notification"));
        assertValue(find + ".tags", hasItem("admin-system-notification"));
        assertDoesNotExist("$.paths['/api/notification/system-notifications']");
        assertDoesNotExist("$.paths['/api/notification/system-notifications/{notificationId}']");
        assertValue(list + ".parameters[*].name", containsInAnyOrder("page", "size", "sort"));
        assertValue(list + ".parameters[0].schema.default", 0);
        assertValue(list + ".parameters[1].schema.default", 20);
        assertValue(list + ".parameters[2].schema.type", "array");
        assertValue(list + ".responses['200'].content['application/json'].schema.type", "array");
        assertValue(list + ".responses['200'].content['application/json'].schema.items['$ref']", "#/components/schemas/SystemNotification");
        assertValue(list + ".responses['200'].headers.Link.schema.type", "string");
        assertValue(list + ".responses['200'].headers['X-Total-Count'].schema.type", "integer");
        assertValue(find + ".operationId", "getSystemNotification");
        assertValue(find + ".responses['200'].content['application/json'].schema['$ref']", "#/components/schemas/SystemNotification");
        assertExists(find + ".responses['404']");
        assertDoesNotExist(find + ".responses['200'].headers");
        assertValue(active + ".operationId", "getActiveAndFutureSystemNotifications");
        assertValue(active + ".responses['200'].content['application/json'].schema.items['$ref']", "#/components/schemas/SystemNotification");
        assertDoesNotExist(active + ".responses['404']");
        assertDoesNotExist(active + ".security");
        assertValue(create + ".operationId", "createSystemNotification");
        assertValue(create + ".parameters[0].name", "sendMaintenanceEmail");
        assertValue(create + ".parameters[0].required", false);
        assertValue(create + ".parameters[0].schema.default", false);
        assertValue(create + ".requestBody.content['application/json'].schema['$ref']", "#/components/schemas/SystemNotificationUpdate");
        assertValue(create + ".responses['201'].content['application/json'].schema['$ref']", "#/components/schemas/SystemNotification");
        assertValue(create + ".responses['201'].headers.Location.schema.type", "string");
        assertValue(create + ".responses['201'].headers['X-artemisApp-alert'].schema.type", "string");
        assertValue(create + ".responses['201'].headers['X-artemisApp-params'].schema.type", "string");
        assertDoesNotExist(create + ".responses['200']");
        assertExists(create + ".responses['400']");
        assertValue(update + ".operationId", "updateSystemNotification");
        assertValue(update + ".responses['200'].content['application/json'].schema['$ref']", "#/components/schemas/SystemNotification");
        assertValue(update + ".responses['200'].headers['X-artemisApp-alert'].schema.type", "string");
        assertValue(update + ".responses['200'].headers['X-artemisApp-params'].schema.type", "string");
        assertExists(update + ".responses['400']");
        assertValue(delete + ".operationId", "deleteSystemNotification");
        assertDoesNotExist(delete + ".responses['200'].content");
        assertDoesNotExist(delete + ".responses['204']");
        assertValue(delete + ".responses['200'].headers['X-artemisApp-alert'].schema.type", "string");
        assertValue(delete + ".responses['200'].headers['X-artemisApp-params'].schema.type", "string");
        assertValue(count + ".operationId", "getMaintenanceRecipientsCount");
        assertValue(count + ".responses['200'].content['application/json'].schema.type", "integer");
        assertValue(response + ".required", containsInAnyOrder("id"));
        assertValue(response + ".properties.type.enum", containsInAnyOrder("WARNING", "INFO"));
        assertValue(response + ".properties.notificationDate.format", "date-time");
        assertValue(response + ".properties.expireDate.format", "date-time");
        assertDoesNotExist(response + ".properties.author");
        assertValue(input + ".required", containsInAnyOrder("notificationDate", "expireDate"));
        assertValue(input + ".properties.id.type", containsInAnyOrder("integer", "null"));
        assertValue(input + ".properties.title.type", containsInAnyOrder("string", "null"));
        assertValue(input + ".properties.notificationDate.type", "string");
        assertValue(input + ".properties.type.enum", hasItem(nullValue()));
    }

    @Test
    void shouldDescribeTheThreePreferenceOperationsWithScalarAndNestedMapContracts() {
        String read = "$.paths['/api/notification/courses/{courseId}/settings'].get";
        String preset = "$.paths['/api/notification/courses/{courseId}/setting-preset'].put";
        String specification = "$.paths['/api/notification/courses/{courseId}/setting-specification'].put";
        String info = "$.components.schemas.CourseNotificationSettingInfo";
        String input = "$.components.schemas.CourseNotificationSettingSpecificationRequest";
        String channels = ".properties.notificationTypeChannels.additionalProperties";

        assertValue(read + ".operationId", "getUserCourseNotificationSettings");
        assertValue(read + ".parameters[0].name", "courseId");
        assertValue(read + ".parameters[0].in", "path");
        assertValue(read + ".responses['200'].content['application/json'].schema['$ref']", "#/components/schemas/CourseNotificationSettingInfo");
        assertValue(preset + ".operationId", "setUserCourseNotificationPreset");
        assertValue(preset + ".requestBody.required", true);
        assertValue(preset + ".requestBody.content['application/json'].schema.type", "integer");
        assertValue(preset + ".requestBody.content['application/json'].schema.minimum", -32768);
        assertValue(preset + ".requestBody.content['application/json'].schema.maximum", 32767);
        assertDoesNotExist(preset + ".responses['200'].content");
        assertExists(preset + ".responses['400']");
        assertValue(specification + ".operationId", "setUserCourseNotificationSpecification");
        assertValue(specification + ".requestBody.content['application/json'].schema['$ref']", "#/components/schemas/CourseNotificationSettingSpecificationRequest");
        assertDoesNotExist(specification + ".responses['200'].content");
        assertValue(info + ".required", containsInAnyOrder("selectedPreset"));
        assertValue(info + channels + ".required", containsInAnyOrder("EMAIL", "WEBAPP", "PUSH"));
        assertValue(info + channels + ".additionalProperties", false);
        assertValue(info + channels + ".properties.EMAIL.type", "boolean");
        assertValue(input + ".required", containsInAnyOrder("notificationTypeChannels"));
        assertDoesNotExist(input + channels + ".required");
        assertValue(input + channels + ".additionalProperties", false);
        assertValue(input + channels + ".properties.EMAIL.type", containsInAnyOrder("boolean", "null"));
    }

    @Test
    void shouldDescribeFiveFeedAndStateOperationsWithNullableDiscriminatedItems() {
        String feed = "$.paths['/api/notification/courses/{courseId}'].get";
        String info = "$.paths['/api/notification/courses/info'].get";
        String state = "$.paths['/api/notification/courses/{courseId}/status'].put";
        String seen = "$.paths['/api/notification/courses/{courseId}/seen'].put";
        String archive = "$.paths['/api/notification/courses/{courseId}/archive-all'].put";

        assertValue(feed + ".operationId", "getCourseNotifications");
        assertValue(feed + ".parameters[*].name", containsInAnyOrder("courseId", "page", "size", "sort"));
        assertValue(feed + ".responses['200'].content['application/json'].schema['$ref']", "#/components/schemas/CourseNotificationPage");
        assertValue(info + ".operationId", "getCourseNotificationInfo");
        assertValue(info + ".responses['200'].content['application/json'].schema['$ref']", "#/components/schemas/CourseNotificationInfo");
        assertValue(state + ".operationId", "updateNotificationStatus");
        assertValue(state + ".requestBody.content['application/json'].schema['$ref']", "#/components/schemas/UserCourseNotificationStatusUpdateRequest");
        assertValue(seen + ".operationId", "markDisplayedNotificationsAsSeen");
        assertValue(seen + ".requestBody.content['application/json'].schema['$ref']", "#/components/schemas/UserCourseNotificationSeenRequest");
        assertValue(archive + ".operationId", "archiveAll");
        assertDoesNotExist(archive + ".requestBody");
        assertDoesNotExist(state + ".responses['200'].content");
        assertDoesNotExist(seen + ".responses['200'].content");
        assertDoesNotExist(archive + ".responses['200'].content");
        assertValue("$.components.schemas.UserCourseNotificationStatusUpdateRequest.required", containsInAnyOrder("notificationIds", "statusType"));
        assertValue("$.components.schemas.UserCourseNotificationStatusUpdateRequest.properties.statusType.enum", containsInAnyOrder("UNSEEN", "SEEN", "ARCHIVED"));
        assertValue("$.components.schemas.UserCourseNotificationSeenRequest.required", containsInAnyOrder("notificationIds"));
        assertValue("$.components.schemas.CourseNotification.oneOf", hasSize(28));
        assertValue("$.components.schemas.CourseNotification.discriminator.propertyName", "notificationType");
        assertValue("$.components.schemas.NullableCourseNotification.oneOf", hasSize(29));
        assertValue("$.components.schemas.NullableCourseNotification.oneOf[28].type", "null");
        assertValue("$.components.schemas.CourseNotificationPage.properties.content.items['$ref']", "#/components/schemas/NullableCourseNotification");
        assertValue("$.components.schemas.CourseNotificationPage.required", containsInAnyOrder("pageNumber", "pageSize", "totalElements", "totalPages"));
        assertValue("$.components.schemas.UserCourseNotificationSettingPreset.required", containsInAnyOrder("identifier", "typeId"));
        assertValue("$.components.schemas.UserCourseNotificationSettingPreset.properties.presetMap.additionalProperties.required", containsInAnyOrder("EMAIL", "WEBAPP", "PUSH"));
    }

    @Test
    void shouldDescribePushInputsDefaultsStatusAndOptionalLegacyDeleteBody() {
        String register = "$.paths['/api/notification/push_notification/register'].post";
        String unregister = "$.paths['/api/notification/push_notification/unregister'].delete";
        String input = "$.components.schemas.PushNotificationRegisterBody";
        String legacy = "$.components.schemas.PushNotificationUnregisterRequest";

        assertValue(register + ".operationId", "registerPushNotificationDevice");
        assertValue(register + ".requestBody.required", true);
        assertValue(register + ".requestBody.content['application/json'].schema['$ref']", "#/components/schemas/PushNotificationRegisterBody");
        assertValue(register + ".responses['200'].content['application/json'].schema['$ref']", "#/components/schemas/PushNotificationRegister");
        assertDoesNotExist(register + ".responses['401'].content");
        assertExists(register + ".responses['400']");
        assertExists(register + ".responses['500']");
        assertValue(input + ".properties.deviceType.enum", containsInAnyOrder("FIREBASE", "APNS"));
        assertValue(input + ".properties.apiType.enum", containsInAnyOrder("DEFAULT", "IOS_V2", null));
        assertValue(input + ".properties.apiType.type", containsInAnyOrder("string", "null"));
        assertValue(input + ".properties.apiType.default", "DEFAULT");
        assertValue(input + ".properties.versionCode.type", containsInAnyOrder("string", "null"));
        assertValue(input + ".properties.versionCode.pattern", "^\\d+\\.\\d+\\.\\d+$");
        assertValue("$.components.schemas.PushNotificationRegister.required", containsInAnyOrder("secretKey", "algorithm"));
        assertValue(unregister + ".operationId", "unregisterPushNotificationDevice");
        assertDoesNotExist(unregister + ".deprecated");
        assertValue(unregister + ".parameters[*].name", containsInAnyOrder("token", "deviceType"));
        assertValue(unregister + ".parameters[*].required", containsInAnyOrder(false, false));
        assertDoesNotExist(unregister + ".requestBody.required");
        assertValue(unregister + ".requestBody.description", org.hamcrest.Matchers.containsString("Deprecated"));
        assertValue(unregister + ".requestBody.content['application/json'].schema['$ref']", "#/components/schemas/PushNotificationUnregisterRequest");
        assertDoesNotExist(unregister + ".responses['200'].content");
        assertValue(unregister + ".responses['400'].content['application/problem+json'].schema.type", "object");
        assertDoesNotExist(unregister + ".responses['404'].content");
        assertDoesNotExist(legacy + ".required");
        assertValue(legacy + ".properties.deviceType.enum", containsInAnyOrder("FIREBASE", "APNS", null));
        assertValue(legacy + ".properties.token.type", containsInAnyOrder("string", "null"));
    }

    @Test
    void shouldContainExactlyAllNineteenCanonicalNotificationOperations() {
        var paths = specification.path("paths").properties().stream().filter(path -> path.getKey().startsWith("/api/notification/")).toList();
        assertThat(paths).hasSize(16);
        Set<String> expected = Set.of("GET /api/notification/global-notification-settings", "PUT /api/notification/global-notification-settings/{notificationType}",
                "GET /api/notification/admin/system-notifications", "GET /api/notification/admin/system-notifications/{notificationId}",
                "GET /api/notification/public/system-notifications/active", "POST /api/notification/admin/system-notifications", "PUT /api/notification/admin/system-notifications",
                "DELETE /api/notification/admin/system-notifications/{notificationId}", "GET /api/notification/admin/system-notifications/maintenance-recipients-count",
                "GET /api/notification/courses/info", "GET /api/notification/courses/{courseId}", "GET /api/notification/courses/{courseId}/settings",
                "PUT /api/notification/courses/{courseId}/setting-preset", "PUT /api/notification/courses/{courseId}/setting-specification",
                "PUT /api/notification/courses/{courseId}/status", "PUT /api/notification/courses/{courseId}/seen", "PUT /api/notification/courses/{courseId}/archive-all",
                "POST /api/notification/push_notification/register", "DELETE /api/notification/push_notification/unregister");
        Set<String> actual = paths.stream().flatMap(path -> path.getValue().properties().stream().filter(operation -> HTTP_METHODS.contains(operation.getKey()))
                .map(operation -> operation.getKey().toUpperCase(java.util.Locale.ROOT) + " " + path.getKey())).collect(Collectors.toSet());
        assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
    }

    private void assertValue(String path, Object expected) {
        new JsonPathExpectationsHelper(path).assertValue(specificationJson, expected);
    }

    private <T> void assertValue(String path, Matcher<? super T> expected) {
        new JsonPathExpectationsHelper(path).assertValue(specificationJson, expected);
    }

    private void assertExists(String path) {
        new JsonPathExpectationsHelper(path).exists(specificationJson);
    }

    private void assertDoesNotExist(String path) {
        new JsonPathExpectationsHelper(path).doesNotExist(specificationJson);
    }
}
