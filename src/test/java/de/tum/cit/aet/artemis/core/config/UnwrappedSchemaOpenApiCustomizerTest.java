package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import io.swagger.v3.oas.models.media.Discriminator;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;

/**
 * Pins which schemas {@link UnwrappedSchemaOpenApiCustomizer} describes as the schema they embed plus their own fields.
 */
@SuppressWarnings("rawtypes")
class UnwrappedSchemaOpenApiCustomizerTest {

    private static final String REFERENCE_PREFIX = "#/components/schemas/";

    @Test
    void findsTheRecordsThatUnwrapOneComponent() {
        Map<String, String> parentByChild = UnwrappedSchemaOpenApiCustomizer.unwrappedSchemaNames();

        assertThat(parentByChild).containsEntry("QuizPointStatisticsDTO", "QuizExerciseWithoutQuestionsDTO").containsEntry("QuizBatchWithPasswordDTO", "QuizBatchDTO");
        // Records that unwrap two components keep their flat schema, so they are not listed.
        assertThat(parentByChild).doesNotContainKey("MultipleChoiceQuizQuestionWithSolutionDTO");
    }

    @Test
    void describesAChildThatRepeatsItsParentAsTheParentPlusItsOwnFields() {
        Map<String, Schema> schemas = schemas(Map.of("BatchDTO", batch(), "BatchWithPasswordDTO", batch().addProperty("password", new StringSchema())));

        UnwrappedSchemaOpenApiCustomizer.composeUnwrappedSchemas(schemas, Map.of("BatchWithPasswordDTO", "BatchDTO"));

        Schema<?> child = schemas.get("BatchWithPasswordDTO");
        assertThat(child.getProperties()).isNull();
        assertThat(child.getAllOf()).hasSize(2);
        assertThat(child.getAllOf().getFirst().get$ref()).isEqualTo(REFERENCE_PREFIX + "BatchDTO");
        assertThat(child.getAllOf().getLast().getProperties()).containsOnlyKeys("password");
        assertThat(schemas.get("BatchDTO").getExtensions()).containsEntry(UnwrappedSchemaOpenApiCustomizer.PARENT_EXTENSION, true);
    }

    @Test
    void keepsTheRequiredFieldsOfTheChildOnItsOwnPart() {
        Schema<?> child = batch().addProperty("password", new StringSchema());
        child.setRequired(List.of("id", "password"));
        Schema<?> parent = batch();
        parent.setRequired(List.of("id"));
        Map<String, Schema> schemas = schemas(Map.of("BatchDTO", parent, "BatchWithPasswordDTO", child));

        UnwrappedSchemaOpenApiCustomizer.composeUnwrappedSchemas(schemas, Map.of("BatchWithPasswordDTO", "BatchDTO"));

        assertThat(parts(schemas, "BatchWithPasswordDTO").getLast().getRequired()).containsExactly("password");
    }

    @Test
    void leavesAChildFlatWhenItDescribesAnInheritedFieldDifferently() {
        Schema<?> child = new ObjectSchema().addProperty("id", new StringSchema()).addProperty("started", new Schema<>().type("boolean")).addProperty("password",
                new StringSchema());
        Map<String, Schema> schemas = schemas(Map.of("BatchDTO", batch(), "BatchWithPasswordDTO", child));

        UnwrappedSchemaOpenApiCustomizer.composeUnwrappedSchemas(schemas, Map.of("BatchWithPasswordDTO", "BatchDTO"));

        assertThat(schemas.get("BatchWithPasswordDTO")).isSameAs(child);
        assertThat(schemas.get("BatchDTO").getExtensions()).isNull();
    }

