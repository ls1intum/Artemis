package de.tum.in.www1.artemis.service.compass.umlmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.internal.util.reflection.FieldSetter;

import de.tum.in.www1.artemis.service.compass.assessment.CompassResult;
import de.tum.in.www1.artemis.service.compass.umlmodel.activitydiagram.UMLActivityDiagram;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLClassDiagram;

class UMLDiagramTest {

    private UMLDiagram umlDiagram;

    private UMLDiagram referenceDiagram;

    @Mock
    UMLElement umlElement1;

    @Mock
    UMLElement umlElement2;

    @Mock
    UMLElement umlElement3;

    @Mock
    UMLElement referenceElement1;

    @Mock
    UMLElement referenceElement2;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);

        umlDiagram = mock(UMLDiagram.class, CALLS_REAL_METHODS);
        when(umlDiagram.getModelElements()).thenReturn(List.of(umlElement1, umlElement2));

        referenceDiagram = mock(UMLDiagram.class, CALLS_REAL_METHODS);
        when(referenceDiagram.getModelElements()).thenReturn(List.of(referenceElement1, referenceElement2));

        mockOverallSimilarity(umlElement1, referenceElement1, 1.0);
        mockOverallSimilarity(umlElement1, referenceElement2, 1.0);
        mockOverallSimilarity(umlElement2, referenceElement1, 1.0);
        mockOverallSimilarity(umlElement2, referenceElement2, 1.0);
        mockOverallSimilarity(umlElement3, referenceElement1, 0.123);
        mockOverallSimilarity(umlElement3, referenceElement2, 0.456);
    }

    @Test
    void similarity_null() {
        double similarity = umlDiagram.similarity(null);

        assertThat(similarity).isEqualTo(0);
        verifyNoElementInteraction();
    }

    @Test
    void similarity_differentDiagramType() {
        umlDiagram = mock(UMLClassDiagram.class, CALLS_REAL_METHODS);
        double similarity = umlDiagram.similarity(mock(UMLActivityDiagram.class));

        assertThat(similarity).isEqualTo(0);
        verifyNoElementInteraction();
    }

    @Test
    void similarity_sameDiagram() {
        double similarity = umlDiagram.similarity(referenceDiagram);

        assertThat(similarity).isEqualTo(1);
    }

    @Test
    void similarity_differentDiagrams() {
        when(umlDiagram.getModelElements()).thenReturn(List.of(umlElement1, umlElement2, umlElement3));
        mockOverallSimilarity(referenceElement1, umlElement1, 0.1);
        mockOverallSimilarity(referenceElement1, umlElement2, 0.2);
        mockOverallSimilarity(referenceElement1, umlElement3, 0.3);
        mockOverallSimilarity(referenceElement2, umlElement1, 0.4);
        mockOverallSimilarity(referenceElement2, umlElement2, 0.6);
        mockOverallSimilarity(referenceElement2, umlElement3, 0.5);
        double weight = 1.0 / 3;
        double expectedSimilarity = 0.3 * weight + 0.6 * weight;

        double similarity = umlDiagram.similarity(referenceDiagram);
        double symmetricSimilarity = referenceDiagram.similarity(umlDiagram);

        assertThat(similarity).isEqualTo(expectedSimilarity);
        assertThat(symmetricSimilarity).isEqualTo(similarity);
    }

    @Test
    void isUnassessed_true() {
        boolean isUnassessed = umlDiagram.isUnassessed();

        assertThat(isUnassessed).isTrue();
    }

    @Test
    void isUnassessed_false() throws NoSuchFieldException {
        FieldSetter.setField(umlDiagram, UMLDiagram.class.getDeclaredField("lastAssessmentCompassResult"), mock(CompassResult.class));

        boolean isUnassessed = umlDiagram.isUnassessed();

        assertThat(isUnassessed).isFalse();
    }

    @Test
    void getLastAssessmentConfidence() throws NoSuchFieldException {
        CompassResult compassResult = mock(CompassResult.class);
        FieldSetter.setField(umlDiagram, UMLDiagram.class.getDeclaredField("lastAssessmentCompassResult"), compassResult);
        when(compassResult.getConfidence()).thenReturn(0.456);

        double confidence = umlDiagram.getLastAssessmentConfidence();

        assertThat(confidence).isEqualTo(0.456);
    }

    @Test
    void getLastAssessmentConfidence_noCompassResult() {
        double confidence = umlDiagram.getLastAssessmentConfidence();

        assertThat(confidence).isEqualTo(-1);
    }

    @Test
    void getLastAssessmentCoverage() throws NoSuchFieldException {
        CompassResult compassResult = mock(CompassResult.class);
        FieldSetter.setField(umlDiagram, UMLDiagram.class.getDeclaredField("lastAssessmentCompassResult"), compassResult);
        when(compassResult.getCoverage()).thenReturn(0.789);

        double confidence = umlDiagram.getLastAssessmentCoverage();

        assertThat(confidence).isEqualTo(0.789);
    }

    @Test
    void getLastAssessmentCoverage_noCompassResult() {
        double confidence = umlDiagram.getLastAssessmentCoverage();

        assertThat(confidence).isEqualTo(-1);
    }

    private void verifyNoElementInteraction() {
        verify(umlElement1, Mockito.never()).similarity(any());
        verify(umlElement1, Mockito.never()).overallSimilarity(any());
        verify(umlElement2, Mockito.never()).similarity(any());
        verify(umlElement2, Mockito.never()).overallSimilarity(any());
        verify(umlElement3, Mockito.never()).similarity(any());
        verify(umlElement3, Mockito.never()).overallSimilarity(any());
        verify(referenceElement1, Mockito.never()).similarity(any());
        verify(referenceElement1, Mockito.never()).overallSimilarity(any());
        verify(referenceElement2, Mockito.never()).similarity(any());
        verify(referenceElement2, Mockito.never()).overallSimilarity(any());
    }

    private void mockOverallSimilarity(Similarity<UMLElement> element1, Similarity<UMLElement> element2, double similarityValue) {
        when(element1.overallSimilarity(element2)).thenReturn(similarityValue);
        when(element2.overallSimilarity(element1)).thenReturn(similarityValue);
    }
}
