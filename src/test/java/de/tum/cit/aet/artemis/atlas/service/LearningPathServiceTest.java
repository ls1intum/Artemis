package de.tum.cit.aet.artemis.atlas.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.assessment.util.StudentScoreUtilService;
import de.tum.cit.aet.artemis.atlas.competency.util.CompetencyProgressUtilService;
import de.tum.cit.aet.artemis.atlas.competency.util.CompetencyUtilService;
import de.tum.cit.aet.artemis.atlas.domain.LearningObject;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.LearningPath;
import de.tum.cit.aet.artemis.atlas.domain.competency.RelationType;
import de.tum.cit.aet.artemis.atlas.dto.LearningPathHealthDTO;
import de.tum.cit.aet.artemis.atlas.dto.NgxLearningPathDTO;
import de.tum.cit.aet.artemis.atlas.learningpath.util.LearningPathUtilService;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRepository;
import de.tum.cit.aet.artemis.atlas.service.learningpath.LearningPathNgxService;
import de.tum.cit.aet.artemis.atlas.service.learningpath.LearningPathRecommendationService;
import de.tum.cit.aet.artemis.atlas.service.learningpath.LearningPathService;
import de.tum.cit.aet.artemis.atlas.web.LearningPathResource;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.CourseFactory;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitRepository;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

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
    private CompetencyUtilService competencyUtilService;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private CompetencyRepository competencyRepository;

    @Autowired
    private CompetencyProgressUtilService competencyProgressUtilService;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private StudentScoreUtilService studentScoreUtilService;

    @Autowired
    private LectureUnitRepository lectureUnitRepository;

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
    class HealthCheck {

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
            competencyUtilService.addRelation(competency1, RelationType.MATCHES, competency2);
            course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
            var healthStatus = learningPathService.getHealthStatusForCourse(course);
            assertThat(healthStatus.status()).containsExactly(LearningPathHealthDTO.HealthStatus.OK);
            assertThat(healthStatus.missingLearningPaths()).isNull();
        }

        @Test
        void testHealthStatusMissing() {
            final var competency1 = competencyUtilService.createCompetency(course);
            final var competency2 = competencyUtilService.createCompetency(course);
            competencyUtilService.addRelation(competency1, RelationType.MATCHES, competency2);
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
    class GenerateNgxGraphRepresentationBase {

        @Test
        void testEmptyLearningPath() {
            NgxLearningPathDTO expected = new NgxLearningPathDTO(Set.of(), Set.of());
            generateGraphAndAssert(expected);
        }

        @Test
        void testEmptyCompetency() {
            final var competency = competencyUtilService.createCompetency(course);
            final var startNodeId = LearningPathNgxService.getCompetencyStartNodeId(competency.getId());
            final var endNodeId = LearningPathNgxService.getCompetencyEndNodeId(competency.getId());
            Set<NgxLearningPathDTO.Node> expectedNodes = getExpectedNodesOfEmptyCompetency(competency);
            Set<NgxLearningPathDTO.Edge> expectedEdges = Set.of(new NgxLearningPathDTO.Edge(LearningPathNgxService.getDirectEdgeId(competency.getId()), startNodeId, endNodeId));
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
            final var exercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course);
            exercise.setReleaseDate(null);
            competencyUtilService.linkExerciseToCompetency(competency, exercise);
            final var startNodeId = LearningPathNgxService.getCompetencyStartNodeId(competency.getId());
            final var endNodeId = LearningPathNgxService.getCompetencyEndNodeId(competency.getId());
            Set<NgxLearningPathDTO.Node> expectedNodes = getExpectedNodesOfEmptyCompetency(competency);
            expectedNodes.add(getNodeForLectureUnit(competency, lectureUnit));
            expectedNodes.add(getNodeForExercise(competency, exercise));
            Set<NgxLearningPathDTO.Edge> expectedEdges = Set.of(
                    new NgxLearningPathDTO.Edge(LearningPathNgxService.getLectureUnitInEdgeId(competency.getId(), lectureUnit.getId()), startNodeId,
                            LearningPathNgxService.getLectureUnitNodeId(competency.getId(), lectureUnit.getId())),
                    new NgxLearningPathDTO.Edge(LearningPathNgxService.getLectureUnitOutEdgeId(competency.getId(), lectureUnit.getId()),
                            LearningPathNgxService.getLectureUnitNodeId(competency.getId(), lectureUnit.getId()), endNodeId),
                    new NgxLearningPathDTO.Edge(LearningPathNgxService.getExerciseInEdgeId(competency.getId(), exercise.getId()), startNodeId,
                            LearningPathNgxService.getExerciseNodeId(competency.getId(), exercise.getId())),
                    new NgxLearningPathDTO.Edge(LearningPathNgxService.getExerciseOutEdgeId(competency.getId(), exercise.getId()),
                            LearningPathNgxService.getExerciseNodeId(competency.getId(), exercise.getId()), endNodeId));
            NgxLearningPathDTO expected = new NgxLearningPathDTO(expectedNodes, expectedEdges);
            generateGraphAndAssert(expected);
        }

        @Test
        void testMultipleCompetencies() {
            Competency[] competencies = { competencyUtilService.createCompetency(course), competencyUtilService.createCompetency(course),
                    competencyUtilService.createCompetency(course) };
            String[] startNodeIds = Arrays.stream(competencies).map(Competency::getId).map(LearningPathNgxService::getCompetencyStartNodeId).toArray(String[]::new);
            String[] endNodeIds = Arrays.stream(competencies).map(Competency::getId).map(LearningPathNgxService::getCompetencyEndNodeId).toArray(String[]::new);
            Set<NgxLearningPathDTO.Node> expectedNodes = new HashSet<>();
            Set<NgxLearningPathDTO.Edge> expectedEdges = new HashSet<>();
            for (int i = 0; i < competencies.length; i++) {
                expectedNodes.addAll(getExpectedNodesOfEmptyCompetency(competencies[i]));
                expectedEdges.add(new NgxLearningPathDTO.Edge(LearningPathNgxService.getDirectEdgeId(competencies[i].getId()), startNodeIds[i], endNodeIds[i]));
            }
            NgxLearningPathDTO expected = new NgxLearningPathDTO(expectedNodes, expectedEdges);
            generateGraphAndAssert(expected);
        }
    }

    @Nested
    class GenerateNgxGraphRepresentationRelation {

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

        void testSimpleRelation(RelationType type) {
            competencyUtilService.addRelation(competency1, type, competency2);
            final var sourceNodeId = LearningPathNgxService.getCompetencyEndNodeId(competency2.getId());
            final var targetNodeId = LearningPathNgxService.getCompetencyStartNodeId(competency1.getId());
            expectedEdges.add(new NgxLearningPathDTO.Edge(LearningPathNgxService.getEdgeFromToId(sourceNodeId, targetNodeId), sourceNodeId, targetNodeId));
            NgxLearningPathDTO expected = new NgxLearningPathDTO(expectedNodes, expectedEdges);
            generateGraphAndAssert(expected);
        }

        @Test
        void testSingleAssumes() {
            testSimpleRelation(RelationType.ASSUMES);
        }

        @Test
        void testSingleExtends() {
            testSimpleRelation(RelationType.EXTENDS);
        }

        @Test
        void testSingleMatches() {
            competencyUtilService.addRelation(competency1, RelationType.MATCHES, competency2);
            expectedNodes.add(NgxLearningPathDTO.Node.of(LearningPathNgxService.getMatchingClusterStartNodeId(0), NgxLearningPathDTO.NodeType.MATCH_START, null, ""));
            expectedNodes.add(NgxLearningPathDTO.Node.of(LearningPathNgxService.getMatchingClusterEndNodeId(0), NgxLearningPathDTO.NodeType.MATCH_END, null, ""));
            expectedEdges.add(new NgxLearningPathDTO.Edge(LearningPathNgxService.getInEdgeId(competency1.getId()), LearningPathNgxService.getMatchingClusterStartNodeId(0),
                    LearningPathNgxService.getCompetencyStartNodeId(competency1.getId())));
            expectedEdges.add(new NgxLearningPathDTO.Edge(LearningPathNgxService.getOutEdgeId(competency1.getId()),
                    LearningPathNgxService.getCompetencyEndNodeId(competency1.getId()), LearningPathNgxService.getMatchingClusterEndNodeId(0)));
            expectedEdges.add(new NgxLearningPathDTO.Edge(LearningPathNgxService.getInEdgeId(competency2.getId()), LearningPathNgxService.getMatchingClusterStartNodeId(0),
                    LearningPathNgxService.getCompetencyStartNodeId(competency2.getId())));
            expectedEdges.add(new NgxLearningPathDTO.Edge(LearningPathNgxService.getOutEdgeId(competency2.getId()),
                    LearningPathNgxService.getCompetencyEndNodeId(competency2.getId()), LearningPathNgxService.getMatchingClusterEndNodeId(0)));
            NgxLearningPathDTO expected = new NgxLearningPathDTO(expectedNodes, expectedEdges);
            generateGraphAndAssert(expected);
        }

        @Test
        void testMatchesTransitive() {
            var competency3 = competencyUtilService.createCompetency(course);
            addExpectedComponentsForEmptyCompetencies(expectedNodes, expectedEdges, competency3);

            competencyUtilService.addRelation(competency1, RelationType.MATCHES, competency2);
            competencyUtilService.addRelation(competency2, RelationType.MATCHES, competency3);
            expectedNodes.add(NgxLearningPathDTO.Node.of(LearningPathNgxService.getMatchingClusterStartNodeId(0), NgxLearningPathDTO.NodeType.MATCH_START, null, ""));
            expectedNodes.add(NgxLearningPathDTO.Node.of(LearningPathNgxService.getMatchingClusterEndNodeId(0), NgxLearningPathDTO.NodeType.MATCH_END, null, ""));
            expectedEdges.add(new NgxLearningPathDTO.Edge(LearningPathNgxService.getInEdgeId(competency1.getId()), LearningPathNgxService.getMatchingClusterStartNodeId(0),
                    LearningPathNgxService.getCompetencyStartNodeId(competency1.getId())));
            expectedEdges.add(new NgxLearningPathDTO.Edge(LearningPathNgxService.getOutEdgeId(competency1.getId()),
                    LearningPathNgxService.getCompetencyEndNodeId(competency1.getId()), LearningPathNgxService.getMatchingClusterEndNodeId(0)));
            expectedEdges.add(new NgxLearningPathDTO.Edge(LearningPathNgxService.getInEdgeId(competency2.getId()), LearningPathNgxService.getMatchingClusterStartNodeId(0),
                    LearningPathNgxService.getCompetencyStartNodeId(competency2.getId())));
            expectedEdges.add(new NgxLearningPathDTO.Edge(LearningPathNgxService.getOutEdgeId(competency2.getId()),
                    LearningPathNgxService.getCompetencyEndNodeId(competency2.getId()), LearningPathNgxService.getMatchingClusterEndNodeId(0)));
            expectedEdges.add(new NgxLearningPathDTO.Edge(LearningPathNgxService.getInEdgeId(competency3.getId()), LearningPathNgxService.getMatchingClusterStartNodeId(0),
                    LearningPathNgxService.getCompetencyStartNodeId(competency3.getId())));
            expectedEdges.add(new NgxLearningPathDTO.Edge(LearningPathNgxService.getOutEdgeId(competency3.getId()),
                    LearningPathNgxService.getCompetencyEndNodeId(competency3.getId()), LearningPathNgxService.getMatchingClusterEndNodeId(0)));
            NgxLearningPathDTO expected = new NgxLearningPathDTO(expectedNodes, expectedEdges);
            generateGraphAndAssert(expected);
        }
    }

    @Nested
    class GenerateNgxPathRepresentation {

        @BeforeEach
        void setup() {
            final var users = userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
            user = users.getFirst();
            course = CourseFactory.generateCourse(null, ZonedDateTime.now().minusDays(8), ZonedDateTime.now().minusDays(8), new HashSet<>(), TEST_PREFIX + "tumuser",
                    TEST_PREFIX + "tutor", TEST_PREFIX + "editor", TEST_PREFIX + "instructor");
            course = courseRepository.save(course);
        }

        @Test
        void testUtilityConstantsValid() throws NoSuchFieldException, IllegalAccessException {
            Field extendsUtilityRatioField = LearningPathRecommendationService.class.getDeclaredField("EXTENDS_UTILITY_RATIO");
            Field assumesUtilityRatioField = LearningPathRecommendationService.class.getDeclaredField("ASSUMES_UTILITY_RATIO");
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
            final var startNodeId = LearningPathNgxService.getCompetencyStartNodeId(competency.getId());
            final var endNodeId = LearningPathNgxService.getCompetencyEndNodeId(competency.getId());
            Set<NgxLearningPathDTO.Node> expectedNodes = getExpectedNodesOfEmptyCompetency(competency);
            Set<NgxLearningPathDTO.Edge> expectedEdges = Set.of(new NgxLearningPathDTO.Edge(LearningPathNgxService.getDirectEdgeId(competency.getId()), startNodeId, endNodeId));
            NgxLearningPathDTO expected = new NgxLearningPathDTO(expectedNodes, expectedEdges);
            generatePathAndAssert(expected);
        }
    }

    @Nested
    class GenerateNgxPathRepresentationCompetencyOrder {

        private Competency competency1;

        private Competency competency2;

        private Set<NgxLearningPathDTO.Node> expectedNodes;

        private Set<NgxLearningPathDTO.Edge> expectedEdges;

        @BeforeEach
        void setup() {
            final var users = userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
            user = users.getFirst();
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
                sourceNodeId = LearningPathNgxService.getCompetencyEndNodeId(competency1.getId());
                targetNodeId = LearningPathNgxService.getCompetencyStartNodeId(competency2.getId());
            }
            else {
                sourceNodeId = LearningPathNgxService.getCompetencyEndNodeId(competency2.getId());
                targetNodeId = LearningPathNgxService.getCompetencyStartNodeId(competency1.getId());
            }
            expectedEdges.add(new NgxLearningPathDTO.Edge(LearningPathNgxService.getEdgeFromToId(sourceNodeId, targetNodeId), sourceNodeId, targetNodeId));

            generatePathAndAssert(new NgxLearningPathDTO(expectedNodes, expectedEdges));
        }

        @Test
        void testOrderOfCompetenciesByPriorUtility() {
            competency1.setSoftDueDate(future(150));
            competencyRepository.save(competency1);
            competency2.setSoftDueDate(future(200));
            competencyRepository.save(competency2);
            Competency[] priors1 = competencyUtilService.createCompetencies(course, future(110), future(112), future(114));
            Competency[] priors2 = competencyUtilService.createCompetencies(course, future(111), future(113), future(115));

            for (var competency : priors1) {
                competencyUtilService.addRelation(competency1, RelationType.ASSUMES, competency);
            }
            for (var competency : priors2) {
                competencyUtilService.addRelation(competency2, RelationType.ASSUMES, competency);
            }
            masterCompetencies(priors1);
            masterCompetencies(priors2[0]);

            Competency[] expectedOrder = new Competency[] { priors2[1], priors2[2], competency1, competency2 };
            for (int i = 0; i < expectedOrder.length - 1; i++) {
                var sourceNodeId = LearningPathNgxService.getCompetencyEndNodeId(expectedOrder[i].getId());
                var targetNodeId = LearningPathNgxService.getCompetencyStartNodeId(expectedOrder[i + 1].getId());
                expectedEdges.add(new NgxLearningPathDTO.Edge(LearningPathNgxService.getEdgeFromToId(sourceNodeId, targetNodeId), sourceNodeId, targetNodeId));
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

            competencyUtilService.addRelation(competency1, RelationType.EXTENDS, priors1[0]);
            competencyUtilService.addRelation(competency1, RelationType.EXTENDS, priors1[1]);
            competencyUtilService.addRelation(competency1, RelationType.ASSUMES, priors1[2]);
            competencyUtilService.addRelation(competency2, RelationType.EXTENDS, priors2[0]);
            competencyUtilService.addRelation(competency2, RelationType.ASSUMES, priors2[1]);
            competencyUtilService.addRelation(competency2, RelationType.ASSUMES, priors2[2]);

            Competency[] expectedOrder = new Competency[] { priors1[0], priors2[0], priors1[1], priors2[1], priors1[2], priors2[2], competency1, competency2 };
            for (int i = 0; i < expectedOrder.length - 1; i++) {
                var sourceNodeId = LearningPathNgxService.getCompetencyEndNodeId(expectedOrder[i].getId());
                var targetNodeId = LearningPathNgxService.getCompetencyStartNodeId(expectedOrder[i + 1].getId());
                expectedEdges.add(new NgxLearningPathDTO.Edge(LearningPathNgxService.getEdgeFromToId(sourceNodeId, targetNodeId), sourceNodeId, targetNodeId));
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

            competencyProgressUtilService.createCompetencyProgress(competency1, user, 30, 1.1);
            competencyProgressUtilService.createCompetencyProgress(competency2, user, 10, 0.9);

            var sourceNodeId = LearningPathNgxService.getCompetencyEndNodeId(competency1.getId());
            var targetNodeId = LearningPathNgxService.getCompetencyStartNodeId(competency2.getId());
            expectedEdges.add(new NgxLearningPathDTO.Edge(LearningPathNgxService.getEdgeFromToId(sourceNodeId, targetNodeId), sourceNodeId, targetNodeId));

            generatePathAndAssert(new NgxLearningPathDTO(expectedNodes, expectedEdges));
        }

        private void masterCompetencies(Competency... competencies) {
            for (var competency : competencies) {
                competencyProgressUtilService.createCompetencyProgress(competency, user, 100, 1);
            }
        }
    }

    @Nested
    class GenerateNgxPathRepresentationLearningObjectOrder {

        private Competency competency;

        private Lecture lecture;

        private LectureUnit[] lectureUnits;

        private Exercise[] exercises;

        private Set<NgxLearningPathDTO.Node> expectedNodes;

        private Set<NgxLearningPathDTO.Edge> expectedEdges;

        @BeforeEach
        void setup() {
            final var users = userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
            user = users.getFirst();
            course = CourseFactory.generateCourse(null, ZonedDateTime.now().minusDays(8), ZonedDateTime.now().minusDays(8), new HashSet<>(), TEST_PREFIX + "tumuser",
                    TEST_PREFIX + "tutor", TEST_PREFIX + "editor", TEST_PREFIX + "instructor");
            course = courseRepository.save(course);

            lecture = lectureUtilService.createLecture(course, ZonedDateTime.now());

            competency = competencyUtilService.createCompetency(course);
            competency.setMasteryThreshold(100);
            competency = competencyRepository.save(competency);
            expectedNodes = new HashSet<>(getExpectedNodesOfEmptyCompetency(competency));
            expectedEdges = new HashSet<>();
        }

        @Test
        void testCompetencyWithLectureUnitAndExercise() {
            competency.setMasteryThreshold(70);
            competency = competencyRepository.save(competency);

            generateLectureUnits(1);
            generateExercises(1);

            addNodes(lectureUnits);
            addNodes(exercises);
            addEdges(competency, lectureUnits[0], exercises[0]);
            generatePathAndAssert(new NgxLearningPathDTO(expectedNodes, expectedEdges));
        }

        @Test
        void testOrderByDifficultyLevel() {
            generateExercises(3);
            exercises[0].setDifficulty(DifficultyLevel.HARD);
            exercises[1].setDifficulty(DifficultyLevel.EASY);
            exercises[2].setDifficulty(DifficultyLevel.MEDIUM);
            exerciseRepository.saveAll(List.of(exercises));

            addNodes(exercises);
            addEdges(competency, exercises[1], exercises[2], exercises[0]);
            generatePathAndAssert(new NgxLearningPathDTO(expectedNodes, expectedEdges));
        }

        @Test
        void testAvoidReschedulingCompletedLearningObjects() {
            generateLectureUnits(2);
            lectureUnits[0] = lectureUtilService.completeLectureUnitForUser(lectureUnits[0], user);
            generateExercises(6);
            exercises[0].setDifficulty(DifficultyLevel.EASY);
            studentScoreUtilService.createStudentScore(exercises[0], user, 100);
            exercises[1].setDifficulty(DifficultyLevel.EASY);
            exercises[2].setDifficulty(DifficultyLevel.MEDIUM);
            studentScoreUtilService.createStudentScore(exercises[2], user, 100);
            exercises[3].setDifficulty(DifficultyLevel.MEDIUM);
            exercises[4].setDifficulty(DifficultyLevel.HARD);
            studentScoreUtilService.createStudentScore(exercises[4], user, 100);
            exercises[5].setDifficulty(DifficultyLevel.HARD);
            exerciseRepository.saveAll(List.of(exercises));

            addNodes(lectureUnits[1]);
            addNodes(exercises[1], exercises[3], exercises[5]);
            addEdges(competency, lectureUnits[1], exercises[1], exercises[3], exercises[5]);
            generatePathAndAssert(new NgxLearningPathDTO(expectedNodes, expectedEdges));
        }

        @Test
        void testRecommendCorrectAmountOfLearningObjects() {
            competency.setMasteryThreshold(40);
            competency = competencyRepository.save(competency);

            generateLectureUnits(1);
            generateExercises(9);
            exercises[0].setDifficulty(DifficultyLevel.EASY);
            exercises[1].setDifficulty(DifficultyLevel.MEDIUM);
            exercises[2].setDifficulty(DifficultyLevel.HARD);
            exerciseRepository.saveAll(List.of(exercises));

            LearningPath learningPath = learningPathUtilService.createLearningPathInCourseForUser(course, user);
            learningPath = learningPathService.findWithCompetenciesAndReleasedLearningObjectsAndCompletedUsersById(learningPath.getId());
            NgxLearningPathDTO actual = learningPathService.generateNgxPathRepresentation(learningPath);
            // competency start & end, lecture unit, and one exercise per difficulty level
            assertThat(actual.nodes()).hasSize(6);
        }

        @Test
        void testDoesNotLeakUnreleasedLearningObjects() {
            generateLectureUnits(3);
            generateExercises(3);

            lectureUnits[0].setReleaseDate(ZonedDateTime.now().plusDays(1));
            lectureUnits[1].setReleaseDate(ZonedDateTime.now().minusDays(1));
            lectureUnits[2].setReleaseDate(null);
            lectureUnitRepository.saveAll(List.of(lectureUnits));

            exercises[0].setReleaseDate(ZonedDateTime.now().plusDays(1));
            exercises[1].setReleaseDate(ZonedDateTime.now().minusDays(1));
            exercises[2].setReleaseDate(null);
            exerciseRepository.saveAll(List.of(exercises));

            LearningPath learningPath = learningPathUtilService.createLearningPathInCourseForUser(course, user);
            learningPath = learningPathService.findWithCompetenciesAndReleasedLearningObjectsAndCompletedUsersById(learningPath.getId());
            NgxLearningPathDTO actual = learningPathService.generateNgxPathRepresentation(learningPath);
            // competency start & end, lecture unit, and one exercise per difficulty level
            assertThat(actual.nodes()).hasSize(6);
        }

        private void generateLectureUnits(int numberOfLectureUnits) {
            lectureUnits = new LectureUnit[numberOfLectureUnits];
            for (int i = 0; i < lectureUnits.length; i++) {
                lectureUnits[i] = lectureUtilService.createTextUnit();
                lectureUtilService.addLectureUnitsToLecture(lecture, List.of(lectureUnits[i]));
                lectureUnits[i] = competencyUtilService.linkLectureUnitToCompetency(competency, lectureUnits[i]);
            }
        }

        private void generateExercises(int numberOfExercises) {
            exercises = new Exercise[numberOfExercises];
            for (int i = 0; i < exercises.length; i++) {
                exercises[i] = programmingExerciseUtilService.addProgrammingExerciseToCourse(course);
                exercises[i].setReleaseDate(null);
                exercises[i] = competencyUtilService.linkExerciseToCompetency(competency, exercises[i]);
            }
        }

        private void addNodes(LearningObject... learningObjects) {
            for (var learningObject : learningObjects) {
                if (learningObject instanceof LectureUnit lectureUnit) {
                    expectedNodes.add(getNodeForLectureUnit(competency, lectureUnit));
                }
                else if (learningObject instanceof Exercise exercise) {
                    expectedNodes.add(getNodeForExercise(competency, exercise));
                }
            }
        }

        private void addEdges(Competency competency, LearningObject... learningObjects) {
            addEdge(competency, learningObjects[0]);
            addEdges(competency.getId(), learningObjects);
            addEdge(learningObjects[learningObjects.length - 1], competency);
        }

        private void addEdges(long competencyId, LearningObject... learningObjects) {
            for (int i = 1; i < learningObjects.length; i++) {
                final var sourceId = LearningPathNgxService.getLearningObjectNodeId(competencyId, learningObjects[i - 1]);
                final var targetId = LearningPathNgxService.getLearningObjectNodeId(competencyId, learningObjects[i]);
                expectedEdges.add(new NgxLearningPathDTO.Edge(LearningPathNgxService.getEdgeFromToId(sourceId, targetId), sourceId, targetId));
            }
        }

        private void addEdge(Competency competency, LearningObject learningObject) {
            final var sourceId = LearningPathNgxService.getCompetencyStartNodeId(competency.getId());
            final var targetId = LearningPathNgxService.getLearningObjectNodeId(competency.getId(), learningObject);
            expectedEdges.add(new NgxLearningPathDTO.Edge(LearningPathNgxService.getEdgeFromToId(sourceId, targetId), sourceId, targetId));
        }

        private void addEdge(LearningObject learningObject, Competency competency) {
            final var sourceId = LearningPathNgxService.getLearningObjectNodeId(competency.getId(), learningObject);
            final var targetId = LearningPathNgxService.getCompetencyEndNodeId(competency.getId());
            expectedEdges.add(new NgxLearningPathDTO.Edge(LearningPathNgxService.getEdgeFromToId(sourceId, targetId), sourceId, targetId));
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
        learningPath = learningPathService.findWithCompetenciesAndReleasedLearningObjectsAndCompletedUsersById(learningPath.getId());
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
                NgxLearningPathDTO.Node.of(LearningPathNgxService.getCompetencyStartNodeId(competency.getId()), NgxLearningPathDTO.NodeType.COMPETENCY_START, competency.getId(),
                        ""),
                NgxLearningPathDTO.Node.of(LearningPathNgxService.getCompetencyEndNodeId(competency.getId()), NgxLearningPathDTO.NodeType.COMPETENCY_END, competency.getId(), "")));
    }

    private static NgxLearningPathDTO.Node getNodeForLectureUnit(Competency competency, LectureUnit lectureUnit) {
        return NgxLearningPathDTO.Node.of(LearningPathNgxService.getLectureUnitNodeId(competency.getId(), lectureUnit.getId()), NgxLearningPathDTO.NodeType.LECTURE_UNIT,
                lectureUnit.getId(), lectureUnit.getLecture().getId(), false, lectureUnit.getName());
    }

    private static NgxLearningPathDTO.Node getNodeForExercise(Competency competency, Exercise exercise) {
        return NgxLearningPathDTO.Node.of(LearningPathNgxService.getExerciseNodeId(competency.getId(), exercise.getId()), NgxLearningPathDTO.NodeType.EXERCISE, exercise.getId(),
                exercise.getTitle());
    }

    private void addExpectedComponentsForEmptyCompetencies(Set<NgxLearningPathDTO.Node> expectedNodes, Set<NgxLearningPathDTO.Edge> expectedEdges, Competency... competencies) {
        for (var competency : competencies) {
            expectedNodes.addAll(getExpectedNodesOfEmptyCompetency(competency));
            expectedEdges.add(new NgxLearningPathDTO.Edge(LearningPathNgxService.getDirectEdgeId(competency.getId()),
                    LearningPathNgxService.getCompetencyStartNodeId(competency.getId()), LearningPathNgxService.getCompetencyEndNodeId(competency.getId())));
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
