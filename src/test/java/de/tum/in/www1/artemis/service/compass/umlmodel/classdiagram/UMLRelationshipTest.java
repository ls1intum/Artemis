package de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram;

import static de.tum.in.www1.artemis.service.compass.utils.CompassConfiguration.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.internal.util.reflection.FieldSetter;

import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLRelationship.UMLRelationshipType;

class UMLRelationshipTest {

    private UMLRelationship relationship;

    @Mock
    UMLRelationship referenceRelationship;

    @Mock
    UMLClass source1;

    @Mock
    UMLClass source2;

    @Mock
    UMLClass target1;

    @Mock
    UMLClass target2;

    @BeforeEach
    void setUp() throws NoSuchFieldException {
        MockitoAnnotations.initMocks(this);

        relationship = new UMLRelationship(source1, target1, UMLRelationshipType.CLASS_BIDIRECTIONAL, "relationshipId", "sourceRole", "targetRole", "*", "0..1");

        FieldSetter.setField(referenceRelationship, UMLRelationship.class.getDeclaredField("type"), UMLRelationshipType.CLASS_BIDIRECTIONAL);
        FieldSetter.setField(referenceRelationship, UMLRelationship.class.getDeclaredField("sourceRole"), "sourceRole");
        FieldSetter.setField(referenceRelationship, UMLRelationship.class.getDeclaredField("targetRole"), "targetRole");
        FieldSetter.setField(referenceRelationship, UMLRelationship.class.getDeclaredField("sourceMultiplicity"), "*");
        FieldSetter.setField(referenceRelationship, UMLRelationship.class.getDeclaredField("targetMultiplicity"), "0..1");
        when(referenceRelationship.getSource()).thenReturn(source2);
        when(referenceRelationship.getTarget()).thenReturn(target2);

        when(source1.similarity(source2)).thenReturn(1.0);
        when(source2.similarity(source1)).thenReturn(1.0);
        when(target1.similarity(target2)).thenReturn(1.0);
        when(target2.similarity(target1)).thenReturn(1.0);
    }

    @Test
    void similarity_null() {
        double similarity = relationship.similarity(null);

        assertThat(similarity).isEqualTo(0);
    }

    @Test
    void similarity_differentElementType() {
        double similarity = relationship.similarity(mock(UMLClass.class));

        assertThat(similarity).isEqualTo(0);
    }

    @Test
    void similarity_sameRelationship() {
        double similarity = relationship.similarity(referenceRelationship);

        assertThat(similarity).isEqualTo(1);
    }

    @Test
    void similarity_sameSymmetricBidirectionalRelationship() throws NoSuchFieldException {
        FieldSetter.setField(referenceRelationship, UMLRelationship.class.getDeclaredField("sourceRole"), "targetRole");
        FieldSetter.setField(referenceRelationship, UMLRelationship.class.getDeclaredField("targetRole"), "sourceRole");
        FieldSetter.setField(referenceRelationship, UMLRelationship.class.getDeclaredField("sourceMultiplicity"), "0..1");
        FieldSetter.setField(referenceRelationship, UMLRelationship.class.getDeclaredField("targetMultiplicity"), "*");
        when(referenceRelationship.getSource()).thenReturn(target2);
        when(referenceRelationship.getTarget()).thenReturn(source2);

        double similarity = relationship.similarity(referenceRelationship);

        similarity = Math.round(similarity * 1000000) / 1000000.0;
        assertThat(similarity).isEqualTo(1);
    }

    @Test
    void similarity_noSameSymmetricUnidirectionalRelationship() throws NoSuchFieldException {
        relationship = new UMLRelationship(source1, target1, UMLRelationshipType.CLASS_UNIDIRECTIONAL, "relationshipId", "sourceRole", "targetRole", "*", "0..1");
        FieldSetter.setField(referenceRelationship, UMLRelationship.class.getDeclaredField("type"), UMLRelationshipType.CLASS_UNIDIRECTIONAL);
        FieldSetter.setField(referenceRelationship, UMLRelationship.class.getDeclaredField("sourceRole"), "targetRole");
        FieldSetter.setField(referenceRelationship, UMLRelationship.class.getDeclaredField("targetRole"), "sourceRole");
        FieldSetter.setField(referenceRelationship, UMLRelationship.class.getDeclaredField("sourceMultiplicity"), "0..1");
        FieldSetter.setField(referenceRelationship, UMLRelationship.class.getDeclaredField("targetMultiplicity"), "*");
        when(referenceRelationship.getSource()).thenReturn(target2);
        when(referenceRelationship.getTarget()).thenReturn(source2);
        double expectedWeight = 1 + 2 * RELATION_ROLE_OPTIONAL_WEIGHT + 2 * RELATION_MULTIPLICITY_OPTIONAL_WEIGHT;
        double expectedSimilarity = RELATION_TYPE_WEIGHT / expectedWeight;

        double similarity = relationship.similarity(referenceRelationship);

        assertThat(similarity).isNotEqualTo(1);
        assertThat(similarity).isEqualTo(expectedSimilarity);
    }

