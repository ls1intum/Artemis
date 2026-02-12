package de.tum.cit.aet.artemis.globalsearch.config.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class WeaviateCollectionSchemaTest {

    private static final WeaviatePropertyDefinition PROP_A = WeaviatePropertyDefinition.filterable("id", WeaviateDataType.INT, "ID");

    private static final WeaviatePropertyDefinition PROP_B = WeaviatePropertyDefinition.searchable("name", WeaviateDataType.TEXT, "Name");

    @Test
    void of_withoutReferences_createsEmptyReferencesList() {
        var schema = WeaviateCollectionSchema.of("TestCollection", List.of(PROP_A, PROP_B));

        assertThat(schema.collectionName()).isEqualTo("TestCollection");
        assertThat(schema.properties()).containsExactly(PROP_A, PROP_B);
        assertThat(schema.references()).isEmpty();
    }

    @Test
    void of_withReferences_includesReferences() {
        var ref = WeaviateReferenceDefinition.of("courseRef", "Courses", "Reference to course");
        var schema = WeaviateCollectionSchema.of("TestCollection", List.of(PROP_A), List.of(ref));

        assertThat(schema.references()).containsExactly(ref);
    }

    @Test
    void getProperty_returnsMatchingProperty() {
        var schema = WeaviateCollectionSchema.of("TestCollection", List.of(PROP_A, PROP_B));

        assertThat(schema.getProperty("id")).isEqualTo(PROP_A);
        assertThat(schema.getProperty("name")).isEqualTo(PROP_B);
    }

    @Test
    void getProperty_returnsNullForUnknownName() {
        var schema = WeaviateCollectionSchema.of("TestCollection", List.of(PROP_A));

        assertThat(schema.getProperty("nonexistent")).isNull();
    }
}
