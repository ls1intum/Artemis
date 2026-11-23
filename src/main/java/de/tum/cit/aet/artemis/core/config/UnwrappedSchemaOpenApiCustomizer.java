package de.tum.cit.aet.artemis.core.config;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;

/**
 * Describes a DTO that embeds another DTO with {@link JsonUnwrapped} as that DTO plus its own fields.
 * <p>
 * swagger-core writes the fields of an unwrapped component into the schema of every record that embeds it, so a schema that ten records
 * embed appears eleven times in the specification, and the generated client repeats it in eleven models. This customizer rewrites each
 * such schema as {@code allOf} of a reference to the embedded schema and an object with the remaining fields, and marks the embedded
 * schema with {@value #PARENT_EXTENSION}, which makes the generator emit {@code interface Child extends Parent}. The JSON on the wire
 * is unchanged: only the specification's description of it changes.
 * <p>
 * A schema is rewritten only when it repeats every field of the embedded schema exactly, with the same required fields. A member of a
 * discriminated union, which swagger-core writes as {@code allOf} of a back-reference to the union and its fields, keeps the
 * back-reference that {@code pruneOpenApiSpec} removes and gets the embedded schema as a second entry. A record that unwraps more than
 * one component keeps its flat schema. It runs before {@code OpenAPIConfiguration#schemaCustomizer}, which strips the DTO suffix from
 * the names this customizer works with.
 */
