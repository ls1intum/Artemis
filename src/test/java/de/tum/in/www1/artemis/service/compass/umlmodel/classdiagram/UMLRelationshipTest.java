package de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram;

import static de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLRelationship.UMLRelationshipType.*;
import static de.tum.in.www1.artemis.service.compass.utils.CompassConfiguration.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

class UMLRelationshipTest {

    private UMLRelationship relationship;

    @Spy
    private UMLRelationship referenceRelationship;

    @Spy
    private UMLClass source1;

    @Spy
    private UMLClass source2;

    @Spy
    private UMLClass target1;

    @Spy
    private UMLClass target2;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        relationship = new UMLRelationship(source1, target1, CLASS_BIDIRECTIONAL, "relationshipId", "sourceRole", "targetRole", "*", "0..1");

        doReturn(CLASS_BIDIRECTIONAL).when(referenceRelationship).getRelationshipType();
        mockReferenceRelationship("sourceRole", "targetRole", "*", "0..1", source2, target2);

        mockSimilarity(source1, source2, 1.0);
        mockSimilarity(source2, source1, 1.0);
        mockSimilarity(source1, target2, 1.0);
        mockSimilarity(source2, target1, 1.0);
    }

    @AfterEach
    void tearDown() {
        Mockito.reset(referenceRelationship, source1, source2, target1, target2);
    }

    private void mockSimilarity(UMLClass umlClass1, UMLClass umlClass2, double similarity) {
        doReturn(similarity).when(umlClass1).similarity(umlClass2);
    }

    @Test
    void similarity_null() {
        double similarity = relationship.similarity(null);
        assertThat(similarity).isZero();
    }

    @Test
    void similarity_differentElementType() {
        double similarity = relationship.similarity(mock(UMLClass.class));
        assertThat(similarity).isZero();
    }

    @Test
    void similarity_sameRelationship() {
        double similarity = relationship.similarity(referenceRelationship);
        assertThat(similarity).isEqualTo(1.0);
    }

    @Test
    void similarity_sameSymmetricBidirectionalRelationship() {
        mockReferenceRelationship("targetRole", "sourceRole", "0..1", "*", target2, source2);
        double similarity = relationship.similarity(referenceRelationship);
        similarity = Math.round(similarity * 1000000) / 1000000.0;
        assertThat(similarity).isEqualTo(1.0);
    }

    @Test
    void similarity_noSameSymmetricUnidirectionalRelationship() {
        relationship = new UMLRelationship(source1, target1, CLASS_UNIDIRECTIONAL, "relationshipId", "sourceRole", "targetRole", "*", "0..1");
        doReturn(CLASS_UNIDIRECTIONAL).when(referenceRelationship).getRelationshipType();
        mockReferenceRelationship("targetRole", "sourceRole", "0..1", "*", source2, target2);
        mockSimilarity(source1, source2, 0.0);
        mockSimilarity(source2, source1, 0.0);
        mockSimilarity(target1, target2, 0.0);
        mockSimilarity(target2, target1, 0.0);
        double similarity = relationship.similarity(referenceRelationship);
        assertThat(similarity).isNotEqualTo(1).isEqualTo(RELATION_TYPE_WEIGHT, offset(0.01));
    }

    private void mockReferenceRelationship(String sourceRole, String targetRole, String sourceMultiplicity, String targetMultiplicity, UMLClass source, UMLClass target) {
        doReturn(sourceRole).when(referenceRelationship).getSourceRole();
        doReturn(targetRole).when(referenceRelationship).getTargetRole();
        doReturn(sourceMultiplicity).when(referenceRelationship).getSourceMultiplicity();
        doReturn(targetMultiplicity).when(referenceRelationship).getTargetMultiplicity();
        doReturn(source).when(referenceRelationship).getSource();
        doReturn(target).when(referenceRelationship).getTarget();
    }

    @Test
    void similarity_differentSourceAndTargetElements() {
        mockSimilarity(source1, source2, 0.12);
        mockSimilarity(source2, source1, 0.12);
        mockSimilarity(source1, target2, 0.12);
        mockSimilarity(target2, source1, 0.12);

        mockSimilarity(target1, source2, 0.34);
        mockSimilarity(source2, target1, 0.34);
        mockSimilarity(target1, target2, 0.34);
        mockSimilarity(target2, target1, 0.34);
        double expectedSimilarity = RELATION_TYPE_WEIGHT + 0.12 * RELATION_ELEMENT_WEIGHT + 0.34 * RELATION_ELEMENT_WEIGHT + 2 * RELATION_ROLE_WEIGHT
                + 2 * RELATION_MULTIPLICITY_WEIGHT;
        double similarity = relationship.similarity(referenceRelationship);
        assertThat(similarity).isEqualTo(expectedSimilarity);
    }

    @Test
    void similarity_differentRelationshipType() {
        doReturn(CLASS_UNIDIRECTIONAL).when(referenceRelationship).getRelationshipType();
        double expectedSimilarity = 2 * RELATION_ELEMENT_WEIGHT + 2 * RELATION_ROLE_WEIGHT + 2 * RELATION_MULTIPLICITY_WEIGHT;
        double similarity = relationship.similarity(referenceRelationship);
        expectedSimilarity = Math.round(expectedSimilarity * 1000000) / 1000000.0;
        assertThat(similarity).isEqualTo(expectedSimilarity, offset(0.01));
    }

    @Test
    void similarity_differentSourceRole() {
        doReturn("differentRole").when(referenceRelationship).getSourceRole();
        double expectedSimilarity = RELATION_TYPE_WEIGHT + 2 * RELATION_ELEMENT_WEIGHT + RELATION_ROLE_WEIGHT + 2 * RELATION_MULTIPLICITY_WEIGHT;
        double similarity = relationship.similarity(referenceRelationship);
        assertThat(similarity).isEqualTo(expectedSimilarity, offset(0.01));
    }

    @Test
    void similarity_differentTargetRole() {
        doReturn("differentRole").when(referenceRelationship).getTargetRole();
        double expectedSimilarity = RELATION_TYPE_WEIGHT + 2 * RELATION_ELEMENT_WEIGHT + RELATION_ROLE_WEIGHT + 2 * RELATION_MULTIPLICITY_WEIGHT;
        double similarity = relationship.similarity(referenceRelationship);
        assertThat(similarity).isEqualTo(expectedSimilarity, offset(0.01));
    }

    @Test
    void similarity_differentSourceMultiplicity() {
        doReturn("differentMultiplicity").when(referenceRelationship).getSourceMultiplicity();
        double expectedSimilarity = RELATION_TYPE_WEIGHT + 2 * RELATION_ELEMENT_WEIGHT + 2 * RELATION_ROLE_WEIGHT + RELATION_MULTIPLICITY_WEIGHT;
        double similarity = relationship.similarity(referenceRelationship);
        assertThat(similarity).isEqualTo(expectedSimilarity, offset(0.01));
    }

    @Test
    void similarity_differentTargetMultiplicity() {
        doReturn("differentMultiplicity").when(referenceRelationship).getTargetMultiplicity();
        double expectedSimilarity = RELATION_TYPE_WEIGHT + 2 * RELATION_ELEMENT_WEIGHT + 2 * RELATION_ROLE_WEIGHT + RELATION_MULTIPLICITY_WEIGHT;
        double similarity = relationship.similarity(referenceRelationship);
        assertThat(similarity).isEqualTo(expectedSimilarity, offset(0.01));
    }

    @Test
    void similarity_nullReferenceValues() {
        mockReferenceRelationship(null, null, null, null, source2, target2);
        double expectedSimilarity = RELATION_TYPE_WEIGHT + 2 * RELATION_ELEMENT_WEIGHT;
        double similarity = relationship.similarity(referenceRelationship);
        assertThat(similarity).isEqualTo(expectedSimilarity);
    }

    @Test
    void similarity_emptyReferenceValues() {
        mockReferenceRelationship("", "", "", "", source2, target2);
        double expectedSimilarity = RELATION_TYPE_WEIGHT + 2 * RELATION_ELEMENT_WEIGHT;
        double similarity = relationship.similarity(referenceRelationship);
        assertThat(similarity).isEqualTo(expectedSimilarity);
    }

    @Test
    void similarity_missingAndDifferentValues() {
        relationship = new UMLRelationship(source1, target1, CLASS_BIDIRECTIONAL, "relationshipId", "", "targetRole", "different", "");
        doReturn("").when(referenceRelationship).getSourceRole();
        doReturn("").when(referenceRelationship).getTargetMultiplicity();
        mockSimilarity(target1, target2, 0.42);
        mockSimilarity(target2, target1, 0.42);
        double expectedSimilarity = RELATION_TYPE_WEIGHT + RELATION_ELEMENT_WEIGHT + 0.42 * RELATION_ELEMENT_WEIGHT + RELATION_ROLE_WEIGHT + RELATION_MULTIPLICITY_WEIGHT
                + RELATION_MULTIPLICITY_WEIGHT;

        double similarity = relationship.similarity(referenceRelationship);

        assertThat(similarity).isEqualTo(expectedSimilarity, offset(0.01));
    }
}
