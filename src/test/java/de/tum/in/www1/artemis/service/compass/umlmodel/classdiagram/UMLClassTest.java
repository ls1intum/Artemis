package de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram;

import static de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLClass.UMLClassType.ABSTRACT_CLASS;
import static de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLClass.UMLClassType.CLASS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;

import me.xdrop.fuzzywuzzy.FuzzySearch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.internal.util.reflection.FieldSetter;

import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;
import de.tum.in.www1.artemis.service.compass.utils.CompassConfiguration;

class UMLClassTest {

    private UMLClass umlClass;

    @Mock
    UMLClass referenceClass;

    @Mock
    UMLAttribute attribute1;

    @Mock
    UMLAttribute attribute2;

    @Mock
    UMLAttribute attribute3;

    @Mock
    UMLAttribute attribute4;

    @Mock
    UMLMethod method1;

    @Mock
    UMLMethod method2;

    @Mock
    UMLMethod method3;

    @Mock
    UMLMethod method4;

    @BeforeEach
    void setUp() throws NoSuchFieldException {
        MockitoAnnotations.initMocks(this);

        umlClass = spy(new UMLClass("myClass", List.of(attribute1, attribute2), List.of(method1, method2), "classId", CLASS));

        FieldSetter.setField(referenceClass, UMLClass.class.getDeclaredField("type"), CLASS);
        FieldSetter.setField(referenceClass, UMLClass.class.getDeclaredField("attributes"), List.of(attribute3, attribute4));
        FieldSetter.setField(referenceClass, UMLClass.class.getDeclaredField("methods"), List.of(method3, method4));
        when(referenceClass.getName()).thenReturn("myClass");
        when(referenceClass.getElementCount()).thenReturn(5);

        when(attribute1.similarity(attribute3)).thenReturn(1.0);
        when(attribute3.similarity(attribute1)).thenReturn(1.0);
        when(attribute1.similarity(attribute4)).thenReturn(0.22);
        when(attribute4.similarity(attribute1)).thenReturn(0.22);
        when(attribute2.similarity(attribute3)).thenReturn(0.66);
        when(attribute3.similarity(attribute2)).thenReturn(0.66);
        when(attribute2.similarity(attribute4)).thenReturn(1.0);
        when(attribute4.similarity(attribute2)).thenReturn(1.0);

        when(attribute1.getJSONElementID()).thenReturn("otherId");
        when(attribute2.getJSONElementID()).thenReturn("attributeId");

        when(method1.similarity(method3)).thenReturn(1.0);
        when(method3.similarity(method1)).thenReturn(1.0);
        when(method1.similarity(method4)).thenReturn(0.44);
        when(method4.similarity(method1)).thenReturn(0.44);
        when(method2.similarity(method3)).thenReturn(0.11);
        when(method3.similarity(method2)).thenReturn(0.11);
        when(method2.similarity(method4)).thenReturn(1.0);
        when(method4.similarity(method2)).thenReturn(1.0);

        when(method1.getJSONElementID()).thenReturn("methodId");
        when(method2.getJSONElementID()).thenReturn("otherId");
    }

    @Test
    void similarity_null() {
        double similarity = umlClass.similarity(null);

        assertThat(similarity).isEqualTo(0);
    }

    @Test
    void similarity_differentElementType() {
        double similarity = umlClass.similarity(mock(UMLRelationship.class));

        assertThat(similarity).isEqualTo(0);
    }

    @Test
    void similarity_sameClass() {
        double similarity = umlClass.similarity(referenceClass);

        assertThat(similarity).isEqualTo(1);
    }

    @Test
    void similarity_differentClassType() throws NoSuchFieldException {
        FieldSetter.setField(referenceClass, UMLClass.class.getDeclaredField("type"), ABSTRACT_CLASS);

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

        assertThat(similarity).isEqualTo(0);
    }

    @Test
    void overallSimilarity_differentElementType() {
        double similarity = umlClass.overallSimilarity(mock(UMLPackage.class));

        assertThat(similarity).isEqualTo(0);
    }

    @Test
    void overallSimilarity_sameClass() {
        double similarity = umlClass.overallSimilarity(referenceClass);

        assertThat(similarity).isEqualTo(1);
        verify(umlClass).similarity(referenceClass);
    }

    @Test
    void overallSimilarity_differentAttributesAndMethods() throws NoSuchFieldException {
        referenceClass = mock(UMLClass.class, CALLS_REAL_METHODS);
        FieldSetter.setField(referenceClass, UMLClass.class.getDeclaredField("attributes"), List.of(attribute3));
        FieldSetter.setField(referenceClass, UMLClass.class.getDeclaredField("methods"), List.of(method4));
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
