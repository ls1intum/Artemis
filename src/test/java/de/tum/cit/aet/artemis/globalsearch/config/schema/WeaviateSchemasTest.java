package de.tum.cit.aet.artemis.globalsearch.config.schema;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.globalsearch.config.schema.entitySchemas.ExerciseSchema;

class WeaviateSchemasTest {

    @Test
    void allSchemas_containsExerciseSchema() {
        assertThat(WeaviateSchemas.ALL_SCHEMAS).contains(ExerciseSchema.SCHEMA);
    }

    @Test
    void getSchema_returnsExerciseSchemaByName() {
        assertThat(WeaviateSchemas.getSchema(ExerciseSchema.COLLECTION_NAME)).isEqualTo(ExerciseSchema.SCHEMA);
    }

    @Test
    void getSchema_returnsNullForUnknownName() {
        assertThat(WeaviateSchemas.getSchema("NonexistentCollection")).isNull();
    }

    @Test
    void schemasByName_hasSameSizeAsAllSchemas() {
        assertThat(WeaviateSchemas.SCHEMAS_BY_NAME).hasSameSizeAs(WeaviateSchemas.ALL_SCHEMAS);
    }
}
