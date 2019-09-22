package de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram;

import me.xdrop.fuzzywuzzy.FuzzySearch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UMLMethodTest {

    private UMLMethod method;

    @Mock
    UMLMethod referenceMethod;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);

        method = new UMLMethod("myMethod(arg1, arg2): void", "myMethod", "void", List.of("arg1", "arg2"), "methodId");
    }

    @Test
    void similarity_null() {
        double similarity = method.similarity(null);

        assertThat(similarity).isEqualTo(0);
    }

    @Test
    void similarity_differentElementType() {
        double similarity = method.similarity(mock(UMLAttribute.class));

        assertThat(similarity).isEqualTo(0);
    }

    @Test
    void similarity_sameMethod() {
        when(referenceMethod.getName()).thenReturn("myMethod");
        when(referenceMethod.getReturnType()).thenReturn("void");
        when(referenceMethod.getParameters()).thenReturn(List.of("arg1", "arg2"));

        double similarity = method.similarity(referenceMethod);

        assertThat(similarity).isEqualTo(1);
    }

    @Test
    void similarity_differentMethodName() {
        when(referenceMethod.getName()).thenReturn("differentMethodName");
        when(referenceMethod.getReturnType()).thenReturn("void");
        when(referenceMethod.getParameters()).thenReturn(List.of("arg1", "arg2"));
        double expectedNameSimilarity = (FuzzySearch.ratio("myMethod", "differentMethodName") / 100.0) * 0.25;

        double similarity = method.similarity(referenceMethod);

        assertThat(similarity).isEqualTo(expectedNameSimilarity + 0.75);
    }

    @Test
    void similarity_differentReturnType() {
        when(referenceMethod.getName()).thenReturn("myMethod");
        when(referenceMethod.getReturnType()).thenReturn("String");
        when(referenceMethod.getParameters()).thenReturn(List.of("arg1", "arg2"));

        double similarity = method.similarity(referenceMethod);

        assertThat(similarity).isEqualTo(0.75);
    }

    @Test
    void similarity_differentArguments() {
        when(referenceMethod.getName()).thenReturn("myMethod");
        when(referenceMethod.getReturnType()).thenReturn("void");
        when(referenceMethod.getParameters()).thenReturn(List.of("arg1", "otherArg"));

        double similarity = method.similarity(referenceMethod);

        assertThat(similarity).isEqualTo(0.75);
    }

    @Test
    void similarity_nullReferenceValues() {
        when(referenceMethod.getName()).thenReturn(null);
        when(referenceMethod.getReturnType()).thenReturn(null);
        when(referenceMethod.getParameters()).thenReturn(null);

        double similarity = method.similarity(referenceMethod);

        assertThat(similarity).isEqualTo(0);
    }
}