@Component
@Profile(PROFILE_CORE)
@Lazy
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class UnwrappedSchemaOpenApiCustomizer implements OpenApiCustomizer {

    /** The extension that marks a schema as a parent the generator may extend instead of copying its fields. */
    public static final String PARENT_EXTENSION = "x-parent";

    private static final String SCHEMA_REFERENCE_PREFIX = "#/components/schemas/";

    private static final String ARTEMIS_PACKAGE = "de.tum.cit.aet.artemis";

    @Override
    public void customise(OpenAPI openApi) {
        if (openApi.getComponents() != null && openApi.getComponents().getSchemas() != null) {
            Map<String, Schema> schemas = openApi.getComponents().getSchemas();
            describeEmbeddedSchemas(schemas, unwrappedTypes());
            composeUnwrappedSchemas(schemas, unwrappedSchemaNames());
        }
    }

    /**
     * Adds the schema of an embedded record marked {@link DescribedWhenUnwrapped}, which no endpoint refers to by itself. springdoc keeps
     * only the schemas something references, so a record that exists only to be unwrapped, such as the counts two summaries share, is
     * missing and nothing can be composed of it.
     *
     * @param schemas       the component schemas, changed in place
     * @param parentByChild the embedded record by the name of the embedding schema, as {@link #unwrappedTypes()} returns it
     */
    @SuppressWarnings("rawtypes")
    static void describeEmbeddedSchemas(Map<String, Schema> schemas, Map<String, Class<?>> parentByChild) {
        parentByChild.forEach((childName, parentType) -> {
            if (parentType.isAnnotationPresent(DescribedWhenUnwrapped.class) && schemas.containsKey(childName) && !schemas.containsKey(schemaName(parentType))) {
                var resolved = ModelConverters.getInstance(true).resolveAsResolvedSchema(new AnnotatedType(parentType).resolveAsRef(true));
                resolved.referencedSchemas.forEach(schemas::putIfAbsent);
            }
        });
    }

    /**
     * Finds the records that unwrap exactly one component, by the names swagger-core gives their schemas.
     *
     * @return the name of the embedded schema by the name of the embedding schema
     */
    public static Map<String, String> unwrappedSchemaNames() {
        Map<String, String> parentByChild = new TreeMap<>();
        unwrappedTypes().forEach((childName, parentType) -> parentByChild.put(childName, schemaName(parentType)));
        return parentByChild;
    }

    /**
     * Finds the records that unwrap exactly one component, by the names swagger-core gives their schemas.
     *
     * @return the type of the embedded record by the name of the embedding schema
     */
    public static Map<String, Class<?>> unwrappedTypes() {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(Record.class));
        Map<String, Class<?>> parentByChild = new TreeMap<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(ARTEMIS_PACKAGE)) {
            String className = definition.getBeanClassName();
            if (className == null) {
                continue;
            }
            Class<?> type = ClassUtils.resolveClassName(className, UnwrappedSchemaOpenApiCustomizer.class.getClassLoader());
            RecordComponent[] components = type.getRecordComponents();
            if (components == null) {
                continue;
            }
            // @JsonUnwrapped targets fields and methods, not record components, so Java puts it on the accessor.
            List<RecordComponent> unwrapped = Arrays.stream(components).filter(component -> {
                JsonUnwrapped annotation = component.getAccessor().getAnnotation(JsonUnwrapped.class);
                return annotation != null && annotation.enabled() && annotation.prefix().isEmpty() && annotation.suffix().isEmpty();
            }).toList();
            if (unwrapped.size() == 1) {
                parentByChild.put(schemaName(type), unwrapped.getFirst().getType());
            }
        }
        return parentByChild;
    }

    /**
     * Rewrites every schema that repeats the fields of the schema it embeds as {@code allOf} of that schema and its own fields.
     *
     * @param schemas       the component schemas, changed in place
     * @param parentByChild the name of the embedded schema by the name of the embedding schema, as {@link #unwrappedSchemaNames()} returns it
     */
    @SuppressWarnings("rawtypes")
    public static void composeUnwrappedSchemas(Map<String, Schema> schemas, Map<String, String> parentByChild) {
        // Decide on the schemas as swagger-core wrote them, so a chain of embeddings compares each child with its parent's own fields.
        Map<String, Schema<?>> composed = new LinkedHashMap<>();
        parentByChild.forEach((childName, parentName) -> {
            Schema<?> child = schemas.get(childName);
            Schema<?> parent = schemas.get(parentName);
            if (child != null && parent != null && repeatsFieldsOf(child, parent)) {
                composed.put(childName, compose(child, parentName, parent));
            }
        });
        composed.forEach((childName, schema) -> schemas.put(childName, schema));
        // Mark the parents only now: a schema can be a parent and a composed child, and the mark belongs on its final form.
        composed.keySet().forEach(childName -> schemas.get(parentByChild.get(childName)).addExtension(PARENT_EXTENSION, true));
    }

    private static boolean repeatsFieldsOf(Schema<?> child, Schema<?> parent) {
        Schema<?> fields = fieldsOf(child);
        if (fields == null || isComposedOrUnion(parent) || parent.getProperties() == null || parent.getProperties().isEmpty()) {
            return false;
        }
        Map<String, Schema> childProperties = fields.getProperties();
        boolean sameFields = parent.getProperties().entrySet().stream().allMatch(field -> field.getValue().equals(childProperties.get(field.getKey())));
        Set<String> childRequired = new HashSet<>(required(child));
        childRequired.addAll(required(fields));
        childRequired.retainAll(parent.getProperties().keySet());
        return sameFields && childRequired.equals(new HashSet<>(required(parent)));
    }

    /**
     * The part of a schema that lists its fields: the schema itself, or for a member of a discriminated union the entry next to the
     * back-reference. Null for any other shape, which keeps such a schema flat.
     */
    private static Schema<?> fieldsOf(Schema<?> schema) {
        if (schema.getOneOf() != null || schema.getAnyOf() != null || schema.getDiscriminator() != null) {
            return null;
        }
        if (schema.getAllOf() == null) {
            return schema.getProperties() == null ? null : schema;
        }
        List<Schema> parts = schema.getAllOf();
        if (parts.size() != 2 || parts.getFirst().get$ref() == null || parts.getLast().get$ref() != null || parts.getLast().getProperties() == null) {
            return null;
        }
        return parts.getLast();
    }

    private static List<String> required(Schema<?> schema) {
        return schema.getRequired() == null ? List.of() : schema.getRequired();
    }

    private static boolean isComposedOrUnion(Schema<?> schema) {
        return schema.getAllOf() != null || schema.getOneOf() != null || schema.getAnyOf() != null || schema.getDiscriminator() != null;
    }

    private static Schema<?> compose(Schema<?> child, String parentName, Schema<?> parent) {
        Set<String> inherited = parent.getProperties().keySet();
        Schema<?> fields = fieldsOf(child);
        ObjectSchema own = new ObjectSchema();
        fields.getProperties().forEach((name, property) -> {
            if (!inherited.contains(name)) {
                own.addProperty(name, property);
            }
        });
        required(fields).stream().filter(name -> !inherited.contains(name)).forEach(own::addRequiredItem);
        Schema<Object> composed = new Schema<>();
        if (fields != child) {
            // Keep the back-reference to the union first, where pruneOpenApiSpec looks for it.
            composed.addAllOfItem(child.getAllOf().getFirst());
        }
        composed.addAllOfItem(new Schema<>().$ref(SCHEMA_REFERENCE_PREFIX + parentName));
        composed.addAllOfItem(own);
        List<String> ownRequired = required(child).stream().filter(name -> !inherited.contains(name)).toList();
        if (fields != child && !ownRequired.isEmpty()) {
            composed.setRequired(ownRequired);
        }
        composed.setDescription(child.getDescription());
        return composed;
    }

    private static String schemaName(Class<?> type) {
        var schema = type.getAnnotation(io.swagger.v3.oas.annotations.media.Schema.class);
        return schema != null && !schema.name().isEmpty() ? schema.name() : type.getSimpleName();
    }
}
