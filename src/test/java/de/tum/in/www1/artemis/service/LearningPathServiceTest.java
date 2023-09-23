package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.competency.CompetencyUtilService;
import de.tum.in.www1.artemis.competency.LearningPathUtilService;
import de.tum.in.www1.artemis.course.CourseFactory;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.competency.Competency;
import de.tum.in.www1.artemis.domain.competency.CompetencyRelation;
import de.tum.in.www1.artemis.domain.competency.LearningPath;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseUtilService;
import de.tum.in.www1.artemis.lecture.LectureUtilService;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.LearningPathRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.web.rest.dto.competency.LearningPathHealthDTO;
import de.tum.in.www1.artemis.web.rest.dto.competency.NgxLearningPathDTO;

class LearningPathServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "learningpathservice";

    @Autowired
    private LearningPathService learningPathService;

    @Autowired
    private LearningPathUtilService learningPathUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CompetencyUtilService competencyUtilService;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private LearningPathRepository learningPathRepository;

    private Course course;

    @BeforeEach
    void setAuthorizationForRepositoryRequests() {
        SecurityUtils.setAuthorizationObject();
    }

    @BeforeEach
    void setup() {
        course = courseUtilService.createCourse();
    }

    @Nested
    class HeathCheckTest {

        @BeforeEach
        void setup() {
            userUtilService.addUsers(TEST_PREFIX, 5, 1, 1, 1);
            course = CourseFactory.generateCourse(null, ZonedDateTime.now().minusDays(8), ZonedDateTime.now().minusDays(8), new HashSet<>(), TEST_PREFIX + "tumuser",
                    TEST_PREFIX + "tutor", TEST_PREFIX + "editor", TEST_PREFIX + "instructor");
            course = courseRepository.save(course);
        }

        @Test
        void testHealthStatusDisabled() {
            var healthStatus = learningPathService.getHealthStatusForCourse(course);
            assertThat(healthStatus.status()).isEqualTo(LearningPathHealthDTO.HealthStatus.DISABLED);
        }

        @Test
        void testHealthStatusOK() {
            course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
            var healthStatus = learningPathService.getHealthStatusForCourse(course);
            assertThat(healthStatus.status()).isEqualTo(LearningPathHealthDTO.HealthStatus.OK);
        }

        @Test
        void testHealthStatusMissing() {
            course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
            userUtilService.addStudent(TEST_PREFIX + "tumuser", TEST_PREFIX + "student1337");
            var healthStatus = learningPathService.getHealthStatusForCourse(course);
            assertThat(healthStatus.status()).isEqualTo(LearningPathHealthDTO.HealthStatus.MISSING);
            assertThat(healthStatus.missingLearningPaths()).isEqualTo(1);
        }
    }

    @Nested
    class GenerateNgxRepresentationBaseTest {

        @Test
        void testEmptyLearningPath() {
            NgxLearningPathDTO expected = new NgxLearningPathDTO(Set.of(), Set.of());
            generateAndAssert(expected);
        }

        @Test
        void testEmptyCompetency() {
            final var competency = competencyUtilService.createCompetency(course);
            final var startNodeId = LearningPathService.getCompetencyStartNodeId(competency.getId());
            final var endNodeId = LearningPathService.getCompetencyEndNodeId(competency.getId());
            Set<NgxLearningPathDTO.Node> expectedNodes = getExpectedNodesOfEmptyCompetency(competency);
            Set<NgxLearningPathDTO.Edge> expectedEdges = Set.of(new NgxLearningPathDTO.Edge(LearningPathService.getDirectEdgeId(competency.getId()), startNodeId, endNodeId));
            NgxLearningPathDTO expected = new NgxLearningPathDTO(expectedNodes, expectedEdges);
            generateAndAssert(expected);
        }

        @Test
        void testCompetencyWithLectureUnitAndExercise() {
            var competency = competencyUtilService.createCompetency(course);
            var lecture = lectureUtilService.createLecture(course, ZonedDateTime.now());
            final var lectureUnit = lectureUtilService.createTextUnit();
            lectureUtilService.addLectureUnitsToLecture(lecture, List.of(lectureUnit));
            competencyUtilService.linkLectureUnitToCompetency(competency, lectureUnit);
            final var exercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course, false);
            competencyUtilService.linkExerciseToCompetency(competency, exercise);
            final var startNodeId = LearningPathService.getCompetencyStartNodeId(competency.getId());
            final var endNodeId = LearningPathService.getCompetencyEndNodeId(competency.getId());
            Set<NgxLearningPathDTO.Node> expectedNodes = getExpectedNodesOfEmptyCompetency(competency);
            expectedNodes.add(getNodeForLectureUnit(competency, lectureUnit));
            expectedNodes.add(getNodeForExercise(competency, exercise));
            Set<NgxLearningPathDTO.Edge> expectedEdges = Set.of(
                    new NgxLearningPathDTO.Edge(LearningPathService.getLectureUnitInEdgeId(competency.getId(), lectureUnit.getId()), startNodeId,
                            LearningPathService.getLectureUnitNodeId(competency.getId(), lectureUnit.getId())),
                    new NgxLearningPathDTO.Edge(LearningPathService.getLectureUnitOutEdgeId(competency.getId(), lectureUnit.getId()),
                            LearningPathService.getLectureUnitNodeId(competency.getId(), lectureUnit.getId()), endNodeId),
                    new NgxLearningPathDTO.Edge(LearningPathService.getExerciseInEdgeId(competency.getId(), exercise.getId()), startNodeId,
                            LearningPathService.getExerciseNodeId(competency.getId(), exercise.getId())),
                    new NgxLearningPathDTO.Edge(LearningPathService.getExerciseOutEdgeId(competency.getId(), exercise.getId()),
                            LearningPathService.getExerciseNodeId(competency.getId(), exercise.getId()), endNodeId));
            NgxLearningPathDTO expected = new NgxLearningPathDTO(expectedNodes, expectedEdges);
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
            for (int i = 0; i < competencies.length; i++) {
                expectedNodes.addAll(getExpectedNodesOfEmptyCompetency(competencies[i]));
                expectedEdges.add(new NgxLearningPathDTO.Edge(LearningPathService.getDirectEdgeId(competencies[i].getId()), startNodeIds[i], endNodeIds[i]));
            }
            NgxLearningPathDTO expected = new NgxLearningPathDTO(expectedNodes, expectedEdges);
            generateAndAssert(expected);
        }
    }

    @Nested
    class GenerateNgxRepresentationRelationTest {

        private Competency competency1;

        private Competency competency2;

        private Set<NgxLearningPathDTO.Node> expectedNodes;

        Set<NgxLearningPathDTO.Edge> expectedEdges;

        @BeforeEach
        void setup() {
            competency1 = competencyUtilService.createCompetency(course);
            competency2 = competencyUtilService.createCompetency(course);
            expectedNodes = new HashSet<>();
            expectedEdges = new HashSet<>();
            addExpectedComponentsForEmptyCompetencies(competency1, competency2);
        }

        void testSimpleRelation(CompetencyRelation.RelationType type) {
            competencyUtilService.addRelation(competency1, type, competency2);
            final var sourceNodeId = LearningPathService.getCompetencyEndNodeId(competency2.getId());
            final var targetNodeId = LearningPathService.getCompetencyStartNodeId(competency1.getId());
            expectedEdges.add(new NgxLearningPathDTO.Edge(LearningPathService.getRelationEdgeId(sourceNodeId, targetNodeId), sourceNodeId, targetNodeId));
            NgxLearningPathDTO expected = new NgxLearningPathDTO(expectedNodes, expectedEdges);
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
            expectedNodes.add(new NgxLearningPathDTO.Node(LearningPathService.getMatchingClusterStartNodeId(0), NgxLearningPathDTO.NodeType.MATCH_START, null, ""));
            expectedNodes.add(new NgxLearningPathDTO.Node(LearningPathService.getMatchingClusterEndNodeId(0), NgxLearningPathDTO.NodeType.MATCH_END, null, ""));
            expectedEdges.add(new NgxLearningPathDTO.Edge(LearningPathService.getInEdgeId(competency1.getId()), LearningPathService.getMatchingClusterStartNodeId(0),
                    LearningPathService.getCompetencyStartNodeId(competency1.getId())));
            expectedEdges.add(new NgxLearningPathDTO.Edge(LearningPathService.getOutEdgeId(competency1.getId()), LearningPathService.getCompetencyEndNodeId(competency1.getId()),
                    LearningPathService.getMatchingClusterEndNodeId(0)));
            expectedEdges.add(new NgxLearningPathDTO.Edge(LearningPathService.getInEdgeId(competency2.getId()), LearningPathService.getMatchingClusterStartNodeId(0),
                    LearningPathService.getCompetencyStartNodeId(competency2.getId())));
            expectedEdges.add(new NgxLearningPathDTO.Edge(LearningPathService.getOutEdgeId(competency2.getId()), LearningPathService.getCompetencyEndNodeId(competency2.getId()),
                    LearningPathService.getMatchingClusterEndNodeId(0)));
            NgxLearningPathDTO expected = new NgxLearningPathDTO(expectedNodes, expectedEdges);
            generateAndAssert(expected);
        }

        @Test
        void testMatchesTransitive() {
            var competency3 = competencyUtilService.createCompetency(course);
            addExpectedComponentsForEmptyCompetencies(competency3);

            competencyUtilService.addRelation(competency1, CompetencyRelation.RelationType.MATCHES, competency2);
            competencyUtilService.addRelation(competency2, CompetencyRelation.RelationType.MATCHES, competency3);
            expectedNodes.add(new NgxLearningPathDTO.Node(LearningPathService.getMatchingClusterStartNodeId(0), NgxLearningPathDTO.NodeType.MATCH_START, null, ""));
            expectedNodes.add(new NgxLearningPathDTO.Node(LearningPathService.getMatchingClusterEndNodeId(0), NgxLearningPathDTO.NodeType.MATCH_END, null, ""));
            expectedEdges.add(new NgxLearningPathDTO.Edge(LearningPathService.getInEdgeId(competency1.getId()), LearningPathService.getMatchingClusterStartNodeId(0),
                    LearningPathService.getCompetencyStartNodeId(competency1.getId())));
            expectedEdges.add(new NgxLearningPathDTO.Edge(LearningPathService.getOutEdgeId(competency1.getId()), LearningPathService.getCompetencyEndNodeId(competency1.getId()),
                    LearningPathService.getMatchingClusterEndNodeId(0)));
            expectedEdges.add(new NgxLearningPathDTO.Edge(LearningPathService.getInEdgeId(competency2.getId()), LearningPathService.getMatchingClusterStartNodeId(0),
                    LearningPathService.getCompetencyStartNodeId(competency2.getId())));
            expectedEdges.add(new NgxLearningPathDTO.Edge(LearningPathService.getOutEdgeId(competency2.getId()), LearningPathService.getCompetencyEndNodeId(competency2.getId()),
                    LearningPathService.getMatchingClusterEndNodeId(0)));
            expectedEdges.add(new NgxLearningPathDTO.Edge(LearningPathService.getInEdgeId(competency3.getId()), LearningPathService.getMatchingClusterStartNodeId(0),
                    LearningPathService.getCompetencyStartNodeId(competency3.getId())));
            expectedEdges.add(new NgxLearningPathDTO.Edge(LearningPathService.getOutEdgeId(competency3.getId()), LearningPathService.getCompetencyEndNodeId(competency3.getId()),
                    LearningPathService.getMatchingClusterEndNodeId(0)));
            NgxLearningPathDTO expected = new NgxLearningPathDTO(expectedNodes, expectedEdges);
            generateAndAssert(expected);
        }

        private void addExpectedComponentsForEmptyCompetencies(Competency... competencies) {
            for (var competency : competencies) {
                expectedNodes.addAll(getExpectedNodesOfEmptyCompetency(competency));
                expectedEdges.add(new NgxLearningPathDTO.Edge(LearningPathService.getDirectEdgeId(competency.getId()),
                        LearningPathService.getCompetencyStartNodeId(competency.getId()), LearningPathService.getCompetencyEndNodeId(competency.getId())));
            }
        }
    }

    private void generateAndAssert(NgxLearningPathDTO expected) {
        LearningPath learningPath = learningPathUtilService.createLearningPathInCourse(course);
        learningPath = learningPathRepository.findWithEagerCompetenciesAndLearningObjectsAndCompletedUsersByIdElseThrow(learningPath.getId());
        NgxLearningPathDTO actual = learningPathService.generateNgxGraphRepresentation(learningPath);
        assertThat(actual).isNotNull();
        assertNgxRepEquals(actual, expected);
    }

    private void assertNgxRepEquals(NgxLearningPathDTO was, NgxLearningPathDTO expected) {
        assertThat(was.nodes()).as("correct nodes").containsExactlyInAnyOrderElementsOf(expected.nodes());
        assertThat(was.edges()).as("correct edges").containsExactlyInAnyOrderElementsOf(expected.edges());
    }

    private static Set<NgxLearningPathDTO.Node> getExpectedNodesOfEmptyCompetency(Competency competency) {
        return new HashSet<>(Set.of(
                new NgxLearningPathDTO.Node(LearningPathService.getCompetencyStartNodeId(competency.getId()), NgxLearningPathDTO.NodeType.COMPETENCY_START, competency.getId(), ""),
                new NgxLearningPathDTO.Node(LearningPathService.getCompetencyEndNodeId(competency.getId()), NgxLearningPathDTO.NodeType.COMPETENCY_END, competency.getId(), "")));
    }

    private static NgxLearningPathDTO.Node getNodeForLectureUnit(Competency competency, LectureUnit lectureUnit) {
        return new NgxLearningPathDTO.Node(LearningPathService.getLectureUnitNodeId(competency.getId(), lectureUnit.getId()), NgxLearningPathDTO.NodeType.LECTURE_UNIT,
                lectureUnit.getId(), lectureUnit.getLecture().getId(), false, lectureUnit.getName());
    }

    private static NgxLearningPathDTO.Node getNodeForExercise(Competency competency, Exercise exercise) {
        return new NgxLearningPathDTO.Node(LearningPathService.getExerciseNodeId(competency.getId(), exercise.getId()), NgxLearningPathDTO.NodeType.EXERCISE, exercise.getId(),
                exercise.getTitle());
    }
}
