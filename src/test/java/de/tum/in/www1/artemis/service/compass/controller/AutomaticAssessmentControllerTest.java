package de.tum.in.www1.artemis.service.compass.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

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

    @Mock
    ModelIndex modelIndex;

    @Mock(serializable = true)
    UMLClassDiagram classDiagram;

    @Mock(serializable = true)
    UMLClass umlClass;

    @Mock(serializable = true)
    UMLRelationship umlRelationship;

    @Mock
    UMLActivityDiagram activityDiagram;

    @Mock
    UMLActivityElement umlActivityElement;

    @Mock
    UMLControlFlow umlControlFlow;

    @Mock(serializable = true)
    Feedback feedback1;

    @Mock(serializable = true)
    Feedback feedback2;

    @Mock(serializable = true)
    Feedback feedback3;

    @Mock(serializable = true)
    SimilaritySetAssessment similaritySetAssessment;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        Long exerciseId = 1L;
        Config config = new Config();
        config.setProperty("hazelcast.shutdownhook.enabled", "false");
        config.setInstanceName("testHazelcastInstance");
        NetworkConfig network = config.getNetworkConfig();
        network.getJoin().getTcpIpConfig().setEnabled(false);
        network.getJoin().getMulticastConfig().setEnabled(false);
        HazelcastInstance testInstance = Hazelcast.getOrCreateHazelcastInstance(config);
        automaticAssessmentController = new AutomaticAssessmentController(exerciseId, testInstance);
        testInstance.getMap("modelAssessments - " + exerciseId).clear();
        testInstance.getMap("modelResults - " + exerciseId).clear();
        feedbacks = new ArrayList<>();
        feedbacks.add(feedback1);
        feedbacks.add(feedback2);
        feedbacks.add(feedback3);
        when(feedback1.getId()).thenReturn(301L);
        when(feedback2.getId()).thenReturn(302L);
        when(feedback3.getId()).thenReturn(303L);
        when(feedback2.getCredits()).thenReturn(0.5);
    }

    @AfterEach
    void tearDown() {
        feedbacks = new ArrayList<>();
        reset(classDiagram, similaritySetAssessment, feedback1, feedback2);
    }

    @Test
    void addFeedbacksToAssessmentClassDiagram() {
        when(feedback1.getReferenceElementId()).thenReturn("element1Id");
        when(feedback2.getReferenceElementId()).thenReturn("element2Id");
        when(classDiagram.getElementByJSONID("element1Id")).thenReturn(umlClass);
        when(classDiagram.getElementByJSONID("element2Id")).thenReturn(umlRelationship);
        when(umlClass.getSimilarityID()).thenReturn(1);
        when(umlRelationship.getSimilarityID()).thenReturn(2);

        SimilaritySetAssessment similaritySetAssessment = new SimilaritySetAssessment(feedback3);
        automaticAssessmentController.addSimilaritySetAssessment(1, similaritySetAssessment);
        automaticAssessmentController.addFeedbacksToSimilaritySet(feedbacks, classDiagram);
        List<Long> ids = automaticAssessmentController.getAssessmentForSimilaritySet(1).get().getFeedbackList().stream().map(Feedback::getId).collect(Collectors.toList());
        assertThat(ids).contains(feedback3.getId());
        assertThat(ids).contains(feedback1.getId());
        assertThat(automaticAssessmentController.getAssessmentForSimilaritySet(2).get().getFeedbackList().get(0).getId()).isEqualTo(feedback2.getId());
    }

    @Test
    void addFeedbacksToAssessmentActivityDiagram() {
        when(feedback1.getReferenceElementId()).thenReturn("element1Id");
        when(feedback2.getReferenceElementId()).thenReturn("element2Id");
        when(activityDiagram.getElementByJSONID("element1Id")).thenReturn(umlControlFlow);
        when(activityDiagram.getElementByJSONID("element2Id")).thenReturn(umlActivityElement);
        when(umlControlFlow.getSimilarityID()).thenReturn(1);
        when(umlActivityElement.getSimilarityID()).thenReturn(2);

        SimilaritySetAssessment similaritySetAssessment = new SimilaritySetAssessment(feedback3);
        automaticAssessmentController.addSimilaritySetAssessment(1, similaritySetAssessment);
        automaticAssessmentController.addFeedbacksToSimilaritySet(feedbacks, activityDiagram);

        Optional<SimilaritySetAssessment> ssA = automaticAssessmentController.getAssessmentForSimilaritySet(1);
        List<Long> ids = ssA.get().getFeedbackList().stream().map(Feedback::getId).collect(Collectors.toList());
        assertThat(ids).contains(feedback3.getId());
        assertThat(ids).contains(feedback1.getId());
        Optional<SimilaritySetAssessment> ssA2 = automaticAssessmentController.getAssessmentForSimilaritySet(2);
        assertThat(ssA2.get().getFeedbackList().get(0).getId()).isEqualTo(feedback2.getId());
    }

    @Test
    void addFeedbacksToAssessmentNullElements() {
        when(classDiagram.getElementByJSONID("element1Id")).thenReturn(null);
        when(classDiagram.getElementByJSONID("element2Id")).thenReturn(null);

        automaticAssessmentController.addFeedbacksToSimilaritySet(feedbacks, classDiagram);
        Optional<SimilaritySetAssessment> ssA = automaticAssessmentController.getAssessmentForSimilaritySet(1);

        assertThat(automaticAssessmentController.getAssessmentMap().values().size()).isEqualTo(0);
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
    void assessModelAutomaticallyClassDiagram() {
        prepareClassDiagramForAutomaticAssessment();
        prepareAssessmentIndexForAutomaticAssessment();

        CompassResult compassResult = automaticAssessmentController.assessModelAutomatically(classDiagram);

        assertThat(compassResult.entitiesCovered()).isEqualTo(6);
        assertThat(compassResult.getPoints()).isEqualTo(-0.5 - 0.5 + 0 + 1.5 + 1.0 + 0.5);
        assertThat(compassResult.getConfidence()).isEqualTo((0.5 + 0.6 + 0.7 + 0.8 + 0.9 + 1.0) / 6, offset(0.000001));
        CompassResult savedResult = automaticAssessmentController.getLastAssessmentCompassResult(classDiagram.getModelSubmissionId());
        assertThat(savedResult.entitiesCovered()).isEqualTo(6);
        assertThat(savedResult.getPoints()).isEqualTo(-0.5 - 0.5 + 0 + 1.5 + 1.0 + 0.5);
        assertThat(savedResult.getConfidence()).isEqualTo((0.5 + 0.6 + 0.7 + 0.8 + 0.9 + 1.0) / 6, offset(0.000001));
    }

    @Test
    void assessModelAutomaticallyActivityDiagram() {
        prepareActivityDiagramForAutomaticAssessment();
        prepareAssessmentIndexForAutomaticAssessment();

        CompassResult compassResult = automaticAssessmentController.assessModelAutomatically(activityDiagram);

        assertThat(compassResult.entitiesCovered()).isEqualTo(6);
        assertThat(compassResult.getPoints()).isEqualTo(-0.5 - 0.5 + 0 + 1.5 + 1.0 + 0.5);
        assertThat(compassResult.getConfidence()).isEqualTo((0.5 + 0.6 + 0.7 + 0.8 + 0.9 + 1.0) / 6, offset(0.000001));
        CompassResult savedResult = automaticAssessmentController.getLastAssessmentCompassResult(activityDiagram.getModelSubmissionId());
        assertThat(savedResult.entitiesCovered()).isEqualTo(6);
        assertThat(savedResult.getPoints()).isEqualTo(-0.5 - 0.5 + 0 + 1.5 + 1.0 + 0.5);
        assertThat(savedResult.getConfidence()).isEqualTo((0.5 + 0.6 + 0.7 + 0.8 + 0.9 + 1.0) / 6, offset(0.000001));
    }

    @Test
    void assessModelAutomaticallyNullScore() {
        when(classDiagram.getClassList()).thenReturn(List.of(umlClass));
        when(umlClass.getSimilarityID()).thenReturn(1);
        when(similaritySetAssessment.getScore()).thenReturn(null);

        CompassResult compassResult = automaticAssessmentController.assessModelAutomatically(classDiagram);

        assertThat(compassResult.entitiesCovered()).isEqualTo(0);
        assertThat(compassResult.getPoints()).isEqualTo(0);
        assertThat(compassResult.getConfidence()).isEqualTo(0);
        CompassResult savedResult = automaticAssessmentController.getLastAssessmentCompassResult(activityDiagram.getModelSubmissionId());
        assertThat(savedResult.entitiesCovered()).isEqualTo(0);
        assertThat(savedResult.getPoints()).isEqualTo(0);
        assertThat(savedResult.getConfidence()).isEqualTo(0);

    }

    @Test
    void isUnassessedTrue() {
        boolean isUnassessed = automaticAssessmentController.isUnassessed(classDiagram.getModelSubmissionId());
        assertThat(isUnassessed).isTrue();
    }

    @Test
    void isUnassessedFalse() {
        when(classDiagram.getModelSubmissionId()).thenReturn(1L);
        CompassResult compassResult = mock(CompassResult.class);
        automaticAssessmentController.setLastAssessmentCompassResult(1L, compassResult);
        boolean isUnassessed = automaticAssessmentController.isUnassessed(classDiagram.getModelSubmissionId());
        assertThat(isUnassessed).isFalse();
    }

    @Test
    void getLastAssessmentConfidence() {
        CompassResult compassResult = mock(CompassResult.class, withSettings().serializable());
        doReturn(0.456).when(compassResult).getConfidence();
        automaticAssessmentController.setLastAssessmentCompassResult(classDiagram.getModelSubmissionId(), compassResult);
        double confidence = automaticAssessmentController.getLastAssessmentConfidence(classDiagram.getModelSubmissionId());
        assertThat(confidence).isEqualTo(0.456);
    }

    @Test
    void getLastAssessmentConfidenceNoCompassResult() {
        double confidence = automaticAssessmentController.getLastAssessmentConfidence(classDiagram.getModelSubmissionId());
        assertThat(confidence).isEqualTo(-1);
    }

    @Test
    void getLastAssessmentCoverage() {
        CompassResult compassResult = mock(CompassResult.class, withSettings().serializable());
        doReturn(0.789).when(compassResult).getCoverage();
        automaticAssessmentController.setLastAssessmentCompassResult(classDiagram.getModelSubmissionId(), compassResult);
        double confidence = automaticAssessmentController.getLastAssessmentCoverage(classDiagram.getModelSubmissionId());
        assertThat(confidence).isEqualTo(0.789);
    }

    @Test
    void getLastAssessmentCoverageNoCompassResult() {
        double confidence = automaticAssessmentController.getLastAssessmentCoverage(classDiagram.getModelSubmissionId());
        assertThat(confidence).isEqualTo(-1);
    }

    private void prepareClassDiagramForAutomaticAssessment() {
        UMLAttribute attribute1 = mock(UMLAttribute.class, withSettings().serializable());
        UMLAttribute attribute2 = mock(UMLAttribute.class, withSettings().serializable());
        UMLMethod method1 = mock(UMLMethod.class, withSettings().serializable());
        UMLMethod method2 = mock(UMLMethod.class, withSettings().serializable());
        UMLClass class1 = mock(UMLClass.class, withSettings().serializable());
        UMLClass class2 = mock(UMLClass.class, withSettings().serializable());
        UMLRelationship relationship1 = mock(UMLRelationship.class, withSettings().serializable());
        UMLRelationship relationship2 = mock(UMLRelationship.class, withSettings().serializable());
        UMLPackage package1 = mock(UMLPackage.class, withSettings().serializable());
        UMLPackage package2 = mock(UMLPackage.class, withSettings().serializable());

        when(class1.getAttributes()).thenReturn(Collections.emptyList());
        when(class1.getMethods()).thenReturn(Collections.emptyList());
        when(class1.getElementCount()).thenReturn(1);
        when(class2.getAttributes()).thenReturn(List.of(attribute1, attribute2));
        when(class2.getMethods()).thenReturn(List.of(method1, method2));
        when(class2.getElementCount()).thenReturn(5);

        when(classDiagram.getAllModelElements()).thenReturn(List.of(class1, class2, relationship1, relationship2, package1, package2, attribute1, attribute2, method1, method2));

        when(class1.getSimilarityID()).thenReturn(1);
        when(class1.getJSONElementID()).thenReturn("class1");
        when(class2.getSimilarityID()).thenReturn(2);
        when(class2.getJSONElementID()).thenReturn("class2");
        when(relationship1.getSimilarityID()).thenReturn(3);
        when(relationship1.getJSONElementID()).thenReturn("relationship1");
        when(relationship2.getSimilarityID()).thenReturn(4);
        when(relationship2.getJSONElementID()).thenReturn("relationship2");
        when(package1.getSimilarityID()).thenReturn(5);
        when(package1.getJSONElementID()).thenReturn("package1");
        when(package2.getSimilarityID()).thenReturn(6);
        when(package2.getJSONElementID()).thenReturn("package2");
        when(attribute1.getSimilarityID()).thenReturn(7);
        when(attribute1.getJSONElementID()).thenReturn("attribute1");
        when(attribute2.getSimilarityID()).thenReturn(8);
        when(attribute2.getJSONElementID()).thenReturn("attribute2");
        when(method1.getSimilarityID()).thenReturn(9);
        when(method1.getJSONElementID()).thenReturn("method1");
        when(method2.getSimilarityID()).thenReturn(10);
        when(method2.getJSONElementID()).thenReturn("method2");
    }

    private void prepareActivityDiagramForAutomaticAssessment() {
        UMLActivityNode activityNode1 = mock(UMLActivityNode.class, withSettings().serializable());
        UMLActivityNode activityNode2 = mock(UMLActivityNode.class, withSettings().serializable());
        UMLActivityNode activityNode3 = mock(UMLActivityNode.class, withSettings().serializable());
        UMLActivity activity1 = mock(UMLActivity.class, withSettings().serializable());
        UMLActivity activity2 = mock(UMLActivity.class, withSettings().serializable());
        UMLActivity activity3 = mock(UMLActivity.class, withSettings().serializable());
        UMLControlFlow controlFlow1 = mock(UMLControlFlow.class, withSettings().serializable());
        UMLControlFlow controlFlow2 = mock(UMLControlFlow.class, withSettings().serializable());
        UMLControlFlow controlFlow3 = mock(UMLControlFlow.class, withSettings().serializable());

        when(activityDiagram.getAllModelElements())
                .thenReturn(List.of(activityNode1, activityNode2, activityNode3, activity1, activity2, activity3, controlFlow1, controlFlow2, controlFlow3));

        when(activityNode1.getSimilarityID()).thenReturn(1);
        when(activityNode1.getJSONElementID()).thenReturn("activityNode1");
        when(activityNode2.getSimilarityID()).thenReturn(2);
        when(activityNode2.getJSONElementID()).thenReturn("activityNode2");
        when(activityNode3.getSimilarityID()).thenReturn(4);
        when(activityNode3.getJSONElementID()).thenReturn("activityNode3");
        when(activity1.getSimilarityID()).thenReturn(3);
        when(activity1.getJSONElementID()).thenReturn("activity1");
        when(activity2.getSimilarityID()).thenReturn(5);
        when(activity2.getJSONElementID()).thenReturn("activity2");
        when(activity3.getSimilarityID()).thenReturn(6);
        when(activity3.getJSONElementID()).thenReturn("activity3");
        when(controlFlow1.getSimilarityID()).thenReturn(7);
        when(controlFlow1.getJSONElementID()).thenReturn("controlFlow1");
        when(controlFlow2.getSimilarityID()).thenReturn(8);
        when(controlFlow2.getJSONElementID()).thenReturn("controlFlow2");
        when(controlFlow3.getSimilarityID()).thenReturn(9);
        when(controlFlow3.getJSONElementID()).thenReturn("controlFlow3");
    }

    private void prepareAssessmentIndexForAutomaticAssessment() {
        SimilaritySetAssessment similaritySetAssessment1 = mock(SimilaritySetAssessment.class, withSettings().serializable());
        SimilaritySetAssessment similaritySetAssessment2 = mock(SimilaritySetAssessment.class, withSettings().serializable());
        SimilaritySetAssessment similaritySetAssessment3 = mock(SimilaritySetAssessment.class, withSettings().serializable());
        SimilaritySetAssessment similaritySetAssessment4 = mock(SimilaritySetAssessment.class, withSettings().serializable());
        SimilaritySetAssessment similaritySetAssessment5 = mock(SimilaritySetAssessment.class, withSettings().serializable());
        SimilaritySetAssessment similaritySetAssessment6 = mock(SimilaritySetAssessment.class, withSettings().serializable());

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

        automaticAssessmentController.addSimilaritySetAssessment(1, similaritySetAssessment1);
        automaticAssessmentController.addSimilaritySetAssessment(2, similaritySetAssessment2);
        automaticAssessmentController.addSimilaritySetAssessment(3, similaritySetAssessment3);
        automaticAssessmentController.addSimilaritySetAssessment(6, similaritySetAssessment4);
        automaticAssessmentController.addSimilaritySetAssessment(8, similaritySetAssessment5);
        automaticAssessmentController.addSimilaritySetAssessment(9, similaritySetAssessment6);
    }

    private Score mockScore(double points, double confidence) {
        Score score = mock(Score.class, withSettings().serializable());
        when(score.getPoints()).thenReturn(points);
        when(score.getConfidence()).thenReturn(confidence);
        return score;
    }
}
