package de.tum.in.www1.artemis.exercise.modelingexercise.compass.umlmodel.classdiagram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLAttribute;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLClass;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLMethod;
import de.tum.in.www1.artemis.service.compass.utils.CompassConfiguration;
import me.xdrop.fuzzywuzzy.FuzzySearch;

class UMLMethodTest {

    private UMLMethod method;

    @Mock
    private UMLClass parentClass;

    @Mock
    private UMLMethod referenceMethod;

    @Mock
    private UMLClass referenceParentClass;

    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);

        method = new UMLMethod("myMethod(arg1, arg2): void", "myMethod", "void", List.of("arg1", "arg2"), "methodId");
        method.setParentElement(parentClass);

        when(referenceMethod.getParentElement()).thenReturn(referenceParentClass);
        when(parentClass.getSimilarityID()).thenReturn(123);
        when(referenceParentClass.getSimilarityID()).thenReturn(123);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (closeable != null) {
            closeable.close();
        }
    }

    @Test
    void similarity_null() {
        double similarity = method.similarity(null);

        assertThat(similarity).isZero();
    }

    @Test
    void similarity_differentElementType() {
        double similarity = method.similarity(mock(UMLAttribute.class));

        assertThat(similarity).isZero();
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
    void similarity_sameMethod_noParentSimilarityId() {
        when(referenceMethod.getName()).thenReturn("myMethod");
        when(referenceMethod.getReturnType()).thenReturn("void");
        when(referenceMethod.getParameters()).thenReturn(List.of("arg1", "arg2"));
        when(referenceParentClass.getSimilarityID()).thenReturn(-1);
        when(parentClass.similarity(referenceParentClass)).thenReturn(CompassConfiguration.EQUALITY_THRESHOLD + 0.01);

        double similarity = method.similarity(referenceMethod);

        assertThat(similarity).isEqualTo(1);
    }

    @Test
    void similarity_sameMethod_differentParent() {
        when(referenceMethod.getName()).thenReturn("myMethod");
        when(referenceMethod.getReturnType()).thenReturn("void");
        when(referenceMethod.getParameters()).thenReturn(List.of("arg1", "arg2"));
        when(referenceParentClass.getSimilarityID()).thenReturn(321);

        double similarity = method.similarity(referenceMethod);

        assertThat(similarity).isZero();
    }

    @Test
    void similarity_sameMethod_differentParent_noParentSimilarityId() {
        when(referenceMethod.getName()).thenReturn("myMethod");
        when(referenceMethod.getReturnType()).thenReturn("void");
        when(referenceMethod.getParameters()).thenReturn(List.of("arg1", "arg2"));
        when(referenceParentClass.getSimilarityID()).thenReturn(-1);
        when(parentClass.similarity(referenceParentClass)).thenReturn(CompassConfiguration.EQUALITY_THRESHOLD - 0.01);

        double similarity = method.similarity(referenceMethod);

        assertThat(similarity).isZero();
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

        assertThat(similarity).isZero();
    }
}
