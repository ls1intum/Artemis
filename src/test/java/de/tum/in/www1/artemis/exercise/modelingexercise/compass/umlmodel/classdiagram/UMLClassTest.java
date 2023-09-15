package de.tum.in.www1.artemis.exercise.modelingexercise.compass.umlmodel.classdiagram;

import static de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLClass.UMLClassType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.*;
import de.tum.in.www1.artemis.service.compass.utils.CompassConfiguration;
import me.xdrop.fuzzywuzzy.FuzzySearch;

class UMLClassTest {

    private UMLClass umlClass;

    @Spy
    private UMLClass referenceClass;

    @Spy
    private UMLAttribute attribute1;

    @Spy
    private UMLAttribute attribute2;

    @Spy
    private UMLAttribute attribute3;

    @Spy
    private UMLAttribute attribute4;

    @Spy
    private UMLMethod method1;

    @Spy
    private UMLMethod method2;

    @Spy
    private UMLMethod method3;

    @Spy
    private UMLMethod method4;

    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);

        umlClass = spy(new UMLClass("myClass", List.of(attribute1, attribute2), List.of(method1, method2), "classId", CLASS));

        doReturn(CLASS).when(referenceClass).getClassType();
        doReturn(List.of(attribute3, attribute4)).when(referenceClass).getAttributes();
        doReturn(List.of(method3, method4)).when(referenceClass).getMethods();

        doReturn("myClass").when(referenceClass).getName();
        doReturn(5).when(referenceClass).getElementCount();

        doReturn(1.0).when(attribute1).similarity(attribute3);
        doReturn(1.0).when(attribute3).similarity(attribute1);
        doReturn(0.22).when(attribute1).similarity(attribute4);
        doReturn(0.22).when(attribute4).similarity(attribute1);
        doReturn(0.66).when(attribute2).similarity(attribute3);
        doReturn(0.66).when(attribute3).similarity(attribute2);
        doReturn(1.0).when(attribute2).similarity(attribute4);
        doReturn(1.0).when(attribute4).similarity(attribute2);

        doReturn("otherId").when(attribute1).getJSONElementID();
        doReturn("attributeId").when(attribute2).getJSONElementID();

        doReturn(1.0).when(method1).similarity(method3);
        doReturn(1.0).when(method3).similarity(method1);
        doReturn(0.44).when(method1).similarity(method4);
        doReturn(0.44).when(method4).similarity(method1);
        doReturn(0.11).when(method2).similarity(method3);
        doReturn(0.11).when(method3).similarity(method2);
        doReturn(1.0).when(method2).similarity(method4);
        doReturn(1.0).when(method4).similarity(method2);

        doReturn("methodId").when(method1).getJSONElementID();
        doReturn("otherId").when(method2).getJSONElementID();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (closeable != null) {
            closeable.close();
        }
    }

    @Test
    void similarity_null() {
        double similarity = umlClass.similarity(null);
        assertThat(similarity).isZero();
    }

    @Test
    void similarity_differentElementType() {
        double similarity = umlClass.similarity(mock(UMLRelationship.class));
        assertThat(similarity).isZero();
    }

    @Test
    void similarity_sameClass() {
        double similarity = umlClass.similarity(referenceClass);
        assertThat(similarity).isEqualTo(1);
    }

    @Test
    void similarity_differentClassType() {
        doReturn(ABSTRACT_CLASS).when(referenceClass).getClassType();
        double similarity = umlClass.similarity(referenceClass);
        assertThat(similarity).isEqualTo(CompassConfiguration.CLASS_NAME_WEIGHT);
    }

    @Test
    void similarity_differentClassName() {
        when(referenceClass.getName()).thenReturn("anotherClass");
        double expectedNameSimilarity = FuzzySearch.ratio("myClass", "anotherClass") / 100.00 * CompassConfiguration.CLASS_NAME_WEIGHT;
        double similarity = umlClass.similarity(referenceClass);
        assertThat(similarity).isEqualTo(expectedNameSimilarity + CompassConfiguration.CLASS_TYPE_WEIGHT);
    }

    @Test
    void similarity_nullReferenceName() {
        when(referenceClass.getName()).thenReturn(null);
        double similarity = umlClass.similarity(referenceClass);
        assertThat(similarity).isEqualTo(CompassConfiguration.CLASS_TYPE_WEIGHT);
    }

    @Test
    void overallSimilarity_null() {
        double similarity = umlClass.overallSimilarity(null);
        assertThat(similarity).isZero();
    }

    @Test
    void overallSimilarity_differentElementType() {
        double similarity = umlClass.overallSimilarity(mock(UMLPackage.class));
        assertThat(similarity).isZero();
    }

    @Test
    void overallSimilarity_sameClass() {
        double similarity = umlClass.overallSimilarity(referenceClass);
        assertThat(similarity).isEqualTo(1);
        verify(umlClass).similarity(referenceClass);
    }

    @Test
    void overallSimilarity_differentAttributesAndMethods() {
        referenceClass = mock(UMLClass.class, CALLS_REAL_METHODS);
        doReturn(List.of(attribute3)).when(referenceClass).getAttributes();
        doReturn(List.of(method4)).when(referenceClass).getMethods();
        when(referenceClass.getElementCount()).thenReturn(3);
        when(referenceClass.similarity(umlClass)).thenReturn(1.0);
        when(attribute1.similarity(attribute3)).thenReturn(0.77);
        when(attribute3.similarity(attribute1)).thenReturn(0.77);
        when(method2.similarity(method4)).thenReturn(0.55);
        when(method4.similarity(method2)).thenReturn(0.55);
        double expectedAttributeSimilarity = 0.2 * 0.77;
        double expectedMethodSimilarity = 0.2 * 0.55;

        double similarity1 = umlClass.overallSimilarity(referenceClass);
        double similarity2 = referenceClass.overallSimilarity(umlClass);

        similarity1 = Math.round(similarity1 * 100000) / 100000.0;
        similarity2 = Math.round(similarity2 * 100000) / 100000.0;
        assertThat(similarity1).isEqualTo(expectedAttributeSimilarity + expectedMethodSimilarity + 0.2);
        assertThat(similarity1).isEqualTo(similarity2);
    }

    @Test
    void getElementByJSONID_noElementFound() {
        UMLElement element = umlClass.getElementByJSONID("nonExistingId");
        assertThat(element).isNull();
    }

    @Test
    void getElementByJSONID_getClass() {
        UMLElement element = umlClass.getElementByJSONID("classId");
        assertThat(element).isEqualTo(umlClass);
    }

    @Test
    void getElementByJSONID_getAttribute() {
        UMLElement element = umlClass.getElementByJSONID("attributeId");
        assertThat(element).isEqualTo(attribute2);
    }

    @Test
    void getElementByJSONID_getMethod() {
        UMLElement element = umlClass.getElementByJSONID("methodId");
        assertThat(element).isEqualTo(method1);
    }

    @Test
    void getElementCount() {
        int elementCount = umlClass.getElementCount();
        assertThat(elementCount).isEqualTo(5);
    }

    @Test
    void getType() {
        umlClass = new UMLClass("myClass", Collections.emptyList(), Collections.emptyList(), "classId", ABSTRACT_CLASS);
        String classType = umlClass.getType();
        assertThat(classType).isEqualTo("AbstractClass");
    }
}
