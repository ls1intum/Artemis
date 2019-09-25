package de.tum.in.www1.artemis.service.compass.umlmodel.activitydiagram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import me.xdrop.fuzzywuzzy.FuzzySearch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.internal.util.reflection.FieldSetter;

class UMLActivityElementTest {

    private UMLActivityElement activityElement;

    @Mock
    UMLActivityElement referenceElement;

    @BeforeEach
    void setUp() throws NoSuchFieldException {
        MockitoAnnotations.initMocks(this);

        activityElement = mock(UMLActivityElement.class, CALLS_REAL_METHODS);
        FieldSetter.setField(activityElement, UMLActivityElement.class.getDeclaredField("name"), "myActivityElement");
    }

    @Test
    void similarity_null() {
        double similarity = activityElement.similarity(null);

        assertThat(similarity).isEqualTo(0);
    }

    @Test
    void similarity_differentElementType() {
        double similarity = activityElement.similarity(mock(UMLControlFlow.class));

        assertThat(similarity).isEqualTo(0);
    }

    @Test
    void similarity_sameActivityElement() {
        when(referenceElement.getName()).thenReturn("myActivityElement");

        double similarity = activityElement.similarity(referenceElement);

        assertThat(similarity).isEqualTo(1);
    }

    @Test
    void similarity_differentName() {
        when(referenceElement.getName()).thenReturn("otherElementName");
        double expectedSimilarity = FuzzySearch.ratio("myActivityElement", "otherElementName") / 100.0;

        double similarity = activityElement.similarity(referenceElement);

        assertThat(similarity).isEqualTo(expectedSimilarity);
    }

    @Test
    void similarity_nullReferenceName() {
        when(referenceElement.getName()).thenReturn(null);

        double similarity = activityElement.similarity(referenceElement);

        assertThat(similarity).isEqualTo(0);
    }
}
