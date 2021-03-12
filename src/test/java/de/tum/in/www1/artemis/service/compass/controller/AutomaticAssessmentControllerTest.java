package de.tum.in.www1.artemis.service.compass.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import com.hazelcast.config.Config;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.service.compass.assessment.CompassResult;
import de.tum.in.www1.artemis.service.compass.assessment.Score;
import de.tum.in.www1.artemis.service.compass.assessment.SimilaritySetAssessment;
import de.tum.in.www1.artemis.service.compass.umlmodel.activity.*;
import de.tum.in.www1.artemis.service.compass.umlmodel.classdiagram.*;

class AutomaticAssessmentControllerTest {

    private AutomaticAssessmentController automaticAssessmentController;

    private List<Feedback> feedbacks;
    //
    // @Mock
    // AssessmentIndex assessmentIndex;

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
    SimilaritySetAssessment similaritySetAssessment;

    @Spy
    CompassResult compassResult;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        Long exerciseId = 1L;
        Config config = new Config();
        config.setProperty("hazelcast.shutdownhook.enabled", "false");
        NetworkConfig network = config.getNetworkConfig();
        network.getJoin().getTcpIpConfig().setEnabled(false);
        network.getJoin().getMulticastConfig().setEnabled(false);
        HazelcastInstance testInstance = Hazelcast.newHazelcastInstance(config);
        automaticAssessmentController = new AutomaticAssessmentController(exerciseId, testInstance);

