package de.tum.in.www1.artemis.modeling.compass.umlmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import de.tum.in.www1.artemis.service.compass.umlmodel.Similarity;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLDiagram;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;
import de.tum.in.www1.artemis.service.compass.umlmodel.activity.UMLActivityDiagram;

class UMLDiagramTest {

    @Spy
    private UMLDiagram umlDiagram;

    @Spy
    private UMLDiagram referenceDiagram;

    @Spy
    private UMLElement umlElement1;

    @Spy
    private UMLElement umlElement2;

    @Spy
    private UMLElement umlElement3;

    @Spy
    private UMLElement referenceElement1;

    @Spy
    private UMLElement referenceElement2;

    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        doReturn(List.of(umlElement1, umlElement2)).when(umlDiagram).getModelElements();
        doReturn(List.of(referenceElement1, referenceElement2)).when(referenceDiagram).getModelElements();

        mockOverallSimilarity(umlElement1, referenceElement1, 1.0);
        mockOverallSimilarity(umlElement1, referenceElement2, 1.0);
        mockOverallSimilarity(umlElement2, referenceElement1, 1.0);
        mockOverallSimilarity(umlElement2, referenceElement2, 1.0);
        mockOverallSimilarity(umlElement3, referenceElement1, 0.123);
        mockOverallSimilarity(umlElement3, referenceElement2, 0.456);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (closeable != null) {
            closeable.close();
        }
        reset(umlDiagram, umlElement1, umlElement2, umlElement3, referenceElement1, referenceElement2, referenceDiagram);
    }

    @Test
    void similarity_null() {
        double similarity = umlDiagram.similarity(null);
        assertThat(similarity).isZero();
    }

    @Test
    void similarity_differentDiagramType() {
        double similarity = umlDiagram.similarity(spy(UMLActivityDiagram.class));
        assertThat(similarity).isZero();
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
