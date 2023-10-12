package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.competency.CompetencyProgressUtilService;
import de.tum.in.www1.artemis.competency.CompetencyUtilService;
import de.tum.in.www1.artemis.competency.LearningPathUtilService;
import de.tum.in.www1.artemis.course.CourseFactory;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.competency.Competency;
import de.tum.in.www1.artemis.domain.competency.CompetencyRelation;
import de.tum.in.www1.artemis.domain.competency.LearningPath;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseUtilService;
import de.tum.in.www1.artemis.lecture.LectureUtilService;
import de.tum.in.www1.artemis.repository.CompetencyRepository;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.LearningPathRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.web.rest.LearningPathResource;
import de.tum.in.www1.artemis.web.rest.dto.competency.LearningPathHealthDTO;
import de.tum.in.www1.artemis.web.rest.dto.competency.NgxLearningPathDTO;

class LearningPathServiceTest extends AbstractSpringIntegrationIndependentTest {

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

    @Autowired
    private CompetencyRepository competencyRepository;

    @Autowired
    private CompetencyProgressUtilService competencyProgressUtilService;

    private Course course;

    private User user;

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
            assertThat(healthStatus.status()).containsExactly(LearningPathHealthDTO.HealthStatus.DISABLED);
            assertThat(healthStatus.missingLearningPaths()).isNull();
        }

        @Test
        void testHealthStatusOK() {
            final var competency1 = competencyUtilService.createCompetency(course);
            final var competency2 = competencyUtilService.createCompetency(course);
            competencyUtilService.addRelation(competency1, CompetencyRelation.RelationType.MATCHES, competency2);
            course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
            var healthStatus = learningPathService.getHealthStatusForCourse(course);
            assertThat(healthStatus.status()).containsExactly(LearningPathHealthDTO.HealthStatus.OK);
            assertThat(healthStatus.missingLearningPaths()).isNull();
        }

        @Test
        void testHealthStatusMissing() {
            final var competency1 = competencyUtilService.createCompetency(course);
            final var competency2 = competencyUtilService.createCompetency(course);
            competencyUtilService.addRelation(competency1, CompetencyRelation.RelationType.MATCHES, competency2);
            course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
            userUtilService.addStudent(TEST_PREFIX + "tumuser", TEST_PREFIX + "student1337");
            var healthStatus = learningPathService.getHealthStatusForCourse(course);
            assertThat(healthStatus.status()).containsExactly(LearningPathHealthDTO.HealthStatus.MISSING);
            assertThat(healthStatus.missingLearningPaths()).isEqualTo(1);
        }

        @Test
        void testHealthStatusNoCompetencies() {
            course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
            var healthStatus = learningPathService.getHealthStatusForCourse(course);
            assertThat(healthStatus.status()).containsExactlyInAnyOrder(LearningPathHealthDTO.HealthStatus.NO_COMPETENCIES, LearningPathHealthDTO.HealthStatus.NO_RELATIONS);
            assertThat(healthStatus.missingLearningPaths()).isNull();
        }

        @Test
        void testHealthStatusNoRelations() {
            competencyUtilService.createCompetency(course);
            competencyUtilService.createCompetency(course);
            course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
            var healthStatus = learningPathService.getHealthStatusForCourse(course);
            assertThat(healthStatus.status()).containsExactly(LearningPathHealthDTO.HealthStatus.NO_RELATIONS);
            assertThat(healthStatus.missingLearningPaths()).isNull();
        }
    }

    @Nested
    class GenerateNgxGraphRepresentationBaseTest {

        @Test
        void testEmptyLearningPath() {
            NgxLearningPathDTO expected = new NgxLearningPathDTO(Set.of(), Set.of());
            generateGraphAndAssert(expected);
        }

        @Test
        void testEmptyCompetency() {
            final var competency = competencyUtilService.createCompetency(course);
            final var startNodeId = LearningPathService.getCompetencyStartNodeId(competency.getId());
            final var endNodeId = LearningPathService.getCompetencyEndNodeId(competency.getId());
            Set<NgxLearningPathDTO.Node> expectedNodes = getExpectedNodesOfEmptyCompetency(competency);
            Set<NgxLearningPathDTO.Edge> expectedEdges = Set.of(new NgxLearningPathDTO.Edge(LearningPathService.getDirectEdgeId(competency.getId()), startNodeId, endNodeId));
            NgxLearningPathDTO expected = new NgxLearningPathDTO(expectedNodes, expectedEdges);
            generateGraphAndAssert(expected);
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
            generateGraphAndAssert(expected);
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
            generateGraphAndAssert(expected);
        }
    }

    @Nested
    class GenerateNgxGraphRepresentationRelationTest {

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
            addExpectedComponentsForEmptyCompetencies(expectedNodes, expectedEdges, competency1, competency2);
        }

        void testSimpleRelation(CompetencyRelation.RelationType type) {
            competencyUtilService.addRelation(competency1, type, competency2);
            final var sourceNodeId = LearningPathService.getCompetencyEndNodeId(competency2.getId());
            final var targetNodeId = LearningPathService.getCompetencyStartNodeId(competency1.getId());
            expectedEdges.add(new NgxLearningPathDTO.Edge(LearningPathService.getRelationEdgeId(sourceNodeId, targetNodeId), sourceNodeId, targetNodeId));
            NgxLearningPathDTO expected = new NgxLearningPathDTO(expectedNodes, expectedEdges);
            generateGraphAndAssert(expected);
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
            generateGraphAndAssert(expected);
        }

        @Test
        void testMatchesTransitive() {
            var competency3 = competencyUtilService.createCompetency(course);
            addExpectedComponentsForEmptyCompetencies(expectedNodes, expectedEdges, competency3);

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
            generateGraphAndAssert(expected);
        }
    }

    @Nested
    class GenerateNgxPathRepresentationTest {

        @BeforeEach
        void setup() {
            final var users = userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
            user = users.get(0);
            course = CourseFactory.generateCourse(null, ZonedDateTime.now().minusDays(8), ZonedDateTime.now().minusDays(8), new HashSet<>(), TEST_PREFIX + "tumuser",
                    TEST_PREFIX + "tutor", TEST_PREFIX + "editor", TEST_PREFIX + "instructor");
            course = courseRepository.save(course);
        }

        @Test
        void testUtilityConstantsValid() throws NoSuchFieldException, IllegalAccessException {
            Field extendsUtilityRatioField = LearningPathService.class.getDeclaredField("EXTENDS_UTILITY_RATIO");
            Field assumesUtilityRatioField = LearningPathService.class.getDeclaredField("ASSUMES_UTILITY_RATIO");
            extendsUtilityRatioField.setAccessible(true);
            assumesUtilityRatioField.setAccessible(true);
            final var extendsUtilityRatio = extendsUtilityRatioField.getDouble(null);
            final var assumesUtilityRatio = assumesUtilityRatioField.getDouble(null);
            assertThat(extendsUtilityRatio).isLessThan(assumesUtilityRatio);
        }

        @Test
        void testEmptyLearningPath() {
            NgxLearningPathDTO expected = new NgxLearningPathDTO(Set.of(), Set.of());
            generatePathAndAssert(expected);
        }

        @Test
        void testEmptyCompetency() {
            final var competency = competencyUtilService.createCompetency(course);
            final var startNodeId = LearningPathService.getCompetencyStartNodeId(competency.getId());
            final var endNodeId = LearningPathService.getCompetencyEndNodeId(competency.getId());
            Set<NgxLearningPathDTO.Node> expectedNodes = getExpectedNodesOfEmptyCompetency(competency);
            Set<NgxLearningPathDTO.Edge> expectedEdges = Set.of(new NgxLearningPathDTO.Edge(LearningPathService.getDirectEdgeId(competency.getId()), startNodeId, endNodeId));
            NgxLearningPathDTO expected = new NgxLearningPathDTO(expectedNodes, expectedEdges);
            generatePathAndAssert(expected);
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
            generatePathAndAssert(expected);
        }
    }

    @Nested
    class GenerateNgxPathRepresentationCompetencyOrderTest {

        private Competency competency1;

        private Competency competency2;

        private Set<NgxLearningPathDTO.Node> expectedNodes;

        private Set<NgxLearningPathDTO.Edge> expectedEdges;

        @BeforeEach
        void setup() {
            final var users = userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
            user = users.get(0);
            course = CourseFactory.generateCourse(null, ZonedDateTime.now().minusDays(8), ZonedDateTime.now().minusDays(8), new HashSet<>(), TEST_PREFIX + "tumuser",
                    TEST_PREFIX + "tutor", TEST_PREFIX + "editor", TEST_PREFIX + "instructor");
            course = courseRepository.save(course);

            competency1 = competencyUtilService.createCompetency(course);
            competency2 = competencyUtilService.createCompetency(course);
            expectedNodes = new HashSet<>();
            expectedEdges = new HashSet<>();
            addExpectedComponentsForEmptyCompetencies(expectedNodes, expectedEdges, competency1, competency2);
        }

        private static Stream<Arguments> orderOfCompetenciesByDueDateUtilityProvider() {
            return Stream.of(Arguments.of(future(10), future(20)), Arguments.of(future(20), future(10)), Arguments.of(past(3), future(3)), Arguments.of(future(3), past(3)),
                    Arguments.of(past(10), past(20)), Arguments.of(past(20), past(10)));
        }

        @ParameterizedTest
        @MethodSource("orderOfCompetenciesByDueDateUtilityProvider")
        void testOrderOfCompetenciesByDueDateUtility(ZonedDateTime time1, ZonedDateTime time2) {
            competency1.setSoftDueDate(time1);
            competency1 = competencyRepository.save(competency1);
            competency2.setSoftDueDate(time2);
            competency2 = competencyRepository.save(competency2);

            String sourceNodeId;
            String targetNodeId;
            if (time1.isBefore(time2)) {
                sourceNodeId = LearningPathService.getCompetencyEndNodeId(competency1.getId());
                targetNodeId = LearningPathService.getCompetencyStartNodeId(competency2.getId());
            }
            else {
                sourceNodeId = LearningPathService.getCompetencyEndNodeId(competency2.getId());
                targetNodeId = LearningPathService.getCompetencyStartNodeId(competency1.getId());
            }
            expectedEdges.add(new NgxLearningPathDTO.Edge(LearningPathService.getRelationEdgeId(sourceNodeId, targetNodeId), sourceNodeId, targetNodeId));

            generatePathAndAssert(new NgxLearningPathDTO(expectedNodes, expectedEdges));
        }

        @Test
        void testOrderOfCompetenciesByPriorUtility() {
            competency1.setSoftDueDate(future(200));
            competencyRepository.save(competency1);
            competency2.setSoftDueDate(future(200));
            competencyRepository.save(competency2);
            Competency[] priors1 = competencyUtilService.createCompetencies(course, future(110), future(112), future(114));
            Competency[] priors2 = competencyUtilService.createCompetencies(course, future(111), future(113), future(115));
            ;
            for (var competency : priors1) {
                competencyUtilService.addRelation(competency1, CompetencyRelation.RelationType.RELATES, competency);
            }
            for (var competency : priors2) {
                competencyUtilService.addRelation(competency2, CompetencyRelation.RelationType.RELATES, competency);
            }
            masterCompetencies(priors1);
            masterCompetencies(priors2[0]);

            Competency[] expectedOrder = new Competency[] { priors2[1], priors2[2], competency1, competency2 };
            for (int i = 0; i < expectedOrder.length - 1; i++) {
                var sourceNodeId = LearningPathService.getCompetencyEndNodeId(expectedOrder[i].getId());
                var targetNodeId = LearningPathService.getCompetencyStartNodeId(expectedOrder[i + 1].getId());
                expectedEdges.add(new NgxLearningPathDTO.Edge(LearningPathService.getRelationEdgeId(sourceNodeId, targetNodeId), sourceNodeId, targetNodeId));
            }
            addExpectedComponentsForEmptyCompetencies(expectedNodes, expectedEdges, priors2[1], priors2[2]);

            generatePathAndAssert(new NgxLearningPathDTO(expectedNodes, expectedEdges));
        }

        @Test
        void testOrderOfCompetenciesByExtendsOrAssumesUtility() {
            competency1.setSoftDueDate(future(200));
            competencyRepository.save(competency1);
            competency2.setSoftDueDate(future(200));
            competencyRepository.save(competency2);
            Competency[] priors1 = competencyUtilService.createCompetencies(course, future(110), future(112), future(114));
            Competency[] priors2 = competencyUtilService.createCompetencies(course, future(111), future(113), future(115));
            ;
            competencyUtilService.addRelation(competency1, CompetencyRelation.RelationType.EXTENDS, priors1[0]);
            competencyUtilService.addRelation(competency1, CompetencyRelation.RelationType.EXTENDS, priors1[1]);
            competencyUtilService.addRelation(competency1, CompetencyRelation.RelationType.ASSUMES, priors1[2]);
            competencyUtilService.addRelation(competency2, CompetencyRelation.RelationType.EXTENDS, priors2[0]);
            competencyUtilService.addRelation(competency2, CompetencyRelation.RelationType.ASSUMES, priors2[1]);
            competencyUtilService.addRelation(competency2, CompetencyRelation.RelationType.ASSUMES, priors2[2]);

            Competency[] expectedOrder = new Competency[] { priors1[0], priors2[0], priors1[1], priors2[1], priors1[2], priors2[2], competency1, competency2 };
            for (int i = 0; i < expectedOrder.length - 1; i++) {
                var sourceNodeId = LearningPathService.getCompetencyEndNodeId(expectedOrder[i].getId());
                var targetNodeId = LearningPathService.getCompetencyStartNodeId(expectedOrder[i + 1].getId());
                expectedEdges.add(new NgxLearningPathDTO.Edge(LearningPathService.getRelationEdgeId(sourceNodeId, targetNodeId), sourceNodeId, targetNodeId));
            }

            addExpectedComponentsForEmptyCompetencies(expectedNodes, expectedEdges, priors1);
            addExpectedComponentsForEmptyCompetencies(expectedNodes, expectedEdges, priors2);

            generatePathAndAssert(new NgxLearningPathDTO(expectedNodes, expectedEdges));
        }

        @Test
        void testOrderOfCompetenciesByMasteryUtility() {
            competency1.setMasteryThreshold(100);
            competency1 = competencyRepository.save(competency1);
            competency2.setMasteryThreshold(100);
            competency2 = competencyRepository.save(competency2);

            competencyProgressUtilService.createCompetencyProgress(competency1, user, 30, 30);
            competencyProgressUtilService.createCompetencyProgress(competency2, user, 10, 10);

            var sourceNodeId = LearningPathService.getCompetencyEndNodeId(competency1.getId());
            var targetNodeId = LearningPathService.getCompetencyStartNodeId(competency2.getId());
            expectedEdges.add(new NgxLearningPathDTO.Edge(LearningPathService.getRelationEdgeId(sourceNodeId, targetNodeId), sourceNodeId, targetNodeId));

            generatePathAndAssert(new NgxLearningPathDTO(expectedNodes, expectedEdges));
        }

        private void masterCompetencies(Competency... competencies) {
            for (var competency : competencies) {
                competencyProgressUtilService.createCompetencyProgress(competency, user, 100, 100);
            }
        }
    }

    private void generateGraphAndAssert(NgxLearningPathDTO expected) {
        generateAndAssert(expected, LearningPathResource.NgxRequestType.GRAPH);
    }

    private void generatePathAndAssert(NgxLearningPathDTO expected) {
        generateAndAssert(expected, LearningPathResource.NgxRequestType.PATH);
    }

    private void generateAndAssert(NgxLearningPathDTO expected, LearningPathResource.NgxRequestType type) {
        LearningPath learningPath = learningPathUtilService.createLearningPathInCourseForUser(course, user);
        learningPath = learningPathRepository.findWithEagerCompetenciesAndProgressAndLearningObjectsAndCompletedUsersByIdElseThrow(learningPath.getId());
        NgxLearningPathDTO actual = switch (type) {
            case GRAPH -> learningPathService.generateNgxGraphRepresentation(learningPath);
            case PATH -> learningPathService.generateNgxPathRepresentation(learningPath);
        };
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

    private void addExpectedComponentsForEmptyCompetencies(Set<NgxLearningPathDTO.Node> expectedNodes, Set<NgxLearningPathDTO.Edge> expectedEdges, Competency... competencies) {
        for (var competency : competencies) {
            expectedNodes.addAll(getExpectedNodesOfEmptyCompetency(competency));
            expectedEdges.add(new NgxLearningPathDTO.Edge(LearningPathService.getDirectEdgeId(competency.getId()), LearningPathService.getCompetencyStartNodeId(competency.getId()),
                    LearningPathService.getCompetencyEndNodeId(competency.getId())));
        }
    }

    private static ZonedDateTime now() {
        return ZonedDateTime.now();
    }

    private static ZonedDateTime past(long days) {
        return now().minusDays(days);
    }

    private static ZonedDateTime future(long days) {
        return now().plusDays(days);
    }
}
