package de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import de.tum.in.www1.artemis.service.compass.umlmodel.AbstractUMLDiagramTest;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;

class UMLClassDiagramTest extends AbstractUMLDiagramTest {

    private UMLClassDiagram classDiagram;

    @Mock
    private UMLClass umlClass1;

    @Mock
    private UMLClass umlClass2;

    @Mock
    private UMLClass umlClass3;

    @Mock
    private UMLRelationship umlRelationship1;

    @Mock
    private UMLRelationship umlRelationship2;

    @Mock
    private UMLRelationship umlRelationship3;

    @Mock
    private UMLPackage umlPackage1;

    @Mock
    private UMLPackage umlPackage2;

    @Mock
    private UMLPackage umlPackage3;

    @Mock
    private UMLAttribute umlAttribute;

    @Mock
    private UMLMethod umlMethod;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        classDiagram = new UMLClassDiagram(123456789, List.of(umlClass1, umlClass2, umlClass3), List.of(umlRelationship1, umlRelationship2, umlRelationship3),
                List.of(umlPackage1, umlPackage2, umlPackage3));

        when(umlClass1.getElementByJSONID("class1")).thenReturn(umlClass1);
        when(umlClass2.getElementByJSONID("class2")).thenReturn(umlClass2);
        when(umlClass3.getElementByJSONID("class3")).thenReturn(umlClass3);
        when(umlClass1.getElementByJSONID("attribute")).thenReturn(umlAttribute);
        when(umlClass3.getElementByJSONID("method")).thenReturn(umlMethod);
        when(umlRelationship1.getJSONElementID()).thenReturn("relationship1");
        when(umlRelationship2.getJSONElementID()).thenReturn("relationship2");
        when(umlRelationship3.getJSONElementID()).thenReturn("relationship3");
        when(umlPackage1.getJSONElementID()).thenReturn("package1");
        when(umlPackage2.getJSONElementID()).thenReturn("package2");
        when(umlPackage3.getJSONElementID()).thenReturn("package3");
    }

    @Test
    void getElementByJSONID_null() {
        UMLElement element = classDiagram.getElementByJSONID(null);

        assertThat(element).isNull();
    }

    @Test
    void getElementByJSONID_emptyString() {
        UMLElement element = classDiagram.getElementByJSONID("");

        assertThat(element).isNull();
    }

    @Test
    void getElementByJSONID_getClass() {
        UMLElement element = classDiagram.getElementByJSONID("class1");

        assertThat(element).isEqualTo(umlClass1);
    }

    @Test
    void getElementByJSONID_getAttribute() {
        UMLElement element = classDiagram.getElementByJSONID("attribute");

        assertThat(element).isEqualTo(umlAttribute);
    }

    @Test
    void getElementByJSONID_getMethod() {
        UMLElement element = classDiagram.getElementByJSONID("method");

        assertThat(element).isEqualTo(umlMethod);
    }

    @Test
    void getElementByJSONID_getRelationship() {
        UMLElement element = classDiagram.getElementByJSONID("relationship2");

        assertThat(element).isEqualTo(umlRelationship2);
    }

    @Test
    void getElementByJSONID_getPackage() {
        UMLElement element = classDiagram.getElementByJSONID("package3");

        assertThat(element).isEqualTo(umlPackage3);
    }

    @Test
    void getElementByJSONID_notExisting() {
        UMLElement element = classDiagram.getElementByJSONID("nonExistingElement");

        assertThat(element).isNull();
    }

    @Test
    void getModelElements() {
        List<UMLElement> elementList = classDiagram.getModelElements();

        assertThat(elementList).containsExactlyInAnyOrder(umlClass1, umlClass2, umlClass3, umlRelationship1, umlRelationship2, umlRelationship3, umlPackage1, umlPackage2,
                umlPackage3);
    }

    @Test
    void getModelElements_emptyElementLists() {
        classDiagram = new UMLClassDiagram(987654321, Collections.emptyList(), Collections.emptyList(), Collections.emptyList());

        List<UMLElement> elementList = classDiagram.getModelElements();

        assertThat(elementList).isEmpty();
    }

    @Test
    void similarityClassDiagram_EqualModels() {
        compareSubmissions(modelingSubmission(UMLClassDiagrams.CLASS_MODEL_1), modelingSubmission(UMLClassDiagrams.CLASS_MODEL_1), 0.8, 100.0);
        compareSubmissions(modelingSubmission(UMLClassDiagrams.CLASS_MODEL_2), modelingSubmission(UMLClassDiagrams.CLASS_MODEL_2), 0.8, 100.0);
    }

    @Test
    void similarityClassDiagram_DifferentModels() {
        compareSubmissions(modelingSubmission(UMLClassDiagrams.CLASS_MODEL_1), modelingSubmission(UMLClassDiagrams.CLASS_MODEL_2), 0.0, 30.95);
    }
}
