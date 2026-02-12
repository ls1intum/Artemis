package de.tum.cit.aet.artemis.globalsearch.config.schema;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WeaviatePropertyDefinitionTest {

    @Test
    void searchable_setsCorrectFlags() {
        var prop = WeaviatePropertyDefinition.searchable("title", WeaviateDataType.TEXT, "A title");

        assertThat(prop.name()).isEqualTo("title");
        assertThat(prop.dataType()).isEqualTo(WeaviateDataType.TEXT);
        assertThat(prop.indexSearchable()).isTrue();
        assertThat(prop.indexFilterable()).isFalse();
        assertThat(prop.description()).isEqualTo("A title");
    }

    @Test
    void filterable_setsCorrectFlags() {
        var prop = WeaviatePropertyDefinition.filterable("course_id", WeaviateDataType.INT, "Course ID");

        assertThat(prop.indexSearchable()).isFalse();
        assertThat(prop.indexFilterable()).isTrue();
    }

    @Test
    void nonSearchable_setsCorrectFlags() {
        var prop = WeaviatePropertyDefinition.nonSearchable("max_points", WeaviateDataType.NUMBER, "Max points");

        assertThat(prop.indexSearchable()).isFalse();
        assertThat(prop.indexFilterable()).isFalse();
    }
}
