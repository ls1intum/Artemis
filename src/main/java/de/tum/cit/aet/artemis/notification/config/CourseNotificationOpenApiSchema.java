package de.tum.cit.aet.artemis.notification.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;

import de.tum.cit.aet.artemis.notification.annotations.CourseNotificationType;
import de.tum.cit.aet.artemis.notification.domain.course_notifications.CourseNotification;
import de.tum.cit.aet.artemis.notification.dto.payload.CourseNotificationPayloadDTO;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.Discriminator;
import io.swagger.v3.oas.models.media.MapSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;

/**
 * Builds schema-only envelope variants from the real record fields and annotated notification classes.
 * Called explicitly by the shared schema pipeline: register payloads with their explicit required metadata, compose envelopes, then filter/strip DTO suffixes.
 * No Jackson polymorphism or notification JSON is changed.
 */
public final class CourseNotificationOpenApiSchema {

    private static final String ROOT = "CourseNotificationDTO";

    private static final String NULLABLE = "NullableCourseNotificationDTO";

    private static final String REF_PREFIX = "#/components/schemas/";

    private CourseNotificationOpenApiSchema() {
    }

    /**
     * Registers all sealed payload records, including explicit required metadata, before composition and DTO filtering.
     *
     * @param components schemas exported by springdoc
     */
    public static void registerPayloads(Components components) {
        if (!components.getSchemas().containsKey(ROOT)) {
            return;
        }
        for (Class<?> payload : CourseNotificationPayloadDTO.class.getPermittedSubclasses()) {
            ModelConverters.getInstance(true).readAll(payload).forEach(components::addSchemas);
        }
    }

    /**
     * Derives flat discriminated branches from explicit required metadata before DTO suffix processing.
     *
     * @param components schemas exported and registered in the first phase
     */
    public static void composeEnvelopes(Components components) {
        Schema<?> envelope = components.getSchemas().get(ROOT);
        if (envelope == null) {
            return;
        }
        if (envelope.getProperties() == null) {
            throw new IllegalStateException("Course notification envelope properties must exist before schema composition");
        }
        var variants = discoverVariants();
        Schema<Object> union = new Schema<>();
        Schema<Object> nullableUnion = new Schema<>();
        var discriminator = new Discriminator().propertyName("notificationType");
        Map<String, String> mappings = new LinkedHashMap<>();
        for (Variant variant : variants) {
            var branch = new ObjectSchema();
            branch.setProperties(new LinkedHashMap<>(envelope.getProperties()));
            branch.setRequired(envelope.getRequired() == null ? new ArrayList<>() : new ArrayList<>(envelope.getRequired()));
            var type = new StringSchema();
            type.setEnum(List.of(variant.notificationType()));
            branch.addProperty("notificationType", type);
            branch.addRequiredItem("notificationType");
            branch.addProperty("payload", reference(variant.payloadType().getSimpleName()));
            var parameters = new MapSchema();
            parameters.setAdditionalProperties(reference("CourseNotificationParameterDTO"));
            parameters.setReadOnly(true);
            parameters.setDeprecated(true);
            parameters.setDescription("Legacy flat compatibility values, including a nullable courseIconUrl; not a structured request payload");
            branch.addProperty("parameters", parameters);
            components.addSchemas(variant.schemaName(), branch);
            union.addOneOfItem(reference(variant.schemaName()));
            nullableUnion.addOneOfItem(reference(variant.schemaName()));
            mappings.put(variant.notificationType(), REF_PREFIX + variant.schemaName());
        }
        discriminator.setMapping(mappings);
        union.setDiscriminator(discriminator);
        nullableUnion.setDiscriminator(discriminator);
        // A distinct nullable union retains all branches when the normalizer removes the null branch. A wrapper
        // containing only a reference and null would collapse into a model copy in the pinned generator normalizer.
        nullableUnion.addOneOfItem(nullValueSchema());
        components.addSchemas(ROOT, union);
        components.addSchemas(NULLABLE, nullableUnion);
        Schema<Object> scalar = new Schema<>();
        scalar.addOneOfItem(new StringSchema());
        scalar.addOneOfItem(new NumberSchema());
        scalar.addOneOfItem(new BooleanSchema());
        scalar.addOneOfItem(nullValueSchema());
        components.addSchemas("CourseNotificationParameterDTO", scalar);
        Schema<?> page = components.getSchemas().get("CourseNotificationPageDTO");
        if (page == null || page.getProperties() == null || !page.getProperties().containsKey("content")) {
            throw new IllegalStateException("Course notification page content must be available before schema composition");
        }
        Schema<?> content = page.getProperties().get("content");
        content.setItems(reference(NULLABLE));
        // This interface's overlapping payloads are represented by the outer envelope branches, not a payload-only oneOf.
        components.getSchemas().remove("CourseNotificationPayloadDTO");
    }

    private static Schema<?> nullValueSchema() {
        Schema<Object> schema = new Schema<>();
        schema.setTypes(Set.of("null"));
        return schema;
    }

    private static Schema<?> reference(String name) {
        return new Schema<>().$ref(REF_PREFIX + name);
    }

    private static List<Variant> discoverVariants() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(CourseNotificationType.class));
        Map<Integer, Variant> variants = new TreeMap<>();
        Set<Class<?>> payloads = new HashSet<>();
        for (var definition : scanner.findCandidateComponents(CourseNotification.class.getPackageName())) {
            Class<?> notification = ClassUtils.resolveClassName(definition.getBeanClassName(), CourseNotification.class.getClassLoader());
            try {
                Class<?> payload = notification.getMethod("payload").getReturnType();
                String simpleName = notification.getSimpleName();
                String readable = Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
                String schemaName = simpleName.substring(0, simpleName.length() - "Notification".length()) + "CourseNotificationDTO";
                int id = notification.getAnnotation(CourseNotificationType.class).value();
                if (variants.putIfAbsent(id, new Variant(schemaName, readable, payload)) != null) {
                    throw new IllegalStateException("Duplicate course notification id " + id);
                }
                payloads.add(payload);
            }
            catch (NoSuchMethodException exception) {
                throw new IllegalStateException("Notification has no covariant payload method: " + notification.getName(), exception);
            }
        }
        if (!payloads.equals(new HashSet<>(Arrays.asList(CourseNotificationPayloadDTO.class.getPermittedSubclasses())))) {
            throw new IllegalStateException("Notification variants must cover exactly the sealed payload records");
        }
        return List.copyOf(variants.values());
    }

    private record Variant(String schemaName, String notificationType, Class<?> payloadType) {
    }
}