    @Test
    void similarity_differentSourceAndTargetElements() throws NoSuchFieldException {
        when(source1.similarity(source2)).thenReturn(0.12);
        when(source2.similarity(source1)).thenReturn(0.12);
        when(target1.similarity(target2)).thenReturn(0.34);
        when(target2.similarity(target1)).thenReturn(0.34);
        double expectedWeight = 1 + 2 * RELATION_ROLE_OPTIONAL_WEIGHT + 2 * RELATION_MULTIPLICITY_OPTIONAL_WEIGHT;
        double expectedSimilarity = (RELATION_TYPE_WEIGHT + 0.12 * RELATION_ELEMENT_WEIGHT + 0.34 * RELATION_ELEMENT_WEIGHT + 2 * RELATION_ROLE_OPTIONAL_WEIGHT
                + 2 * RELATION_MULTIPLICITY_OPTIONAL_WEIGHT) / expectedWeight;

        double similarity = relationship.similarity(referenceRelationship);

        assertThat(similarity).isEqualTo(expectedSimilarity);
    }

    @Test
    void similarity_differentRelationshipType() throws NoSuchFieldException {
        FieldSetter.setField(referenceRelationship, UMLRelationship.class.getDeclaredField("type"), UMLRelationshipType.CLASS_UNIDIRECTIONAL);
        double expectedWeight = 1 + 2 * RELATION_ROLE_OPTIONAL_WEIGHT + 2 * RELATION_MULTIPLICITY_OPTIONAL_WEIGHT;
        double expectedSimilarity = (2 * RELATION_ELEMENT_WEIGHT + 2 * RELATION_ROLE_OPTIONAL_WEIGHT + 2 * RELATION_MULTIPLICITY_OPTIONAL_WEIGHT) / expectedWeight;

        double similarity = relationship.similarity(referenceRelationship);

        expectedSimilarity = Math.round(expectedSimilarity * 1000000) / 1000000.0;
        assertThat(similarity).isEqualTo(expectedSimilarity);
    }

    @Test
    void similarity_differentSourceRole() throws NoSuchFieldException {
        FieldSetter.setField(referenceRelationship, UMLRelationship.class.getDeclaredField("sourceRole"), "differentRole");
        double expectedWeight = 1 + 2 * RELATION_ROLE_OPTIONAL_WEIGHT + 2 * RELATION_MULTIPLICITY_OPTIONAL_WEIGHT;
        double expectedSimilarity = (RELATION_TYPE_WEIGHT + 2 * RELATION_ELEMENT_WEIGHT + RELATION_ROLE_OPTIONAL_WEIGHT + 2 * RELATION_MULTIPLICITY_OPTIONAL_WEIGHT)
                / expectedWeight;

        double similarity = relationship.similarity(referenceRelationship);

        assertThat(similarity).isEqualTo(expectedSimilarity);
    }

    @Test
    void similarity_differentTargetRole() throws NoSuchFieldException {
        FieldSetter.setField(referenceRelationship, UMLRelationship.class.getDeclaredField("targetRole"), "differentRole");
        double expectedWeight = 1 + 2 * RELATION_ROLE_OPTIONAL_WEIGHT + 2 * RELATION_MULTIPLICITY_OPTIONAL_WEIGHT;
        double expectedSimilarity = (RELATION_TYPE_WEIGHT + 2 * RELATION_ELEMENT_WEIGHT + RELATION_ROLE_OPTIONAL_WEIGHT + 2 * RELATION_MULTIPLICITY_OPTIONAL_WEIGHT)
                / expectedWeight;

        double similarity = relationship.similarity(referenceRelationship);

        assertThat(similarity).isEqualTo(expectedSimilarity);
    }

    @Test
    void similarity_differentSourceMultiplicity() throws NoSuchFieldException {
        FieldSetter.setField(referenceRelationship, UMLRelationship.class.getDeclaredField("sourceMultiplicity"), "differentMultiplicity");
        double expectedWeight = 1 + 2 * RELATION_ROLE_OPTIONAL_WEIGHT + 2 * RELATION_MULTIPLICITY_OPTIONAL_WEIGHT;
        double expectedSimilarity = (RELATION_TYPE_WEIGHT + 2 * RELATION_ELEMENT_WEIGHT + 2 * RELATION_ROLE_OPTIONAL_WEIGHT + RELATION_MULTIPLICITY_OPTIONAL_WEIGHT)
                / expectedWeight;

        double similarity = relationship.similarity(referenceRelationship);

        assertThat(similarity).isEqualTo(expectedSimilarity);
    }

