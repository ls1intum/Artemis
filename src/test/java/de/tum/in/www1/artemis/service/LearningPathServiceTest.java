package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.competency.CompetencyUtilService;
import de.tum.in.www1.artemis.competency.LearningPathUtilService;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.competency.Competency;
import de.tum.in.www1.artemis.domain.competency.CompetencyRelation;
import de.tum.in.www1.artemis.domain.competency.LearningPath;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseUtilService;
import de.tum.in.www1.artemis.lecture.LectureUtilService;
import de.tum.in.www1.artemis.repository.CompetencyRepository;
import de.tum.in.www1.artemis.repository.LearningPathRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.web.rest.dto.learningpath.NgxLearningPathDTO;

public class LearningPathServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    LearningPathService learningPathService;

    @Autowired
    LearningPathUtilService learningPathUtilService;

    @Autowired
    CourseUtilService courseUtilService;

    @Autowired
    CompetencyUtilService competencyUtilService;

    @Autowired
    LearningPathRepository learningPathRepository;

    @Autowired
    LectureUtilService lectureUtilService;

    @Autowired
    ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    CompetencyRepository competencyRepository;

    private Course course;

    private void generateAndAssert(NgxLearningPathDTO expected) {
        LearningPath learningPath = learningPathUtilService.createLearningPathInCourse(course);
        learningPath = learningPathRepository.findWithEagerCompetenciesAndLearningUnitsByIdElseThrow(learningPath.getId());
        NgxLearningPathDTO actual = learningPathService.generateNgxRepresentation(learningPath);
        assertThat(actual).isNotNull();
        assertNgxRepEquals(actual, expected);
    }

    private void assertNgxRepEquals(NgxLearningPathDTO was, NgxLearningPathDTO expected) {
        assertThat(was.nodes()).as("correct nodes").containsExactlyInAnyOrderElementsOf(expected.nodes());
        assertThat(was.edges()).as("correct edges").containsExactlyInAnyOrderElementsOf(expected.edges());
        assertThat(was.clusters()).as("correct clusters").containsExactlyInAnyOrderElementsOf(expected.clusters());
    }

    @BeforeEach
    void setup() {
        course = courseUtilService.createCourse();
    }

    @Nested
    class GenerateNgxRepresentationBaseTest {

        @BeforeEach
        void setAuthorizationForRepositoryRequests() {
            SecurityUtils.setAuthorizationObject();
        }

        @Test
        void testEmptyLearningPath() {
            NgxLearningPathDTO expected = new NgxLearningPathDTO(Set.of(), Set.of(), Set.of());
            generateAndAssert(expected);
        }

        @Test
        void testEmptyCompetency() {
            final var competency = competencyUtilService.createCompetency(course);
            final var startNodeId = LearningPathService.getCompetencyStartNodeId(competency.getId());
            final var endNodeId = LearningPathService.getCompetencyEndNodeId(competency.getId());
            Set<NgxLearningPathDTO.Node> expectedNodes = getExpectedNodesOfEmptyCompetency(competency);
            Set<NgxLearningPathDTO.Edge> expectedEdges = Set.of(new NgxLearningPathDTO.Edge(LearningPathService.getDirectEdgeId(competency.getId()), startNodeId, endNodeId));
            Set<NgxLearningPathDTO.Cluster> expectedClusters = Set
                    .of(new NgxLearningPathDTO.Cluster(String.valueOf(competency.getId()), competency.getTitle(), Set.of(startNodeId, endNodeId)));
            NgxLearningPathDTO expected = new NgxLearningPathDTO(expectedNodes, expectedEdges, expectedClusters);
            generateAndAssert(expected);
        }

        @Test
        void testCompetencyWithLectureUnitAndExercise() {
            var competency = competencyUtilService.createCompetency(course);
            final var lectureUnit = lectureUtilService.createTextUnit();
            competencyUtilService.linkLectureUnitToCompetency(competency, lectureUnit);
            final var exercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course, false, false, ProgrammingLanguage.JAVA, "Some Title", "someshortname");
            competencyUtilService.linkExerciseToCompetency(competency, exercise);
            final var startNodeId = LearningPathService.getCompetencyStartNodeId(competency.getId());
            final var endNodeId = LearningPathService.getCompetencyEndNodeId(competency.getId());
            HashSet<NgxLearningPathDTO.Node> expectedNodes = getExpectedNodesOfEmptyCompetency(competency);
            expectedNodes.add(getNodeForLectureUnit(competency, lectureUnit));
            expectedNodes.add(getNodeForExercise(competency, exercise));
            Set<NgxLearningPathDTO.Edge> expectedEdges = Set.of(
                    new NgxLearningPathDTO.Edge(LearningPathService.getLectureUnitInEdgeId(competency.getId(), lectureUnit.getId()), startNodeId,
                            LearningPathService.getLectureUnitNodeId(competency.getId(), lectureUnit.getId())),
                    new NgxLearningPathDTO.Edge(LearningPathService.getLectureUnitOutEdgeId(competency.getId(), lectureUnit.getId()),
                            LearningPathService.getLectureUnitNodeId(competency.getId(), lectureUnit.getId()), endNodeId),
                    new NgxLearningPathDTO.Edge(LearningPathService.getExerciseInEdgeId(competency.getId(), exercise.getId()), startNodeId,
                            LearningPathService.getExerciseNodeId(competency.getId(), lectureUnit.getId())),
                    new NgxLearningPathDTO.Edge(LearningPathService.getExerciseOutEdgeId(competency.getId(), exercise.getId()),
                            LearningPathService.getExerciseNodeId(competency.getId(), exercise.getId()), endNodeId));
            Set<NgxLearningPathDTO.Cluster> expectedClusters = Set.of(new NgxLearningPathDTO.Cluster(String.valueOf(competency.getId()), competency.getTitle(),
                    expectedNodes.stream().map(NgxLearningPathDTO.Node::id).collect(Collectors.toSet())));
            NgxLearningPathDTO expected = new NgxLearningPathDTO(expectedNodes, expectedEdges, expectedClusters);
            generateAndAssert(expected);
        }

        @Test
        void testMultipleCompetencies() {
            Competency[] competencies = { competencyUtilService.createCompetency(course), competencyUtilService.createCompetency(course),
                    competencyUtilService.createCompetency(course) };
            String[] startNodeIds = Arrays.stream(competencies).map(Competency::getId).map(LearningPathService::getCompetencyStartNodeId).toArray(String[]::new);
            String[] endNodeIds = Arrays.stream(competencies).map(Competency::getId).map(LearningPathService::getCompetencyEndNodeId).toArray(String[]::new);
            Set<NgxLearningPathDTO.Node> expectedNodes = new HashSet<>();
            Set<NgxLearningPathDTO.Edge> expectedEdges = new HashSet<>();
            Set<NgxLearningPathDTO.Cluster> expectedClusters = new HashSet<>();
            for (int i = 0; i < competencies.length; i++) {
                expectedNodes.addAll(getExpectedNodesOfEmptyCompetency(competencies[i]));
                expectedEdges.add(new NgxLearningPathDTO.Edge(LearningPathService.getDirectEdgeId(competencies[i].getId()), startNodeIds[i], endNodeIds[i]));
                expectedClusters.add(new NgxLearningPathDTO.Cluster(String.valueOf(competencies[i].getId()), competencies[i].getTitle(), Set.of(startNodeIds[i], endNodeIds[i])));
            }
            NgxLearningPathDTO expected = new NgxLearningPathDTO(expectedNodes, expectedEdges, expectedClusters);
            generateAndAssert(expected);
        }
    }

    @Nested
    class GenerateNgxRepresentationRelationTest {

        private Competency competency1;

        private Competency competency2;

        private Set<NgxLearningPathDTO.Node> expectedNodes;

        Set<NgxLearningPathDTO.Edge> expectedEdges;

        Set<NgxLearningPathDTO.Cluster> expectedClusters;

        @BeforeEach
        void setAuthorizationForRepositoryRequests() {
            SecurityUtils.setAuthorizationObject();
        }

        @BeforeEach
        void setup() {
            competency1 = competencyUtilService.createCompetency(course);
            competency2 = competencyUtilService.createCompetency(course);
            expectedNodes = new HashSet<>();
            expectedNodes.addAll(getExpectedNodesOfEmptyCompetency(competency1));
            expectedNodes.addAll(getExpectedNodesOfEmptyCompetency(competency2));
            expectedEdges = new HashSet<>();
            expectedEdges.add(new NgxLearningPathDTO.Edge(LearningPathService.getDirectEdgeId(competency1.getId()),
                    LearningPathService.getCompetencyStartNodeId(competency1.getId()), LearningPathService.getCompetencyEndNodeId(competency1.getId())));
            expectedEdges.add(new NgxLearningPathDTO.Edge(LearningPathService.getDirectEdgeId(competency2.getId()),
                    LearningPathService.getCompetencyStartNodeId(competency2.getId()), LearningPathService.getCompetencyEndNodeId(competency2.getId())));
            expectedClusters = new HashSet<>();
            expectedClusters.add(new NgxLearningPathDTO.Cluster(String.valueOf(competency1.getId()), competency1.getTitle(),
                    Set.of(LearningPathService.getCompetencyStartNodeId(competency1.getId()), LearningPathService.getCompetencyEndNodeId(competency1.getId()))));
            expectedClusters.add(new NgxLearningPathDTO.Cluster(String.valueOf(competency2.getId()), competency2.getTitle(),
                    Set.of(LearningPathService.getCompetencyStartNodeId(competency2.getId()), LearningPathService.getCompetencyEndNodeId(competency2.getId()))));
        }

        void testSimpleRelation(CompetencyRelation.RelationType type) {
            competencyUtilService.addRelation(competency1, type, competency2);
            final var sourceNodeId = LearningPathService.getCompetencyEndNodeId(competency2.getId());
            final var targetNodeId = LearningPathService.getCompetencyStartNodeId(competency1.getId());
            expectedEdges.add(new NgxLearningPathDTO.Edge(LearningPathService.getRelationEdgeId(sourceNodeId, targetNodeId), sourceNodeId, targetNodeId));
            NgxLearningPathDTO expected = new NgxLearningPathDTO(expectedNodes, expectedEdges, expectedClusters);
            generateAndAssert(expected);
        }

        @Test
        void testSingleRelates() {
            testSimpleRelation(CompetencyRelation.RelationType.RELATES);
        }

        @Test
        void testSingleAssumes() {
            testSimpleRelation(CompetencyRelation.RelationType.ASSUMES);
        }

        @Test
        void testSingleExtends() {
            testSimpleRelation(CompetencyRelation.RelationType.EXTENDS);
        }

        @Test
        void testSingleMatches() {
            competencyUtilService.addRelation(competency1, CompetencyRelation.RelationType.MATCHES, competency2);
            expectedNodes.add(new NgxLearningPathDTO.Node(LearningPathService.getMatchingClusterStartNodeId(0), NgxLearningPathDTO.NodeType.COMPETENCY_START, -1, ""));
            expectedNodes.add(new NgxLearningPathDTO.Node(LearningPathService.getMatchingClusterEndNodeId(0), NgxLearningPathDTO.NodeType.COMPETENCY_END, -1, ""));
            expectedEdges.add(new NgxLearningPathDTO.Edge(LearningPathService.getInEdgeId(competency1.getId()), LearningPathService.getMatchingClusterStartNodeId(0),
                    LearningPathService.getCompetencyStartNodeId(competency1.getId())));
            expectedEdges.add(new NgxLearningPathDTO.Edge(LearningPathService.getOutEdgeId(competency1.getId()), LearningPathService.getCompetencyEndNodeId(competency1.getId()),
                    LearningPathService.getMatchingClusterEndNodeId(0)));
            expectedEdges.add(new NgxLearningPathDTO.Edge(LearningPathService.getInEdgeId(competency2.getId()), LearningPathService.getMatchingClusterStartNodeId(0),
                    LearningPathService.getCompetencyStartNodeId(competency2.getId())));
            expectedEdges.add(new NgxLearningPathDTO.Edge(LearningPathService.getOutEdgeId(competency2.getId()), LearningPathService.getCompetencyEndNodeId(competency2.getId()),
                    LearningPathService.getMatchingClusterEndNodeId(0)));
            NgxLearningPathDTO expected = new NgxLearningPathDTO(expectedNodes, expectedEdges, expectedClusters);
            generateAndAssert(expected);
        }
    }

    private static HashSet<NgxLearningPathDTO.Node> getExpectedNodesOfEmptyCompetency(Competency competency) {
        return new HashSet<>(Set.of(
                new NgxLearningPathDTO.Node(LearningPathService.getCompetencyStartNodeId(competency.getId()), NgxLearningPathDTO.NodeType.COMPETENCY_START, competency.getId(), ""),
                new NgxLearningPathDTO.Node(LearningPathService.getCompetencyEndNodeId(competency.getId()), NgxLearningPathDTO.NodeType.COMPETENCY_END, competency.getId(), "")));
    }

    private static NgxLearningPathDTO.Node getNodeForLectureUnit(Competency competency, LectureUnit lectureUnit) {
        return new NgxLearningPathDTO.Node(LearningPathService.getLectureUnitNodeId(competency.getId(), lectureUnit.getId()), NgxLearningPathDTO.NodeType.LECTURE_UNIT,
                lectureUnit.getId(), lectureUnit.getName());
    }

    private static NgxLearningPathDTO.Node getNodeForExercise(Competency competency, Exercise exercise) {
        return new NgxLearningPathDTO.Node(LearningPathService.getExerciseNodeId(competency.getId(), exercise.getId()), NgxLearningPathDTO.NodeType.EXERCISE, exercise.getId(),
                exercise.getTitle());
    }
}
