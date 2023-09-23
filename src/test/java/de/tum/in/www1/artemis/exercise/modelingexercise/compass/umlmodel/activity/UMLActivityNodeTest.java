package de.tum.in.www1.artemis.exercise.modelingexercise.compass.umlmodel.activity;

import static de.tum.in.www1.artemis.service.compass.umlmodel.activity.UMLActivityNode.UMLActivityNodeType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import de.tum.in.www1.artemis.service.compass.umlmodel.activity.UMLActivityNode;
import de.tum.in.www1.artemis.service.compass.umlmodel.activity.UMLControlFlow;
import me.xdrop.fuzzywuzzy.FuzzySearch;

class UMLActivityNodeTest {

    private UMLActivityNode activityNode;

    @Mock
    private UMLActivityNode referenceNode;

    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        activityNode = new UMLActivityNode("myActivityNode", "activityNodeId", ACTIVITY_ACTION_NODE);
        when(referenceNode.getType()).thenReturn("ActivityActionNode");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (closeable != null) {
            closeable.close();
        }
    }

    @Test
    void similarity_null() {
        double similarity = activityNode.similarity(null);

        assertThat(similarity).isZero();
    }

    @Test
    void similarity_differentElementType() {
        double similarity = activityNode.similarity(mock(UMLControlFlow.class));

        assertThat(similarity).isZero();
    }

    @Test
    void similarity_sameActivityNode() {
        when(referenceNode.getName()).thenReturn("myActivityNode");

        double similarity = activityNode.similarity(referenceNode);

        assertThat(similarity).isEqualTo(1);
    }

    @Test
    void similarity_differentName() {
        when(referenceNode.getName()).thenReturn("otherElementName");
        double expectedSimilarity = FuzzySearch.ratio("myActivityNode", "otherElementName") / 100.0;

        double similarity = activityNode.similarity(referenceNode);

        assertThat(similarity).isEqualTo(expectedSimilarity);
    }

    @Test
    void similarity_nullReferenceName() {
        when(referenceNode.getName()).thenReturn(null);

        double similarity = activityNode.similarity(referenceNode);

        assertThat(similarity).isZero();
    }

    @Test
    void similarity_bothNamesNull() {
        activityNode = new UMLActivityNode(null, "activityNodeId", ACTIVITY_ACTION_NODE);
        when(referenceNode.getName()).thenReturn(null);

        double similarity = activityNode.similarity(referenceNode);

        assertThat(similarity).isEqualTo(1);
    }

    @Test
    void similarity_bothNamesEmpty() {
        activityNode = new UMLActivityNode("", "activityNodeId", ACTIVITY_ACTION_NODE);
        when(referenceNode.getName()).thenReturn("");

        double similarity = activityNode.similarity(referenceNode);

        assertThat(similarity).isEqualTo(1);
    }

    @Test
    void similarity_differentNodeTypesWithoutName() {
        activityNode = new UMLActivityNode("", "finalNodeId", ACTIVITY_FINAL_NODE);
        when(referenceNode.getType()).thenReturn("ActivityInitialNode");
        when(referenceNode.getName()).thenReturn("");

        double similarity = activityNode.similarity(referenceNode);

        assertThat(similarity).isZero();
    }

    @Test
    void getType() {
        String nodeType = activityNode.getType();

        assertThat(nodeType).isEqualTo("ActivityActionNode");
    }
}
