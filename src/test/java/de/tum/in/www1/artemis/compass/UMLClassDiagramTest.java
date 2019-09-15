package de.tum.in.www1.artemis.compass;

import de.tum.in.www1.artemis.service.compass.umlmodel.UMLDiagram;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLAttribute;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLClass;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLClass.UMLClassType;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLClassDiagram;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLMethod;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLPackage;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLRelationship;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLRelationship.UMLRelationshipType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

public class UMLClassDiagramTest {

    private UMLDiagram umlClassDiagram;

    private UMLAttribute attribute1;
    private UMLAttribute attribute2;

    private UMLMethod method1;
    private UMLMethod method2;

    private UMLClass class1;
    private UMLClass class2;

    private UMLRelationship relationship;

    @BeforeEach
    public void setUp() throws Exception {
        umlClassDiagram = createUMLClassDiagram();
    }

    private UMLClassDiagram createUMLClassDiagram() {
        attribute1 = new UMLAttribute("attribute1", "String", "attribute1Id");
        attribute2 = new UMLAttribute("attribute2", "String", "attribute2Id");
        method1 = new UMLMethod("method1()", "method1","void", emptyList(), "method1Id");
        method2 = new UMLMethod("method2()", "method2","void", emptyList(), "method2Id");

        class1 = new UMLClass("class1", singletonList(attribute1), singletonList(method1), "class1Id", UMLClassType.CLASS);
        class2 = new UMLClass("class2", singletonList(attribute2), singletonList(method2), "class2Id", UMLClassType.CLASS);

        relationship = new UMLRelationship(class1, class2, UMLRelationshipType.CLASS_BIDIRECTIONAL, "relationshipId", "", "", "", "");

        List<UMLClass> classList = List.of(class1, class2);
        List<UMLRelationship> relationshipList = singletonList(relationship);
        List<UMLPackage> packageList = List.of(new UMLPackage("package", classList, "packageId"));

        return new UMLClassDiagram(123, classList, relationshipList, packageList);
    }

    @Test
    public void getElementByJsonId() {
        UMLElement element1 = umlClassDiagram.getElementByJSONID("attribute2Id");
        UMLElement element2 = umlClassDiagram.getElementByJSONID("relationshipId");

        assertThat(element1).isNotNull();
        assertThat(element1).isEqualTo(attribute2);
        assertThat(element2).isNotNull();
        assertThat(element2).isEqualTo(relationship);
    }

    @Test
    public void getElementByJsonId_nonExisting() {
        UMLElement element = umlClassDiagram.getElementByJSONID("blabliblu");

        assertThat(element).isNull();
    }

    @Test
    public void getElementByJsonId_emptyString() {
        UMLElement element = umlClassDiagram.getElementByJSONID("");

        assertThat(element).isNull();
    }

    @Test
    public void getElementByJsonId_null() {
        UMLElement element = umlClassDiagram.getElementByJSONID(null);

        assertThat(element).isNull();
    }

    @Test
    public void similarity_sameDiagram() {
        UMLDiagram referenceDiagram = createUMLClassDiagram();

        double similarity1 = umlClassDiagram.similarity(referenceDiagram);
        double similarity2 = referenceDiagram.similarity(umlClassDiagram);

        assertThat(similarity1).isEqualTo(1);
        assertThat(similarity2).isEqualTo(similarity1);
    }

    @Test
    public void similarity_emptyDiagram() {
        UMLDiagram emptyDiagram = new UMLClassDiagram(456, emptyList(), emptyList(), emptyList());

        double similarity1 = umlClassDiagram.similarity(emptyDiagram);
        double similarity2 = emptyDiagram.similarity(umlClassDiagram);

        assertThat(similarity1).isEqualTo(0);
        assertThat(similarity2).isEqualTo(similarity1);
    }

    @Test
    public void similarity_partialDiagram() {
        UMLDiagram referenceDiagram = new UMLClassDiagram(456, List.of(class1, class2), emptyList(), emptyList());

        double similarity1 = umlClassDiagram.similarity(referenceDiagram);
        double similarity2 = referenceDiagram.similarity(umlClassDiagram);

        assertThat(similarity1).isEqualTo(0.25);
        assertThat(similarity2).isEqualTo(similarity1);
    }
}