        feedbacks = new ArrayList<Feedback>();
        feedbacks.add(feedback1);
        feedbacks.add(feedback2);
        when(feedback2.getCredits()).thenReturn(0.5);
        when(automaticAssessmentController.getAssessmentForSimilaritySet(1)).thenReturn(Optional.of(similaritySetAssessment));
        when(automaticAssessmentController.getAssessmentForSimilaritySet(2)).thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        Mockito.reset(classDiagram, feedbacks);
    }

    @Test
    void addFeedbacksToAssessment_ClassDiagram() {
        when(classDiagram.getElementByJSONID("element1Id")).thenReturn(umlClass);
        when(classDiagram.getElementByJSONID("element2Id")).thenReturn(umlRelationship);
        when(umlClass.getSimilarityID()).thenReturn(1);
        when(umlRelationship.getSimilarityID()).thenReturn(2);

        automaticAssessmentController.addFeedbacksToSimilaritySet(feedbacks, classDiagram);

        verify(similaritySetAssessment).addFeedback(feedback1);
        verify(similaritySetAssessment, never()).addFeedback(feedback2);
        verify(automaticAssessmentController).addSimilaritySetAssessment(eq(2), any(SimilaritySetAssessment.class));
        verify(automaticAssessmentController, never()).addSimilaritySetAssessment(eq(1), any(SimilaritySetAssessment.class));
    }

    @Test
    void addFeedbacksToAssessment_ActivityDiagram() {
        when(activityDiagram.getElementByJSONID("element1Id")).thenReturn(umlControlFlow);
        when(activityDiagram.getElementByJSONID("element2Id")).thenReturn(umlActivityElement);
        when(umlControlFlow.getSimilarityID()).thenReturn(1);
        when(umlActivityElement.getSimilarityID()).thenReturn(2);

        automaticAssessmentController.addFeedbacksToSimilaritySet(feedbacks, activityDiagram);

        verify(similaritySetAssessment).addFeedback(feedback1);
        verify(similaritySetAssessment, never()).addFeedback(feedback2);
        verify(automaticAssessmentController).addSimilaritySetAssessment(eq(2), any(SimilaritySetAssessment.class));
        verify(automaticAssessmentController, never()).addSimilaritySetAssessment(eq(1), any(SimilaritySetAssessment.class));
    }

    @Test
    void addFeedbacksToAssessment_nullElements() {
        when(classDiagram.getElementByJSONID("element1Id")).thenReturn(null);
        when(classDiagram.getElementByJSONID("element2Id")).thenReturn(null);

        automaticAssessmentController.addFeedbacksToSimilaritySet(feedbacks, classDiagram);

        verify(similaritySetAssessment, never()).addFeedback(any(Feedback.class));
        verify(automaticAssessmentController, never()).addSimilaritySetAssessment(anyInt(), any(SimilaritySetAssessment.class));
    }

    @Test
    void assessModelsAutomatically() {
        automaticAssessmentController = mock(AutomaticAssessmentController.class);
        doCallRealMethod().when(automaticAssessmentController).assessModelsAutomatically(modelIndex);
        when(automaticAssessmentController.assessModelAutomatically(classDiagram)).thenReturn(mock(CompassResult.class));
        when(automaticAssessmentController.assessModelAutomatically(activityDiagram)).thenReturn(mock(CompassResult.class));
        when(modelIndex.getModelCollection()).thenReturn(List.of(classDiagram));
        when(modelIndex.getModelCollection()).thenReturn(List.of(classDiagram, activityDiagram));

        automaticAssessmentController.assessModelsAutomatically(modelIndex);

        verify(automaticAssessmentController).assessModelAutomatically(classDiagram);
        verify(automaticAssessmentController).assessModelAutomatically(activityDiagram);
    }

    @Test
    void assessModelAutomatically_ClassDiagram() {
        prepareClassDiagramForAutomaticAssessment();
        prepareAssessmentIndexForAutomaticAssessment();

        CompassResult compassResult = automaticAssessmentController.assessModelAutomatically(classDiagram);

        assertThat(compassResult.entitiesCovered()).isEqualTo(6);
        assertThat(compassResult.getPoints()).isEqualTo(-0.5 - 0.5 + 0 + 1.5 + 1.0 + 0.5);
        assertThat(compassResult.getConfidence()).isEqualTo((0.5 + 0.6 + 0.7 + 0.8 + 0.9 + 1.0) / 6, offset(0.000001));
        verify(automaticAssessmentController).setLastAssessmentCompassResult(classDiagram.getModelSubmissionId(), compassResult);
    }

    @Test
    void assessModelAutomatically_ActivityDiagram() {
        prepareActivityDiagramForAutomaticAssessment();
        prepareAssessmentIndexForAutomaticAssessment();

        CompassResult compassResult = automaticAssessmentController.assessModelAutomatically(activityDiagram);

        assertThat(compassResult.entitiesCovered()).isEqualTo(6);
        assertThat(compassResult.getPoints()).isEqualTo(-0.5 - 0.5 + 0 + 1.5 + 1.0 + 0.5);
        assertThat(compassResult.getConfidence()).isEqualTo((0.5 + 0.6 + 0.7 + 0.8 + 0.9 + 1.0) / 6, offset(0.000001));
        verify(automaticAssessmentController).setLastAssessmentCompassResult(activityDiagram.getModelSubmissionId(), compassResult);
    }

    @Test
    void assessModelAutomatically_nullScore() {
        when(classDiagram.getClassList()).thenReturn(List.of(umlClass));
        when(umlClass.getSimilarityID()).thenReturn(1);
        when(similaritySetAssessment.getScore()).thenReturn(null);

        CompassResult compassResult = automaticAssessmentController.assessModelAutomatically(classDiagram);

        assertThat(compassResult.entitiesCovered()).isEqualTo(0);
        assertThat(compassResult.getPoints()).isEqualTo(0);
        assertThat(compassResult.getConfidence()).isEqualTo(0);
        verify(automaticAssessmentController).setLastAssessmentCompassResult(classDiagram.getModelSubmissionId(), compassResult);

    }

    @Test
    void isUnassessed_true() {
        boolean isUnassessed = automaticAssessmentController.isUnassessed(classDiagram.getModelSubmissionId());
        assertThat(isUnassessed).isTrue();
    }

    @Test
    void isUnassessed_false() {
        doReturn(spy(CompassResult.class)).when(automaticAssessmentController).getLastAssessmentCompassResult(classDiagram.getModelSubmissionId());
        boolean isUnassessed = automaticAssessmentController.isUnassessed(classDiagram.getModelSubmissionId());
        assertThat(isUnassessed).isFalse();
    }

    @Test
    void getLastAssessmentConfidence() {
        doReturn(compassResult).when(automaticAssessmentController).getLastAssessmentCompassResult(classDiagram.getModelSubmissionId());
        doReturn(0.456).when(compassResult).getConfidence();
        double confidence = automaticAssessmentController.getLastAssessmentConfidence(classDiagram.getModelSubmissionId());
        assertThat(confidence).isEqualTo(0.456);
    }

    @Test
    void getLastAssessmentConfidence_noCompassResult() {
        double confidence = automaticAssessmentController.getLastAssessmentConfidence(classDiagram.getModelSubmissionId());
        assertThat(confidence).isEqualTo(-1);
    }

    @Test
    void getLastAssessmentCoverage() {
        doReturn(compassResult).when(automaticAssessmentController).getLastAssessmentCompassResult(classDiagram.getModelSubmissionId());
        doReturn(0.789).when(compassResult).getCoverage();
        double confidence = automaticAssessmentController.getLastAssessmentCoverage(classDiagram.getModelSubmissionId());
        assertThat(confidence).isEqualTo(0.789);
    }

    @Test
    void getLastAssessmentCoverage_noCompassResult() {
        double confidence = automaticAssessmentController.getLastAssessmentCoverage(classDiagram.getModelSubmissionId());
        assertThat(confidence).isEqualTo(-1);
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
    }

    private void prepareAssessmentIndexForAutomaticAssessment() {
        SimilaritySetAssessment similaritySetAssessment1 = mock(SimilaritySetAssessment.class);
        SimilaritySetAssessment similaritySetAssessment2 = mock(SimilaritySetAssessment.class);
        SimilaritySetAssessment similaritySetAssessment3 = mock(SimilaritySetAssessment.class);
        SimilaritySetAssessment similaritySetAssessment4 = mock(SimilaritySetAssessment.class);
        SimilaritySetAssessment similaritySetAssessment5 = mock(SimilaritySetAssessment.class);
        SimilaritySetAssessment similaritySetAssessment6 = mock(SimilaritySetAssessment.class);

        when(automaticAssessmentController.getAssessmentForSimilaritySet(1)).thenReturn(Optional.of(similaritySetAssessment1));
        when(automaticAssessmentController.getAssessmentForSimilaritySet(2)).thenReturn(Optional.of(similaritySetAssessment2));
        when(automaticAssessmentController.getAssessmentForSimilaritySet(3)).thenReturn(Optional.of(similaritySetAssessment3));
        when(automaticAssessmentController.getAssessmentForSimilaritySet(4)).thenReturn(Optional.empty());
        when(automaticAssessmentController.getAssessmentForSimilaritySet(5)).thenReturn(Optional.empty());
        when(automaticAssessmentController.getAssessmentForSimilaritySet(6)).thenReturn(Optional.of(similaritySetAssessment4));
        when(automaticAssessmentController.getAssessmentForSimilaritySet(7)).thenReturn(Optional.empty());
        when(automaticAssessmentController.getAssessmentForSimilaritySet(8)).thenReturn(Optional.of(similaritySetAssessment5));
        when(automaticAssessmentController.getAssessmentForSimilaritySet(9)).thenReturn(Optional.of(similaritySetAssessment6));
        when(automaticAssessmentController.getAssessmentForSimilaritySet(10)).thenReturn(Optional.empty());

        Score score1 = mockScore(-0.5, 0.5);
        Score score2 = mockScore(-0.5, 0.6);
        Score score3 = mockScore(0, 0.7);
        Score score4 = mockScore(1.5, 0.8);
        Score score5 = mockScore(1.0, 0.9);
        Score score6 = mockScore(0.5, 1.0);

        when(similaritySetAssessment1.getScore()).thenReturn(score1);
        when(similaritySetAssessment2.getScore()).thenReturn(score2);
        when(similaritySetAssessment3.getScore()).thenReturn(score3);
        when(similaritySetAssessment4.getScore()).thenReturn(score4);
        when(similaritySetAssessment5.getScore()).thenReturn(score5);
        when(similaritySetAssessment6.getScore()).thenReturn(score6);
    }

    private Score mockScore(double points, double confidence) {
        Score score = mock(Score.class);
        when(score.getPoints()).thenReturn(points);
        when(score.getConfidence()).thenReturn(confidence);
        return score;
    }
}
