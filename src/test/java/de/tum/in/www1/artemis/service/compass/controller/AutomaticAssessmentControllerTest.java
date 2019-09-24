package de.tum.in.www1.artemis.service.compass.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.service.compass.assessment.Assessment;
import de.tum.in.www1.artemis.service.compass.assessment.CompassResult;
import de.tum.in.www1.artemis.service.compass.assessment.Context;
import de.tum.in.www1.artemis.service.compass.assessment.Score;
import de.tum.in.www1.artemis.service.compass.umlmodel.activitydiagram.UMLActivity;
import de.tum.in.www1.artemis.service.compass.umlmodel.activitydiagram.UMLActivityDiagram;
import de.tum.in.www1.artemis.service.compass.umlmodel.activitydiagram.UMLActivityElement;
import de.tum.in.www1.artemis.service.compass.umlmodel.activitydiagram.UMLActivityNode;
import de.tum.in.www1.artemis.service.compass.umlmodel.activitydiagram.UMLControlFlow;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLAttribute;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLClass;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLClassDiagram;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLMethod;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLPackage;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.UMLRelationship;

class AutomaticAssessmentControllerTest {

    private AutomaticAssessmentController automaticAssessmentController;

    private Map<String, Feedback> elementIdFeedbackMap;

    @Mock
    AssessmentIndex assessmentIndex;

    @Mock
    ModelIndex modelIndex;

    @Mock
    UMLClassDiagram classDiagram;

    @Mock
    UMLClass umlClass;

    @Mock
    UMLRelationship umlRelationship;

    @Mock
    UMLActivityDiagram activityDiagram;

    @Mock
    UMLActivityElement umlActivityElement;

    @Mock
    UMLControlFlow umlControlFlow;

    @Mock
    Feedback feedback1;

    @Mock
    Feedback feedback2;

    @Mock
    Assessment assessment;

    private Context context1;

    private Context context2;

    private Context context3;

    private Context context4;

    private Context context5;

