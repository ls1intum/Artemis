package de.tum.cit.aet.artemis.globalsearch.config.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class WeaviateCollectionSchemaTest {

    private static final WeaviatePropertyDefinition PROPERTY_A = WeaviatePropertyDefinition.filterable("id", WeaviateDataType.INT, "ID");

    private static final WeaviatePropertyDefinition PROPERTY_B = WeaviatePropertyDefinition.searchable("name", WeaviateDataType.TEXT, "Name");

    @Test
    void of_withoutReferences_createsEmptyReferencesList() {
        var schema = WeaviateCollectionSchema.of("TestCollection", List.of(PROPERTY_A, PROPERTY_B));

        assertThat(schema.collectionName()).isEqualTo("TestCollection");
        assertThat(schema.properties()).containsExactly(PROPERTY_A, PROPERTY_B);
        assertThat(schema.references()).isEmpty();
    }

    @Test
    void of_withReferences_includesReferences() {
        var ref = WeaviateReferenceDefinition.of("courseRef", "Courses", "Reference to course");
        var schema = WeaviateCollectionSchema.of("TestCollection", List.of(PROPERTY_A), List.of(ref));

        assertThat(schema.references()).containsExactly(ref);
    }

    @Test
    void getProperty_returnsMatchingProperty() {
        var schema = WeaviateCollectionSchema.of("TestCollection", List.of(PROPERTY_A, PROPERTY_B));

        assertThat(schema.getProperty("id")).isEqualTo(PROPERTY_A);
        assertThat(schema.getProperty("name")).isEqualTo(PROPERTY_B);
    }

    @Test
    void getProperty_returnsNullForUnknownName() {
        var schema = WeaviateCollectionSchema.of("TestCollection", List.of(PROPERTY_A));

        assertThat(schema.getProperty("nonexistent")).isNull();
    }
}