    @Test
    void similarity_differentTargetMultiplicity() throws NoSuchFieldException {
        FieldSetter.setField(referenceRelationship, UMLRelationship.class.getDeclaredField("targetMultiplicity"), "differentMultiplicity");
        double expectedWeight = 1 + 2 * RELATION_ROLE_OPTIONAL_WEIGHT + 2 * RELATION_MULTIPLICITY_OPTIONAL_WEIGHT;
        double expectedSimilarity = (RELATION_TYPE_WEIGHT + 2 * RELATION_ELEMENT_WEIGHT + 2 * RELATION_ROLE_OPTIONAL_WEIGHT + RELATION_MULTIPLICITY_OPTIONAL_WEIGHT)
                / expectedWeight;

        double similarity = relationship.similarity(referenceRelationship);

        assertThat(similarity).isEqualTo(expectedSimilarity);
    }

    @Test
    void similarity_nullReferenceValues() throws NoSuchFieldException {
        FieldSetter.setField(referenceRelationship, UMLRelationship.class.getDeclaredField("sourceRole"), null);
        FieldSetter.setField(referenceRelationship, UMLRelationship.class.getDeclaredField("targetRole"), null);
        FieldSetter.setField(referenceRelationship, UMLRelationship.class.getDeclaredField("sourceMultiplicity"), null);
        FieldSetter.setField(referenceRelationship, UMLRelationship.class.getDeclaredField("targetMultiplicity"), null);
        double expectedWeight = 1 + 2 * RELATION_ROLE_OPTIONAL_WEIGHT + 2 * RELATION_MULTIPLICITY_OPTIONAL_WEIGHT;
        double expectedSimilarity = (RELATION_TYPE_WEIGHT + 2 * RELATION_ELEMENT_WEIGHT) / expectedWeight;

        double similarity = relationship.similarity(referenceRelationship);

        assertThat(similarity).isEqualTo(expectedSimilarity);
    }

    @Test
    void similarity_emptyReferenceValues() throws NoSuchFieldException {
        FieldSetter.setField(referenceRelationship, UMLRelationship.class.getDeclaredField("sourceRole"), "");
        FieldSetter.setField(referenceRelationship, UMLRelationship.class.getDeclaredField("targetRole"), "");
        FieldSetter.setField(referenceRelationship, UMLRelationship.class.getDeclaredField("sourceMultiplicity"), "");
        FieldSetter.setField(referenceRelationship, UMLRelationship.class.getDeclaredField("targetMultiplicity"), "");
        double expectedWeight = 1 + 2 * RELATION_ROLE_OPTIONAL_WEIGHT + 2 * RELATION_MULTIPLICITY_OPTIONAL_WEIGHT;
        double expectedSimilarity = (RELATION_TYPE_WEIGHT + 2 * RELATION_ELEMENT_WEIGHT) / expectedWeight;

        double similarity = relationship.similarity(referenceRelationship);

        assertThat(similarity).isEqualTo(expectedSimilarity);
    }

    @Test
    void similarity_missingAndDifferentValues() throws NoSuchFieldException {
        relationship = new UMLRelationship(source1, target1, UMLRelationshipType.CLASS_BIDIRECTIONAL, "relationshipId", "", "targetRole", "different", "");
        FieldSetter.setField(referenceRelationship, UMLRelationship.class.getDeclaredField("sourceRole"), "");
        FieldSetter.setField(referenceRelationship, UMLRelationship.class.getDeclaredField("targetMultiplicity"), "");
        when(target1.similarity(target2)).thenReturn(0.42);
        when(target2.similarity(target1)).thenReturn(0.42);
        double expectedWeight = 1 + RELATION_ROLE_OPTIONAL_WEIGHT + RELATION_MULTIPLICITY_OPTIONAL_WEIGHT;
        double expectedSimilarity = (RELATION_TYPE_WEIGHT + RELATION_ELEMENT_WEIGHT + 0.42 * RELATION_ELEMENT_WEIGHT + RELATION_ROLE_OPTIONAL_WEIGHT) / expectedWeight;

        double similarity = relationship.similarity(referenceRelationship);

        assertThat(similarity).isEqualTo(expectedSimilarity);
    }
}
