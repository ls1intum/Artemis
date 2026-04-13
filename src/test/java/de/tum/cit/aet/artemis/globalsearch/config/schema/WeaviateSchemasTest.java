package de.tum.cit.aet.artemis.globalsearch.config.schema;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableItemSchema;

class WeaviateSchemasTest {

    @Test
    void allSchemas_containsSearchableItemSchema() {
        assertThat(WeaviateSchemas.ALL_SCHEMAS).contains(SearchableItemSchema.SCHEMA);
    }

    @Test
    void getSchema_returnsSearchableItemSchemaByName() {
        assertThat(WeaviateSchemas.getSchema(SearchableItemSchema.COLLECTION_NAME)).isEqualTo(SearchableItemSchema.SCHEMA);
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
