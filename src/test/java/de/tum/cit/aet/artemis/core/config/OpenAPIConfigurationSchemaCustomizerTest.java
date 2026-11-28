package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.OpenAPIConfiguration;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;

/**
 * Pins which component schemas {@link OpenAPIConfiguration#schemaCustomizer()} keeps in the application specification.
 */
class OpenAPIConfigurationSchemaCustomizerTest {

    private static final String REFERENCE_PREFIX = "#/components/schemas/";

    @Test
    void keepsDtoSchemasWithoutTheirSuffixAndEnumSchemasUnderTheirOwnName() {
        Schema<?> exercise = new ObjectSchema().addProperty("difficulty", new Schema<>().$ref(REFERENCE_PREFIX + "DifficultyLevel")).addProperty("course",
                new Schema<>().$ref(REFERENCE_PREFIX + "CourseDTO"));
        Schema<?> difficulty = new StringSchema()._enum(List.of("EASY", "MEDIUM", "HARD"));
        OpenAPI openApi = new OpenAPI().paths(new Paths())
                .components(new Components().schemas(new TreeMap<>(Map.of("ExerciseDTO", exercise, "CourseDTO", new ObjectSchema().addProperty("title", new StringSchema()),
                        "DifficultyLevel", difficulty, "Exercise", new ObjectSchema(), "EmptyEnum", new StringSchema()._enum(List.of()), "EnumWithProperties",
                        new ObjectSchema().addProperty("x", new StringSchema())))));

        customizer().schemaCustomizer().customise(openApi);

        Map<String, Schema> schemas = openApi.getComponents().getSchemas();
        // A DTO loses its suffix and an enum keeps its name; other entity schemas are not part of the specification.
        assertThat(schemas).containsOnlyKeys("Exercise", "Course", "DifficultyLevel");
        assertThat(schemas.get("Exercise")).isSameAs(exercise);
        assertThat(schemas.get("Exercise").getName()).isEqualTo("Exercise");
        assertThat(schemas.get("DifficultyLevel")).isSameAs(difficulty);
        assertThat(schemas.get("DifficultyLevel").getName()).isEqualTo("DifficultyLevel");
        // A reference to a DTO loses the suffix, a reference to an enum stays as it is.
        Map<String, Schema> properties = exercise.getProperties();
        assertThat(properties.get("course").get$ref()).isEqualTo(REFERENCE_PREFIX + "Course");
        assertThat(properties.get("difficulty").get$ref()).isEqualTo(REFERENCE_PREFIX + "DifficultyLevel");
    }

    private static OpenAPIConfiguration customizer() {
        OpenAPIConfiguration configuration = new OpenAPIConfiguration();
        ReflectionTestUtils.setField(configuration, "version", "1.0.0");
        return configuration;
    }
}