    private Context context6;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);

        automaticAssessmentController = new AutomaticAssessmentController();

        elementIdFeedbackMap = Map.of("element1Id", feedback1, "element2Id", feedback2);
        context1 = new Context(123);
        context2 = Context.NO_CONTEXT;
        when(feedback2.getCredits()).thenReturn(0.5);
        when(assessmentIndex.getAssessment(1)).thenReturn(Optional.of(assessment));
        when(assessmentIndex.getAssessment(2)).thenReturn(Optional.empty());
    }

    @Test
    void addFeedbacksToAssessment_ClassDiagram() {
        when(classDiagram.getElementByJSONID("element1Id")).thenReturn(umlClass);
        when(classDiagram.getElementByJSONID("element2Id")).thenReturn(umlRelationship);
        when(umlClass.getContext()).thenReturn(context1);
        when(umlRelationship.getContext()).thenReturn(context2);
        when(umlClass.getSimilarityID()).thenReturn(1);
        when(umlRelationship.getSimilarityID()).thenReturn(2);

        automaticAssessmentController.addFeedbacksToAssessment(assessmentIndex, elementIdFeedbackMap, classDiagram);

        verify(assessment).addFeedback(feedback1, context1);
        verify(assessment, never()).addFeedback(eq(feedback2), any(Context.class));
        verify(assessment, never()).addFeedback(any(Feedback.class), eq(context2));
        verify(assessmentIndex).addAssessment(eq(2), any(Assessment.class));
        verify(assessmentIndex, never()).addAssessment(eq(1), any(Assessment.class));
    }

    @Test
    void addFeedbacksToAssessment_ActivityDiagram() {
        when(activityDiagram.getElementByJSONID("element1Id")).thenReturn(umlControlFlow);
        when(activityDiagram.getElementByJSONID("element2Id")).thenReturn(umlActivityElement);
        when(umlControlFlow.getContext()).thenReturn(context1);
        when(umlActivityElement.getContext()).thenReturn(context2);
        when(umlControlFlow.getSimilarityID()).thenReturn(1);
        when(umlActivityElement.getSimilarityID()).thenReturn(2);

        automaticAssessmentController.addFeedbacksToAssessment(assessmentIndex, elementIdFeedbackMap, activityDiagram);

        verify(assessment).addFeedback(feedback1, context1);
        verify(assessment, never()).addFeedback(eq(feedback2), any(Context.class));
        verify(assessment, never()).addFeedback(any(Feedback.class), eq(context2));
        verify(assessmentIndex).addAssessment(eq(2), any(Assessment.class));
        verify(assessmentIndex, never()).addAssessment(eq(1), any(Assessment.class));
    }

    @Test
    void addFeedbacksToAssessment_nullElements() {
        when(classDiagram.getElementByJSONID("element1Id")).thenReturn(null);
        when(classDiagram.getElementByJSONID("element2Id")).thenReturn(null);

        automaticAssessmentController.addFeedbacksToAssessment(assessmentIndex, elementIdFeedbackMap, classDiagram);

        verify(assessment, never()).addFeedback(any(Feedback.class), any(Context.class));
        verify(assessmentIndex, never()).addAssessment(anyInt(), any(Assessment.class));
    }

    @Test
    void assessModelsAutomatically() {
        automaticAssessmentController = mock(AutomaticAssessmentController.class);
        doCallRealMethod().when(automaticAssessmentController).assessModelsAutomatically(modelIndex, assessmentIndex);
        when(automaticAssessmentController.assessModelAutomatically(classDiagram, assessmentIndex)).thenReturn(mock(CompassResult.class));
        when(automaticAssessmentController.assessModelAutomatically(activityDiagram, assessmentIndex)).thenReturn(mock(CompassResult.class));
        when(modelIndex.getModelCollection()).thenReturn(List.of(classDiagram));
        when(modelIndex.getModelCollection()).thenReturn(List.of(classDiagram, activityDiagram));

        automaticAssessmentController.assessModelsAutomatically(modelIndex, assessmentIndex);

        verify(automaticAssessmentController).assessModelAutomatically(classDiagram, assessmentIndex);
        verify(automaticAssessmentController).assessModelAutomatically(activityDiagram, assessmentIndex);
    }

    @Test
    void assessModelAutomatically_ClassDiagram() {
        prepareClassDiagramForAutomaticAssessment();
        prepareAssessmentIndexForAutomaticAssessment();

        CompassResult compassResult = automaticAssessmentController.assessModelAutomatically(classDiagram, assessmentIndex);

        assertThat(compassResult.entitiesCovered()).isEqualTo(6);
        assertThat(compassResult.getPoints()).isEqualTo(-0.5 - 0.5 + 0 + 1.5 + 1.0 + 0.5);
        assertThat(compassResult.getConfidence()).isEqualTo((0.5 + 0.6 + 0.7 + 0.8 + 0.9 + 1.0) / 6);
        verify(classDiagram).setLastAssessmentCompassResult(compassResult);
    }

    @Test
    void assessModelAutomatically_ActivityDiagram() {
        prepareActivityDiagramForAutomaticAssessment();
        prepareAssessmentIndexForAutomaticAssessment();

        CompassResult compassResult = automaticAssessmentController.assessModelAutomatically(activityDiagram, assessmentIndex);

        assertThat(compassResult.entitiesCovered()).isEqualTo(6);
        assertThat(compassResult.getPoints()).isEqualTo(-0.5 - 0.5 + 0 + 1.5 + 1.0 + 0.5);
        assertThat(compassResult.getConfidence()).isEqualTo((0.5 + 0.6 + 0.7 + 0.8 + 0.9 + 1.0) / 6);
        verify(activityDiagram).setLastAssessmentCompassResult(compassResult);
    }

    @Test
    void assessModelAutomatically_nullScore() {
        when(classDiagram.getClassList()).thenReturn(List.of(umlClass));
        when(umlClass.getContext()).thenReturn(context1);
        when(umlClass.getSimilarityID()).thenReturn(1);
        when(assessment.getScore(context1)).thenReturn(null);

        CompassResult compassResult = automaticAssessmentController.assessModelAutomatically(classDiagram, assessmentIndex);

        assertThat(compassResult.entitiesCovered()).isEqualTo(0);
        assertThat(compassResult.getPoints()).isEqualTo(0);
        assertThat(compassResult.getConfidence()).isEqualTo(0);
        verify(classDiagram).setLastAssessmentCompassResult(compassResult);
    }

    private void prepareClassDiagramForAutomaticAssessment() {
        UMLAttribute attribute1 = mock(UMLAttribute.class);
        UMLAttribute attribute2 = mock(UMLAttribute.class);
        UMLMethod method1 = mock(UMLMethod.class);
        UMLMethod method2 = mock(UMLMethod.class);
        UMLClass class1 = mock(UMLClass.class);
        UMLClass class2 = mock(UMLClass.class);
        UMLRelationship relationship1 = mock(UMLRelationship.class);
        UMLRelationship relationship2 = mock(UMLRelationship.class);
        UMLPackage package1 = mock(UMLPackage.class);
        UMLPackage package2 = mock(UMLPackage.class);

        when(class1.getAttributes()).thenReturn(Collections.emptyList());
        when(class1.getMethods()).thenReturn(Collections.emptyList());
        when(class1.getElementCount()).thenReturn(1);
        when(class2.getAttributes()).thenReturn(List.of(attribute1, attribute2));
        when(class2.getMethods()).thenReturn(List.of(method1, method2));
        when(class2.getElementCount()).thenReturn(5);

        when(classDiagram.getAllModelElements()).thenReturn(List.of(class1, class2, relationship1, relationship2, package1, package2, attribute1, attribute2, method1, method2));

        when(class1.getSimilarityID()).thenReturn(1);
        when(class2.getSimilarityID()).thenReturn(2);
        when(relationship1.getSimilarityID()).thenReturn(3);
        when(relationship2.getSimilarityID()).thenReturn(4);
        when(package1.getSimilarityID()).thenReturn(5);
        when(package2.getSimilarityID()).thenReturn(6);
        when(attribute1.getSimilarityID()).thenReturn(7);
        when(attribute2.getSimilarityID()).thenReturn(8);
        when(method1.getSimilarityID()).thenReturn(9);
        when(method2.getSimilarityID()).thenReturn(10);

        when(class1.getContext()).thenReturn(context1);
        when(class2.getContext()).thenReturn(context2);
        when(relationship1.getContext()).thenReturn(context3);
        when(relationship2.getContext()).thenReturn(Context.NO_CONTEXT);
        when(package1.getContext()).thenReturn(Context.NO_CONTEXT);
        when(package2.getContext()).thenReturn(context4);
        when(attribute1.getContext()).thenReturn(Context.NO_CONTEXT);
        when(attribute2.getContext()).thenReturn(context5);
        when(method1.getContext()).thenReturn(context6);
        when(method2.getContext()).thenReturn(Context.NO_CONTEXT);
    }

    private void prepareActivityDiagramForAutomaticAssessment() {
        UMLActivityNode activityNode1 = mock(UMLActivityNode.class);
        UMLActivityNode activityNode2 = mock(UMLActivityNode.class);
        UMLActivityNode activityNode3 = mock(UMLActivityNode.class);
        UMLActivity activity1 = mock(UMLActivity.class);
        UMLActivity activity2 = mock(UMLActivity.class);
        UMLActivity activity3 = mock(UMLActivity.class);
        UMLControlFlow controlFlow1 = mock(UMLControlFlow.class);
        UMLControlFlow controlFlow2 = mock(UMLControlFlow.class);
        UMLControlFlow controlFlow3 = mock(UMLControlFlow.class);

        when(activityDiagram.getAllModelElements())
                .thenReturn(List.of(activityNode1, activityNode2, activityNode3, activity1, activity2, activity3, controlFlow1, controlFlow2, controlFlow3));

        when(activityNode1.getSimilarityID()).thenReturn(1);
        when(activityNode2.getSimilarityID()).thenReturn(2);
        when(activityNode3.getSimilarityID()).thenReturn(4);
        when(activity1.getSimilarityID()).thenReturn(3);
        when(activity2.getSimilarityID()).thenReturn(5);
        when(activity3.getSimilarityID()).thenReturn(6);
        when(controlFlow1.getSimilarityID()).thenReturn(7);
        when(controlFlow2.getSimilarityID()).thenReturn(8);
        when(controlFlow3.getSimilarityID()).thenReturn(9);

        when(activityNode1.getContext()).thenReturn(context1);
        when(activityNode2.getContext()).thenReturn(context2);
        when(activityNode3.getContext()).thenReturn(Context.NO_CONTEXT);
        when(activity1.getContext()).thenReturn(context3);
        when(activity2.getContext()).thenReturn(Context.NO_CONTEXT);
        when(activity3.getContext()).thenReturn(context4);
        when(controlFlow1.getContext()).thenReturn(Context.NO_CONTEXT);
        when(controlFlow2.getContext()).thenReturn(context5);
        when(controlFlow3.getContext()).thenReturn(context6);
    }

    private void prepareAssessmentIndexForAutomaticAssessment() {
        Assessment assessment1 = mock(Assessment.class);
        Assessment assessment2 = mock(Assessment.class);
        Assessment assessment3 = mock(Assessment.class);
        Assessment assessment4 = mock(Assessment.class);
        Assessment assessment5 = mock(Assessment.class);
        Assessment assessment6 = mock(Assessment.class);

        when(assessmentIndex.getAssessment(1)).thenReturn(Optional.of(assessment1));
        when(assessmentIndex.getAssessment(2)).thenReturn(Optional.of(assessment2));
        when(assessmentIndex.getAssessment(3)).thenReturn(Optional.of(assessment3));
        when(assessmentIndex.getAssessment(4)).thenReturn(Optional.empty());
        when(assessmentIndex.getAssessment(5)).thenReturn(Optional.empty());
        when(assessmentIndex.getAssessment(6)).thenReturn(Optional.of(assessment4));
        when(assessmentIndex.getAssessment(7)).thenReturn(Optional.empty());
        when(assessmentIndex.getAssessment(8)).thenReturn(Optional.of(assessment5));
        when(assessmentIndex.getAssessment(9)).thenReturn(Optional.of(assessment6));
        when(assessmentIndex.getAssessment(10)).thenReturn(Optional.empty());

        Score score1 = mockScore(-0.5, 0.5);
        Score score2 = mockScore(-0.5, 0.6);
        Score score3 = mockScore(0, 0.7);
        Score score4 = mockScore(1.5, 0.8);
        Score score5 = mockScore(1.0, 0.9);
        Score score6 = mockScore(0.5, 1.0);

        when(assessment1.getScore(context1)).thenReturn(score1);
        when(assessment2.getScore(context2)).thenReturn(score2);
        when(assessment3.getScore(context3)).thenReturn(score3);
        when(assessment4.getScore(context4)).thenReturn(score4);
        when(assessment5.getScore(context5)).thenReturn(score5);
        when(assessment6.getScore(context6)).thenReturn(score6);
    }

    private Score mockScore(double points, double confidence) {
        Score score = mock(Score.class);
        when(score.getPoints()).thenReturn(points);
        when(score.getConfidence()).thenReturn(confidence);
        return score;
    }
}
