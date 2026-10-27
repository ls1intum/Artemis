package de.tum.cit.aet.artemis.notification.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.OpenAPIConfiguration;
import de.tum.cit.aet.artemis.notification.dto.CourseNotificationPageDTO;
import de.tum.cit.aet.artemis.notification.dto.payload.CourseNotificationPayloadDTO;
import de.tum.cit.aet.artemis.notification.service.CourseNotificationRegistryService;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;

/** Exercises the actual ordered register → required → compose → filter pipeline, not an independently ordered bean list. */
class CourseNotificationOpenApiSchemaTest {

    @Test
    void shouldRetainEveryEnvelopeAndPayloadThroughTheSharedPipeline() {
        var source = new HashMap<>(ModelConverters.getInstance(true).readAll(CourseNotificationPageDTO.class));
        Set<String> originalFields = new HashSet<>(source.get("CourseNotificationDTO").getProperties().keySet());
        source.put("UnrelatedDTO", new StringSchema());
        var api = new OpenAPI().components(new Components().schemas(source)).paths(new Paths());

        new OpenAPIConfiguration().schemaCustomizer().customise(api);

        var schemas = api.getComponents().getSchemas();
        var union = schemas.get("CourseNotification");
        assertThat(union.getProperties()).isNull();
        assertThat(union.getAllOf()).isNull();
        assertThat(union.getOneOf()).hasSize(28);
        assertThat(union.getDiscriminator().getPropertyName()).isEqualTo("notificationType");
        assertThat(union.getDiscriminator().getMapping().keySet()).containsExactlyInAnyOrderElementsOf(new CourseNotificationRegistryService().getNotificationTypes().values());
        assertThat(schemas).doesNotContainKeys("CourseNotificationDTO", "CourseNotificationPayload");
        assertThat(schemas.get("Unrelated").getType()).isEqualTo("string");
        Set<String> payloadRefs = new HashSet<>();
        union.getDiscriminator().getMapping().forEach((name, ref) -> {
            Schema<?> branch = schemas.get(ref.substring("#/components/schemas/".length()));
            assertThat(branch.getAllOf()).isNull();
            assertThat(branch.getProperties().keySet()).containsExactlyInAnyOrderElementsOf(originalFields);
            Schema<?> discriminator = branch.getProperties().get("notificationType");
            assertThat(discriminator.getEnum()).isEqualTo(java.util.List.of(name));
            assertThat(branch.getRequired()).contains("notificationType", "notificationId", "courseId").doesNotContain("payload", "courseTitle", "courseIconUrl");
            Schema<?> payload = branch.getProperties().get("payload");
            payloadRefs.add(payload.get$ref().substring("#/components/schemas/".length()));
            Schema<?> parameters = branch.getProperties().get("parameters");
            assertThat(parameters.getReadOnly()).isTrue();
            assertThat(parameters.getDeprecated()).isTrue();
        });
        var expectedPayloads = Arrays.stream(CourseNotificationPayloadDTO.class.getPermittedSubclasses()).map(type -> type.getSimpleName().replaceFirst("DTO$", ""))
                .collect(Collectors.toSet());
        assertThat(payloadRefs).containsExactlyInAnyOrderElementsOf(expectedPayloads);
        for (Class<?> payloadType : CourseNotificationPayloadDTO.class.getPermittedSubclasses()) {
            Schema<?> payload = schemas.get(payloadType.getSimpleName().replaceFirst("DTO$", ""));
            assertThat(payload.getProperties().keySet())
                    .containsExactlyInAnyOrderElementsOf(Arrays.stream(payloadType.getRecordComponents()).map(component -> component.getName()).toList());
            var primitives = Arrays.stream(payloadType.getRecordComponents()).filter(component -> component.getType().isPrimitive()).map(component -> component.getName()).toList();
            if (primitives.isEmpty()) {
                assertThat(payload.getRequired()).isNullOrEmpty();
            }
            else {
                assertThat(payload.getRequired()).containsExactlyInAnyOrderElementsOf(primitives);
            }
        }
        Schema<?> nullable = schemas.get("NullableCourseNotification");
        assertThat(nullable.getOneOf()).hasSize(29);
        assertThat(nullable.getOneOf().getLast().getTypes()).containsExactly("null");
        Schema<?> page = schemas.get("CourseNotificationPage");
        Schema<?> content = page.getProperties().get("content");
        assertThat(content.getItems().get$ref()).isEqualTo("#/components/schemas/NullableCourseNotification");
        assertThat(schemas.get("CourseNotificationPage").getRequired()).containsExactlyInAnyOrder("pageNumber", "pageSize", "totalElements", "totalPages");
        Schema<?> scalar = schemas.get("CourseNotificationParameter");
        assertThat(scalar.getOneOf().getLast().getTypes()).containsExactly("null");
    }
}
