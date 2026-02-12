package de.tum.cit.aet.artemis.globalsearch.config.schema;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WeaviateReferenceDefinitionTest {

    @Test
    void of_createsDefinitionWithCorrectValues() {
        var ref = WeaviateReferenceDefinition.of("courseRef", "Courses", "Reference to course");

        assertThat(ref.name()).isEqualTo("courseRef");
        assertThat(ref.targetCollection()).isEqualTo("Courses");
        assertThat(ref.description()).isEqualTo("Reference to course");
    }

    @Test
    void recordEquality_worksCorrectly() {
        var ref1 = WeaviateReferenceDefinition.of("ref", "Target", "desc");
        var ref2 = new WeaviateReferenceDefinition("ref", "Target", "desc");

        assertThat(ref1).isEqualTo(ref2);
    }
}
