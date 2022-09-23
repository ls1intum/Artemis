package de.tum.in.www1.artemis.modeling.compass.umlmodel.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import de.tum.in.www1.artemis.modeling.compass.umlmodel.AbstractUMLDiagramTest;
import de.tum.in.www1.artemis.service.compass.umlmodel.UMLElement;
import de.tum.in.www1.artemis.service.compass.umlmodel.activity.UMLActivity;
import de.tum.in.www1.artemis.service.compass.umlmodel.activity.UMLActivityDiagram;
import de.tum.in.www1.artemis.service.compass.umlmodel.activity.UMLActivityNode;
import de.tum.in.www1.artemis.service.compass.umlmodel.activity.UMLControlFlow;

class UMLActivityDiagramTest extends AbstractUMLDiagramTest {

    private UMLActivityDiagram activityDiagram;

    @Mock
    private UMLActivityNode umlActivityNode1;

    @Mock
    private UMLActivityNode umlActivityNode2;

    @Mock
    private UMLActivityNode umlActivityNode3;

    @Mock
    private UMLActivity umlActivity1;

    @Mock
    private UMLActivity umlActivity2;

    @Mock
    private UMLActivity umlActivity3;

    @Mock
    private UMLControlFlow umlControlFlow1;

    @Mock
    private UMLControlFlow umlControlFlow2;

    @Mock
    private UMLControlFlow umlControlFlow3;

    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);

        activityDiagram = new UMLActivityDiagram(123456789, List.of(umlActivityNode1, umlActivityNode2, umlActivityNode3), List.of(umlActivity1, umlActivity2, umlActivity3),
                List.of(umlControlFlow1, umlControlFlow2, umlControlFlow3));

        when(umlActivityNode1.getJSONElementID()).thenReturn("activityNode1");
        when(umlActivityNode2.getJSONElementID()).thenReturn("activityNode2");
        when(umlActivityNode3.getJSONElementID()).thenReturn("activityNode3");
        when(umlActivity1.getJSONElementID()).thenReturn("activity1");
        when(umlActivity2.getJSONElementID()).thenReturn("activity2");
        when(umlActivity3.getJSONElementID()).thenReturn("activity3");
        when(umlControlFlow1.getJSONElementID()).thenReturn("controlFlow1");
        when(umlControlFlow2.getJSONElementID()).thenReturn("controlFlow2");
        when(umlControlFlow3.getJSONElementID()).thenReturn("controlFlow3");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (closeable != null) {
            closeable.close();
        }
    }

    @Test
    void getElementByJSONID_null() {
        UMLElement element = activityDiagram.getElementByJSONID(null);

        assertThat(element).isNull();
    }

    @Test
    void getElementByJSONID_emptyString() {
        UMLElement element = activityDiagram.getElementByJSONID("");

        assertThat(element).isNull();
    }

    @Test
    void getElementByJSONID_getActivityNode() {
        UMLElement element = activityDiagram.getElementByJSONID("activityNode2");

        assertThat(element).isEqualTo(umlActivityNode2);
    }

    @Test
    void getElementByJSONID_getActivity() {
        UMLElement element = activityDiagram.getElementByJSONID("activity2");

        assertThat(element).isEqualTo(umlActivity2);
    }

    @Test
    void getElementByJSONID_getControlFlow() {
        UMLElement element = activityDiagram.getElementByJSONID("controlFlow2");

        assertThat(element).isEqualTo(umlControlFlow2);
    }

    @Test
    void getElementByJSONID_notExisting() {
        UMLElement element = activityDiagram.getElementByJSONID("nonExistingElement");

        assertThat(element).isNull();
    }

    @Test
    void getModelElements() {
        List<UMLElement> elementList = activityDiagram.getModelElements();

        assertThat(elementList).containsExactlyInAnyOrder(umlActivityNode1, umlActivityNode2, umlActivityNode3, umlActivity1, umlActivity2, umlActivity3, umlControlFlow1,
                umlControlFlow2, umlControlFlow3);
    }

    @Test
    void getModelElements_emptyElementLists() {
        activityDiagram = new UMLActivityDiagram(987654321, Collections.emptyList(), Collections.emptyList(), Collections.emptyList());

        List<UMLElement> elementList = activityDiagram.getModelElements();

        assertThat(elementList).isEmpty();
    }

    @Test
    void similarityActivityDiagram_EqualModels() {
        compareSubmissions(modelingSubmission(UMLActivityDiagrams.ACTIVITY_MODEL_1), modelingSubmission(UMLActivityDiagrams.ACTIVITY_MODEL_1), 0.8, 100.0);
        compareSubmissions(modelingSubmission(UMLActivityDiagrams.ACTIVITY_MODEL_2), modelingSubmission(UMLActivityDiagrams.ACTIVITY_MODEL_2), 0.8, 100.0);
    }

    @Test
    void similarityActivityDiagram_DifferentModels() {
        compareSubmissions(modelingSubmission(UMLActivityDiagrams.ACTIVITY_MODEL_1), modelingSubmission(UMLActivityDiagrams.ACTIVITY_MODEL_2), 0.0, 57.08);
    }
}
