package de.tum.in.www1.artemis.service.compass.umlmodel.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLRelationship;

class UMLControlFlowTest {

    private UMLControlFlow umlControlFlow;

    @Mock
    private UMLControlFlow referenceControlFlow;

    @Mock
    private UMLActivityElement sourceElement1;

    @Mock
    private UMLActivityElement sourceElement2;

    @Mock
    private UMLActivityElement targetElement1;

    @Mock
    private UMLActivityElement targetElement2;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        umlControlFlow = new UMLControlFlow(sourceElement1, targetElement1, "controlFlowId");

        when(referenceControlFlow.getSource()).thenReturn(sourceElement2);
        when(referenceControlFlow.getTarget()).thenReturn(targetElement2);
    }

    @Test
    void similarity_null() {
        double similarity = umlControlFlow.similarity(null);

        assertThat(similarity).isZero();
    }

    @Test
    void similarity_differentElementType() {
        double similarity = umlControlFlow.similarity(mock(UMLRelationship.class));

        assertThat(similarity).isZero();
    }

    @Test
    void similarity_sameControlFlow() {
        when(sourceElement1.similarity(sourceElement2)).thenReturn(1.0);
        when(sourceElement2.similarity(sourceElement1)).thenReturn(1.0);
        when(targetElement1.similarity(targetElement2)).thenReturn(1.0);
        when(targetElement2.similarity(targetElement1)).thenReturn(1.0);

        double similarity = umlControlFlow.similarity(referenceControlFlow);

        assertThat(similarity).isEqualTo(1);
    }

    @Test
    void similarity_differentControlFlow() {
        when(sourceElement1.similarity(sourceElement2)).thenReturn(0.34);
        when(sourceElement2.similarity(sourceElement1)).thenReturn(0.34);
        when(targetElement1.similarity(targetElement2)).thenReturn(0.78);
        when(targetElement2.similarity(targetElement1)).thenReturn(0.78);
        double expectedSimilarity = 0.34 * 0.5 + 0.78 * 0.5;

        double similarity = umlControlFlow.similarity(referenceControlFlow);

        assertThat(similarity).isEqualTo(expectedSimilarity);
    }

    @Test
    void similarity_notSymmetric() {
        when(sourceElement1.similarity(sourceElement2)).thenReturn(0.0);
        when(sourceElement2.similarity(sourceElement1)).thenReturn(0.0);
        when(targetElement1.similarity(targetElement2)).thenReturn(0.0);
        when(targetElement2.similarity(targetElement1)).thenReturn(0.0);
        when(sourceElement1.similarity(targetElement2)).thenReturn(0.34);
        when(targetElement2.similarity(sourceElement1)).thenReturn(0.34);
        when(targetElement1.similarity(sourceElement2)).thenReturn(0.78);
        when(sourceElement2.similarity(targetElement1)).thenReturn(0.78);

        double similarity = umlControlFlow.similarity(referenceControlFlow);

        assertThat(similarity).isZero();
    }
}