    @Test
    void leavesAChildFlatWhenItRequiresAFieldTheParentDoesNot() {
        Schema<?> child = batch().addProperty("password", new StringSchema());
        child.setRequired(List.of("id"));
        Map<String, Schema> schemas = schemas(Map.of("BatchDTO", batch(), "BatchWithPasswordDTO", child));

        UnwrappedSchemaOpenApiCustomizer.composeUnwrappedSchemas(schemas, Map.of("BatchWithPasswordDTO", "BatchDTO"));

        assertThat(schemas.get("BatchWithPasswordDTO")).isSameAs(child);
    }

    @Test
    void keepsTheUnionBackReferenceOfAMemberFirstAndAddsTheEmbeddedSchema() {
        Schema<?> child = new Schema<>().allOf(List.of(new Schema<>().$ref(REFERENCE_PREFIX + "ResponseDTO"), batch().addProperty("type", new StringSchema())));
        child.setRequired(List.of("type"));
        Schema<?> union = new Schema<>().discriminator(new Discriminator().propertyName("type"));
        Map<String, Schema> schemas = schemas(Map.of("BatchDTO", batch(), "LiveBatchDTO", child, "ResponseDTO", union));

        UnwrappedSchemaOpenApiCustomizer.composeUnwrappedSchemas(schemas, Map.of("LiveBatchDTO", "BatchDTO"));

        List<Schema<?>> parts = parts(schemas, "LiveBatchDTO");
        // pruneOpenApiSpec removes the back-reference to the union, which leaves the member extending the embedded schema.
        assertThat(parts).extracting(Schema::get$ref).containsExactly(REFERENCE_PREFIX + "ResponseDTO", REFERENCE_PREFIX + "BatchDTO", null);
        assertThat(parts.getLast().getProperties()).containsOnlyKeys("type");
        assertThat(schemas.get("LiveBatchDTO").getRequired()).containsExactly("type");
    }

    @Test
    void leavesAUnionItselfAlone() {
        Schema<?> union = batch().discriminator(new Discriminator().propertyName("type"));
        Map<String, Schema> schemas = schemas(Map.of("BatchDTO", batch(), "ResponseDTO", union));

        UnwrappedSchemaOpenApiCustomizer.composeUnwrappedSchemas(schemas, Map.of("ResponseDTO", "BatchDTO"));

        assertThat(schemas.get("ResponseDTO")).isSameAs(union);
    }

    @Test
    void composesEachLevelOfAChainWithTheFieldsSwaggerWroteForIt() {
        Map<String, Schema> schemas = schemas(Map.of("BatchDTO", batch(), "BatchWithPasswordDTO", batch().addProperty("password", new StringSchema()), "BatchWithHintDTO",
                batch().addProperty("password", new StringSchema()).addProperty("hint", new StringSchema())));

        UnwrappedSchemaOpenApiCustomizer.composeUnwrappedSchemas(schemas, Map.of("BatchWithPasswordDTO", "BatchDTO", "BatchWithHintDTO", "BatchWithPasswordDTO"));

        assertThat(parts(schemas, "BatchWithHintDTO").getFirst().get$ref()).isEqualTo(REFERENCE_PREFIX + "BatchWithPasswordDTO");
        assertThat(parts(schemas, "BatchWithHintDTO").getLast().getProperties()).containsOnlyKeys("hint");
        assertThat(parts(schemas, "BatchWithPasswordDTO").getLast().getProperties()).containsOnlyKeys("password");
        assertThat(schemas.get("BatchWithPasswordDTO").getExtensions()).containsEntry(UnwrappedSchemaOpenApiCustomizer.PARENT_EXTENSION, true);
    }

    private static List<Schema<?>> parts(Map<String, Schema> schemas, String name) {
        Schema<?> schema = schemas.get(name);
        return schema.getAllOf().stream().<Schema<?>>map(part -> part).toList();
    }

    private static Schema<?> batch() {
        return new ObjectSchema().addProperty("id", new IntegerSchema().format("int64")).addProperty("started", new Schema<>().type("boolean"));
    }

    private static Map<String, Schema> schemas(Map<String, Schema<?>> schemas) {
        return new TreeMap<>(schemas);
    }
}
