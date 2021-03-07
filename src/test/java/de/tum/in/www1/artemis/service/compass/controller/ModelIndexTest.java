package de.tum.in.www1.artemis.service.compass.controller;

import static de.tum.in.www1.artemis.service.compass.utils.CompassConfiguration.EQUALITY_THRESHOLD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;

@ExtendWith(MockitoExtension.class)
class ModelIndexTest {

    private ModelIndex modelIndex;

    @Mock
    UMLElement umlElement1;

    @Mock
    UMLElement umlElement2;

    @Mock
    UMLElement umlElement3;

    @Mock
    UMLElement umlElement4;

    @BeforeEach
    void setUp() {
        // modelIndex = new ModelIndex();
    }

    @Test
    void retrieveSimilarityId_sameElementTwice() {
        int similarityId1 = modelIndex.setSimilarityId(umlElement1);
        int similarityId2 = modelIndex.setSimilarityId(umlElement1);

        assertThat(similarityId1).isEqualTo(0);
        assertThat(similarityId2).isEqualTo(0);
        assertThat(modelIndex.getElementSimilarityMap().keySet()).containsExactly(umlElement1);
        assertThat(modelIndex.getNumberOfUniqueElements()).isEqualTo(1);
    }

    @Test
    void retrieveSimilarityId_twoSimilarElements() {
        mockSimilarityBetweenElements(umlElement2, umlElement1, EQUALITY_THRESHOLD / 2);
        mockSimilarityBetweenElements(umlElement3, umlElement1, EQUALITY_THRESHOLD / 2);
        mockSimilarityBetweenElements(umlElement3, umlElement2, EQUALITY_THRESHOLD / 2);
        mockSimilarityBetweenElements(umlElement4, umlElement1, EQUALITY_THRESHOLD / 2);
        mockSimilarityBetweenElements(umlElement4, umlElement2, EQUALITY_THRESHOLD + 0.01);
        mockSimilarityBetweenElements(umlElement4, umlElement3, EQUALITY_THRESHOLD / 2);

        int similarityId1 = modelIndex.setSimilarityId(umlElement1);
        int similarityId2 = modelIndex.setSimilarityId(umlElement2);
        when(umlElement2.getSimilarityID()).thenReturn(similarityId2);
        int similarityId3 = modelIndex.setSimilarityId(umlElement3);
        int similarityId4 = modelIndex.setSimilarityId(umlElement4);

        assertThat(similarityId1).isEqualTo(0);
        assertThat(similarityId2).isEqualTo(1);
        assertThat(similarityId3).isEqualTo(2);
        assertThat(similarityId4).isEqualTo(1);
        assertThat(modelIndex.getElementSimilarityMap().keySet()).containsExactlyInAnyOrder(umlElement1, umlElement2, umlElement3, umlElement4);
        assertThat(modelIndex.getNumberOfUniqueElements()).isEqualTo(3);
    }

    @Test
    void retrieveSimilarityId_multipleSimilarElements() {
        mockSimilarityBetweenElements(umlElement2, umlElement1, EQUALITY_THRESHOLD / 2);
        mockSimilarityBetweenElements(umlElement3, umlElement1, EQUALITY_THRESHOLD / 2);
        mockSimilarityBetweenElements(umlElement3, umlElement2, EQUALITY_THRESHOLD / 2);
        mockSimilarityBetweenElements(umlElement4, umlElement1, EQUALITY_THRESHOLD + 0.01);
        mockSimilarityBetweenElements(umlElement4, umlElement2, EQUALITY_THRESHOLD + 0.03);
        mockSimilarityBetweenElements(umlElement4, umlElement3, EQUALITY_THRESHOLD + 0.02);

        int similarityId1 = modelIndex.setSimilarityId(umlElement1);
        int similarityId2 = modelIndex.setSimilarityId(umlElement2);
        when(umlElement2.getSimilarityID()).thenReturn(similarityId2);
        int similarityId3 = modelIndex.setSimilarityId(umlElement3);
        int similarityId4 = modelIndex.setSimilarityId(umlElement4);

        assertThat(similarityId1).isEqualTo(0);
        assertThat(similarityId2).isEqualTo(1);
        assertThat(similarityId3).isEqualTo(2);
        assertThat(similarityId4).isEqualTo(1);
        assertThat(modelIndex.getElementSimilarityMap().keySet()).containsExactlyInAnyOrder(umlElement1, umlElement2, umlElement3, umlElement4);
        assertThat(modelIndex.getNumberOfUniqueElements()).isEqualTo(3);
    }

    private void mockSimilarityBetweenElements(UMLElement element1, UMLElement element2, double similarity) {
        when(element2.similarity(element1)).thenReturn(similarity);
    }
}
