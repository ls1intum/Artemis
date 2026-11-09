package de.tum.cit.aet.artemis.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.cit.aet.artemis.assessment.domain.PresentationAssessment;
import de.tum.cit.aet.artemis.assessment.domain.PresentationAssessmentInstance;
import de.tum.cit.aet.artemis.assessment.domain.PresentationAssessmentMode;
import de.tum.cit.aet.artemis.assessment.domain.PresentationGradingMethod;
import de.tum.cit.aet.artemis.assessment.domain.PresentationRubricScale;
import de.tum.cit.aet.artemis.assessment.dto.PresentationAssessmentDTO;
import de.tum.cit.aet.artemis.assessment.dto.PresentationAssessmentInstanceDTO;
import de.tum.cit.aet.artemis.assessment.dto.PresentationAssessmentInstanceRequestDTO;
import de.tum.cit.aet.artemis.assessment.dto.PresentationAssessmentInstancesBatchCreateDTO;
import de.tum.cit.aet.artemis.assessment.dto.PresentationAssessmentStatisticsDTO;
import de.tum.cit.aet.artemis.assessment.dto.PresentationAssessmentStudentRowDTO;
import de.tum.cit.aet.artemis.assessment.dto.PresentationRubricCriterionDTO;
import de.tum.cit.aet.artemis.assessment.dto.PresentationRubricDTO;
import de.tum.cit.aet.artemis.assessment.dto.PresentationRubricLevelDTO;
import de.tum.cit.aet.artemis.assessment.dto.PresentationRubricRatingDTO;
import de.tum.cit.aet.artemis.assessment.repository.PresentationAssessmentInstanceRepository;
import de.tum.cit.aet.artemis.assessment.repository.PresentationAssessmentRepository;
import de.tum.cit.aet.artemis.assessment.repository.PresentationRubricCriterionRepository;
import de.tum.cit.aet.artemis.assessment.repository.PresentationRubricLevelRepository;
import de.tum.cit.aet.artemis.assessment.repository.PresentationRubricRatingRepository;
import de.tum.cit.aet.artemis.assessment.repository.PresentationRubricWriteRepository.PresentationRubricChanges;
import de.tum.cit.aet.artemis.assessment.service.PresentationAssessmentService;
import de.tum.cit.aet.artemis.assessment.service.PresentationRubricService;
import de.tum.cit.aet.artemis.assessment.service.PresentationRubricService.RubricGrade;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Tests the grading methods of presentation assessments: pass/fail, score and rubric, from creating a presentation to grading its instances.
 */
class PresentationGradingMethodIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "presentationgrading";

    private static final String BASE_URL = "/api/assessment/courses/";

    private static final ZonedDateTime FIXED_DATE = ZonedDateTime.parse("2026-07-31T13:26:00+02:00");

    private static final int STUDENT_COUNT = 6;

    @Autowired
    private PresentationAssessmentRepository presentationAssessmentRepository;

    @Autowired
    private PresentationAssessmentInstanceRepository presentationAssessmentInstanceRepository;

    @Autowired
    private PresentationAssessmentService presentationAssessmentService;

    @Autowired
    private PresentationRubricService presentationRubricService;

    @Autowired
    private PresentationRubricCriterionRepository presentationRubricCriterionRepository;

    @Autowired
    private PresentationRubricLevelRepository presentationRubricLevelRepository;

    @Autowired
    private PresentationRubricRatingRepository presentationRubricRatingRepository;

    @Autowired
    private CourseTestRepository courseRepository;

    @Autowired
    private FeatureToggleService featureToggleService;

    private Course course;

    private Course otherCourse;

    @BeforeEach
    void initTestCase() {
        featureToggleService.enableFeature(Feature.PresentationAssessments);
        userUtilService.addUsers(TEST_PREFIX, STUDENT_COUNT, 1, 1, 1);
        course = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        otherCourse = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        courseUtilService.setPresentationAssessmentsEnabled(course, true);
        courseUtilService.setPresentationAssessmentsEnabled(otherCourse, true);
    }

    @AfterEach
    void resetPresentationAssessmentFeature() {
        featureToggleService.disableFeature(Feature.PresentationAssessments);
    }

    @Nested
    class CreatePresentation {

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
        void passFail_shouldStoreTheMethodWithoutMaxPoints() throws Exception {
            PresentationAssessmentDTO created = createPresentation(PresentationGradingMethod.PASS_FAIL, null, null);

            assertThat(created.gradingMethod()).isEqualTo(PresentationGradingMethod.PASS_FAIL);
            assertThat(created.maxPoints()).isNull();
            var stored = presentationAssessmentRepository.findByIdElseThrow(created.id());
            assertThat(stored.getGradingMethod()).isEqualTo(PresentationGradingMethod.PASS_FAIL);
            assertThat(stored.getMaxPoints()).isNull();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
        void score_withoutMaxPoints_shouldBeRejected() throws Exception {
            expectError(MockMvcRequestBuilders.post(baseUrl(course)), presentation(PresentationGradingMethod.SCORE, null, null), "maxPointsRequired");
        }

        @ParameterizedTest
        @EnumSource(value = PresentationGradingMethod.class, names = { "PASS_FAIL", "RUBRIC" })
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
        void otherMethod_withMaxPoints_shouldBeRejected(PresentationGradingMethod gradingMethod) throws Exception {
            List<PresentationRubricCriterionDTO> criteria = gradingMethod == PresentationGradingMethod.RUBRIC ? standardRubric() : null;
            expectError(MockMvcRequestBuilders.post(baseUrl(course)), presentation(gradingMethod, 20.0, criteria), "maxPointsNotAllowed");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
        void rubric_shouldStoreCriteriaAndLevelsInOrder() throws Exception {
            PresentationAssessmentDTO created = createPresentation(PresentationGradingMethod.RUBRIC, null, standardRubric());

            PresentationRubricDTO rubric = getRubric(created.id());

            assertThat(rubric.instanceCount()).isZero();
            assertThat(rubric.assessedInstanceCount()).isZero();
            assertThat(rubric.criteria()).extracting(PresentationRubricCriterionDTO::name).containsExactly("Content", "Delivery", "Slides");
            assertThat(rubric.criteria()).extracting(PresentationRubricCriterionDTO::weight).containsExactly(50, 30, 20);
            assertThat(rubric.criteria()).extracting(PresentationRubricCriterionDTO::scale).containsExactly(PresentationRubricScale.FIVE_POINT, PresentationRubricScale.THREE_POINT,
                    PresentationRubricScale.FIVE_POINT);
            assertThat(rubric.criteria().get(1).levels()).extracting(PresentationRubricLevelDTO::description).containsExactly("Delivery level 1", "Delivery level 2",
                    "Delivery level 3");
            assertThat(rubric.criteria()).flatExtracting(PresentationRubricCriterionDTO::levels).extracting(PresentationRubricLevelDTO::id).doesNotContainNull().hasSize(13);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
        void rubric_withoutCriteria_shouldBeRejected() throws Exception {
            expectError(MockMvcRequestBuilders.post(baseUrl(course)), presentation(PresentationGradingMethod.RUBRIC, null, null), "rubricRequired");
        }

        @ParameterizedTest
        @EnumSource(value = PresentationGradingMethod.class, names = { "PASS_FAIL", "SCORE" })
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
        void otherMethod_withCriteria_shouldBeRejected(PresentationGradingMethod gradingMethod) throws Exception {
            Double maxPoints = gradingMethod == PresentationGradingMethod.SCORE ? 20.0 : null;
            expectError(MockMvcRequestBuilders.post(baseUrl(course)), presentation(gradingMethod, maxPoints, standardRubric()), "rubricNotAllowed");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
        void rubric_withWeightsNotAddingUpTo100_shouldBeRejected() throws Exception {
            var criteria = List.of(criterion("Content", 50, PresentationRubricScale.FIVE_POINT), criterion("Delivery", 40, PresentationRubricScale.THREE_POINT));
            expectError(MockMvcRequestBuilders.post(baseUrl(course)), presentation(PresentationGradingMethod.RUBRIC, null, criteria), "rubricWeightSumInvalid");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
        void rubric_withLevelsNotMatchingTheScale_shouldBeRejected() throws Exception {
            // Five levels, which are within the allowed size but do not match a three-point scale
            var fivePoint = criterion("Content", 100, PresentationRubricScale.FIVE_POINT);
            var mismatch = new PresentationRubricCriterionDTO(null, "Content", null, 100, PresentationRubricScale.THREE_POINT, fivePoint.levels());
            expectError(MockMvcRequestBuilders.post(baseUrl(course)), presentation(PresentationGradingMethod.RUBRIC, null, List.of(mismatch)), "levelsDoNotMatchScale");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
        void rubric_withMoreThan20Criteria_shouldBeRejected() throws Exception {
            // The 21 criteria still add up to 100 percent, so only the number of criteria is wrong
            List<PresentationRubricCriterionDTO> criteria = IntStream.range(0, 21)
                    .mapToObj(index -> criterion("Criterion " + index, index < 16 ? 5 : 4, PresentationRubricScale.THREE_POINT)).toList();
            expectFieldError(MockMvcRequestBuilders.post(baseUrl(course)), presentation(PresentationGradingMethod.RUBRIC, null, criteria), "rubricCriteria");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
        void rubric_withWeightBelowOne_shouldBeRejected() throws Exception {
            // The weights still add up to 100 percent, so only the weight of zero is wrong
            var criteria = List.of(criterion("Content", 0, PresentationRubricScale.FIVE_POINT), criterion("Delivery", 100, PresentationRubricScale.FIVE_POINT));
            expectFieldError(MockMvcRequestBuilders.post(baseUrl(course)), presentation(PresentationGradingMethod.RUBRIC, null, criteria), "rubricCriteria[0].weight");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
        void rubric_withBlankCriterionName_shouldBeRejected() throws Exception {
            var criteria = List.of(criterion(" ", 100, PresentationRubricScale.FIVE_POINT));
            request.postWithResponseBody(baseUrl(course), presentation(PresentationGradingMethod.RUBRIC, null, criteria), PresentationAssessmentDTO.class, HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    class UpdateRubric {

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
        void changingTheGradingMethod_shouldBeRejected() throws Exception {
            PresentationAssessmentDTO created = createPresentation(PresentationGradingMethod.SCORE, 20.0, null);
            var update = new PresentationAssessmentDTO(created.id(), created.title(), created.description(), PresentationGradingMethod.PASS_FAIL, null, null, null, null);

            expectError(MockMvcRequestBuilders.put(assessmentUrl(course, created.id())), update, "gradingMethodImmutable");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
        void withoutInstances_shouldReplaceTheWholeRubric() throws Exception {
            PresentationAssessmentDTO created = createPresentation(PresentationGradingMethod.RUBRIC, null, standardRubric());
            var replacement = List.of(criterion("Structure", 60, PresentationRubricScale.THREE_POINT), criterion("Delivery", 40, PresentationRubricScale.FIVE_POINT));

            updatePresentation(rubricUpdate(created, replacement), HttpStatus.OK);

            PresentationRubricDTO rubric = getRubric(created.id());
            assertThat(rubric.criteria()).extracting(PresentationRubricCriterionDTO::name).containsExactly("Structure", "Delivery");
            assertThat(rubric.criteria()).extracting(PresentationRubricCriterionDTO::scale).containsExactly(PresentationRubricScale.THREE_POINT,
                    PresentationRubricScale.FIVE_POINT);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
        void withoutCriteriaInTheRequest_shouldKeepTheRubric() throws Exception {
            PresentationAssessmentDTO created = createPresentation(PresentationGradingMethod.RUBRIC, null, standardRubric());
            PresentationRubricDTO before = getRubric(created.id());
            var update = new PresentationAssessmentDTO(created.id(), "Renamed presentation", created.description(), PresentationGradingMethod.RUBRIC, null, null, null, null);

            updatePresentation(update, HttpStatus.OK);

            assertThat(getRubric(created.id()).criteria()).isEqualTo(before.criteria());
            assertThat(presentationAssessmentRepository.findByIdElseThrow(created.id()).getTitle()).isEqualTo("Renamed presentation");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
        void withInstances_shouldStillChangeTexts() throws Exception {
            PresentationAssessmentDTO created = createPresentation(PresentationGradingMethod.RUBRIC, null, standardRubric());
            createInstances(created.id(), List.of(student(1)), null, null, null);
            PresentationRubricDTO rubric = getRubric(created.id());
            List<PresentationRubricCriterionDTO> renamed = rubric.criteria().stream()
                    .map(criterion -> new PresentationRubricCriterionDTO(criterion.id(), criterion.name() + " (new)", "New description", criterion.weight(), criterion.scale(),
                            criterion.levels().stream().map(level -> new PresentationRubricLevelDTO(level.id(), "New level text")).toList()))
                    .toList();

            updatePresentation(rubricUpdate(created, renamed), HttpStatus.OK);

            PresentationRubricDTO updated = getRubric(created.id());
            assertThat(updated.criteria()).extracting(PresentationRubricCriterionDTO::name).containsExactly("Content (new)", "Delivery (new)", "Slides (new)");
            assertThat(updated.criteria()).extracting(PresentationRubricCriterionDTO::id)
                    .containsExactlyElementsOf(rubric.criteria().stream().map(PresentationRubricCriterionDTO::id).toList());
            assertThat(updated.criteria()).flatExtracting(PresentationRubricCriterionDTO::levels).extracting(PresentationRubricLevelDTO::description)
                    .containsOnly("New level text");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
        void withInstances_addingACriterion_shouldBeRejected() throws Exception {
            PresentationAssessmentDTO created = createLockedRubricPresentation();
            List<PresentationRubricCriterionDTO> criteria = new ArrayList<>(withWeights(getRubric(created.id()), 40, 30, 20));
            criteria.add(criterion("Questions", 10, PresentationRubricScale.THREE_POINT));

            expectError(MockMvcRequestBuilders.put(assessmentUrl(course, created.id())), rubricUpdate(created, criteria), "rubricStructureLocked");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
        void withInstances_removingACriterion_shouldBeRejected() throws Exception {
            PresentationAssessmentDTO created = createLockedRubricPresentation();
            List<PresentationRubricCriterionDTO> criteria = withWeights(getRubric(created.id()), 70, 30, 0).subList(0, 2);

            expectError(MockMvcRequestBuilders.put(assessmentUrl(course, created.id())), rubricUpdate(created, criteria), "rubricStructureLocked");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
        void withInstances_changingAScale_shouldBeRejected() throws Exception {
            PresentationAssessmentDTO created = createLockedRubricPresentation();
            PresentationRubricDTO rubric = getRubric(created.id());
            PresentationRubricCriterionDTO delivery = rubric.criteria().get(1);
            List<PresentationRubricCriterionDTO> criteria = new ArrayList<>(rubric.criteria());
            // Five new levels match the new scale, so only the fixed structure rejects the change
            criteria.set(1, new PresentationRubricCriterionDTO(delivery.id(), delivery.name(), delivery.description(), delivery.weight(), PresentationRubricScale.FIVE_POINT,
                    criterion(delivery.name(), delivery.weight(), PresentationRubricScale.FIVE_POINT).levels()));

            expectError(MockMvcRequestBuilders.put(assessmentUrl(course, created.id())), rubricUpdate(created, criteria), "rubricStructureLocked");
            assertThat(getRubric(created.id()).criteria()).isEqualTo(rubric.criteria());
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
        void withInstances_reorderingCriteria_shouldBeRejected() throws Exception {
            PresentationAssessmentDTO created = createLockedRubricPresentation();
            List<PresentationRubricCriterionDTO> criteria = new ArrayList<>(getRubric(created.id()).criteria());
            criteria.add(criteria.removeFirst());

            expectError(MockMvcRequestBuilders.put(assessmentUrl(course, created.id())), rubricUpdate(created, criteria), "rubricStructureLocked");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
        void withInstances_levelOfAnotherCriterion_shouldBeRejected() throws Exception {
            PresentationAssessmentDTO created = createLockedRubricPresentation();
            PresentationRubricDTO rubric = getRubric(created.id());
            PresentationRubricCriterionDTO content = rubric.criteria().getFirst();
            List<PresentationRubricLevelDTO> levels = new ArrayList<>(content.levels());
            levels.set(0, rubric.criteria().get(2).levels().getFirst());
            List<PresentationRubricCriterionDTO> criteria = new ArrayList<>(rubric.criteria());
            criteria.set(0, new PresentationRubricCriterionDTO(content.id(), content.name(), content.description(), content.weight(), content.scale(), levels));

            expectError(MockMvcRequestBuilders.put(assessmentUrl(course, created.id())), rubricUpdate(created, criteria), "rubricStructureLocked");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
        void changingWeights_shouldRecalculateTheAssessedScores() throws Exception {
            PresentationAssessmentDTO created = createPresentation(PresentationGradingMethod.RUBRIC, null, standardRubric());
            PresentationRubricDTO rubric = getRubric(created.id());
            long good = createInstances(created.id(), List.of(student(1)), null, null, ratings(rubric, 3, 2, 1)).getFirst().id();
            long mixed = createInstances(created.id(), List.of(student(2)), null, null, ratings(rubric, 4, 0, 0)).getFirst().id();
            long unassessed = createInstances(created.id(), List.of(student(3)), null, null, null).getFirst().id();
            assertThat(rubricScore(good)).isEqualTo(72.5);
            assertThat(rubricScore(mixed)).isEqualTo(50.0);

            updatePresentation(rubricUpdate(created, withWeights(rubric, 40, 40, 20)), HttpStatus.OK);

            assertThat(rubricScore(good)).isEqualTo(75.0);
            assertThat(rubricScore(mixed)).isEqualTo(40.0);
            assertThat(rubricScore(unassessed)).isNull();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
        void changingWeights_shouldNotQueryMoreForMoreInstancesWithTheSameScore() throws Exception {
            long queriesForTwoInstances = countQueriesOfWeightChange(2);
            long queriesForSixInstances = countQueriesOfWeightChange(6);

            // One statement per distinct score rather than per instance
            assertThat(queriesForSixInstances).isEqualTo(queriesForTwoInstances);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
        void changingWeights_withoutAssessedInstances_shouldLeaveTheInstancesUnassessed() throws Exception {
            PresentationAssessmentDTO created = createPresentation(PresentationGradingMethod.RUBRIC, null, standardRubric());
            long unassessed = createInstances(created.id(), List.of(student(1)), null, null, null).getFirst().id();

            updatePresentation(rubricUpdate(created, withWeights(getRubric(created.id()), 40, 40, 20)), HttpStatus.OK);

            assertThat(getRubric(created.id()).criteria()).extracting(PresentationRubricCriterionDTO::weight).containsExactly(40, 40, 20);
            assertThat(rubricScore(unassessed)).isNull();
        }

        private long countQueriesOfWeightChange(int assessedInstanceCount) throws Exception {
            PresentationAssessmentDTO created = createPresentation(PresentationGradingMethod.RUBRIC, null, standardRubric());
            PresentationRubricDTO rubric = getRubric(created.id());
            List<String> logins = IntStream.rangeClosed(1, assessedInstanceCount).mapToObj(PresentationGradingMethodIntegrationTest::student).toList();
            List<PresentationAssessmentInstanceDTO> instances = createInstances(created.id(), logins, null, null, ratings(rubric, 3, 2, 1));
            PresentationAssessmentDTO update = rubricUpdate(created, withWeights(rubric, 40, 40, 20));

            queryInterceptor.startQueryCount();
            updatePresentation(update, HttpStatus.OK);
            long queryCount = queryInterceptor.getQueryCount();

            assertThat(instances).extracting(instance -> rubricScore(instance.id())).containsOnly(75.0);
            return queryCount;
        }
    }

    @Nested
    class GetRubric {

        @Test
        @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
        void asTutor_shouldCountInstancesAndAssessedInstances() throws Exception {
            long presentationId = createRubricPresentationAsInstructor();
            PresentationRubricDTO rubric = getRubric(presentationId);
            createInstances(presentationId, List.of(student(1)), null, null, null);
            createInstances(presentationId, List.of(student(2)), null, null, ratings(rubric, 3, 2, 1));

            PresentationRubricDTO counted = getRubric(presentationId);

            assertThat(counted.instanceCount()).isEqualTo(2);
            assertThat(counted.assessedInstanceCount()).isEqualTo(1);
        }

        @ParameterizedTest
        @EnumSource(value = PresentationGradingMethod.class, names = { "PASS_FAIL", "SCORE" })
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
        void ofAnotherMethod_shouldBeRejected(PresentationGradingMethod gradingMethod) throws Exception {
            Double maxPoints = gradingMethod == PresentationGradingMethod.SCORE ? 20.0 : null;
            PresentationAssessmentDTO created = createPresentation(gradingMethod, maxPoints, null);

            request.performMvcRequest(MockMvcRequestBuilders.get(assessmentUrl(course, created.id()) + "/rubric")).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorKey").value("rubricNotAllowed"));
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void asStudent_shouldBeForbidden() throws Exception {
            long presentationId = createRubricPresentationAsInstructor();

            request.get(assessmentUrl(course, presentationId) + "/rubric", HttpStatus.FORBIDDEN, PresentationRubricDTO.class);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
        void withCourseSettingDisabled_shouldBeForbidden() throws Exception {
            long presentationId = createRubricPresentationAsInstructor();
            courseUtilService.setPresentationAssessmentsEnabled(course, false);

            request.get(assessmentUrl(course, presentationId) + "/rubric", HttpStatus.FORBIDDEN, PresentationRubricDTO.class);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
        void viaTheUrlOfAnotherCourse_shouldNotBeFound() throws Exception {
            PresentationAssessmentDTO created = createPresentation(PresentationGradingMethod.RUBRIC, null, standardRubric());

            request.get(assessmentUrl(otherCourse, created.id()) + "/rubric", HttpStatus.NOT_FOUND, PresentationRubricDTO.class);
        }
    }

    @Nested
    class GradePassFail {

        @Test
        @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
        void batch_shouldGiveEveryPresenterTheResult() throws Exception {
            long presentationId = createPresentationAsInstructor(PresentationGradingMethod.PASS_FAIL, null);

            List<PresentationAssessmentInstanceDTO> instances = createInstances(presentationId, List.of(student(1), student(2)), null, false, null);

            assertThat(instances).hasSize(2).extracting(PresentationAssessmentInstanceDTO::passed).containsOnly(false);
            assertThat(instances).extracting(instance -> presentationAssessmentInstanceRepository.findByIdElseThrow(instance.id()).getPassed()).containsOnly(false);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
        void withoutResult_shouldBeGradedLater() throws Exception {
            long presentationId = createPresentationAsInstructor(PresentationGradingMethod.PASS_FAIL, null);
            PresentationAssessmentInstanceDTO instance = createInstances(presentationId, List.of(student(1)), null, null, null).getFirst();
            assertThat(instance.passed()).isNull();

            PresentationAssessmentInstanceDTO graded = updateInstance(presentationId, instanceUpdate(instance, student(1), null, true, null), HttpStatus.OK);

            assertThat(graded.passed()).isTrue();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
        void changingTheResult_shouldBeAllowed() throws Exception {
            long presentationId = createPresentationAsInstructor(PresentationGradingMethod.PASS_FAIL, null);
            PresentationAssessmentInstanceDTO instance = createInstances(presentationId, List.of(student(1)), null, true, null).getFirst();

            updateInstance(presentationId, instanceUpdate(instance, student(1), null, false, null), HttpStatus.OK);

            assertThat(presentationAssessmentInstanceRepository.findByIdElseThrow(instance.id()).getPassed()).isFalse();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
        void removingTheResult_shouldBeRejected() throws Exception {
            long presentationId = createPresentationAsInstructor(PresentationGradingMethod.PASS_FAIL, null);
            PresentationAssessmentInstanceDTO instance = createInstances(presentationId, List.of(student(1)), null, true, null).getFirst();

            expectError(MockMvcRequestBuilders.put(instanceUrl(presentationId, instance.id())), instanceUpdate(instance, student(1), null, null, null), "resultCannotBeRemoved");
        }

        @ParameterizedTest
        @ValueSource(strings = { "points", "ratings" })
        @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
        void resultOfAnotherMethod_shouldBeRejected(String otherResult) throws Exception {
            long presentationId = createPresentationAsInstructor(PresentationGradingMethod.PASS_FAIL, null);
            var dto = batch(List.of(student(1)), otherResult.equals("points") ? 5.0 : null, true, otherResult.equals("ratings") ? unresolvedRatings() : null);

            expectError(MockMvcRequestBuilders.post(instancesUrl(presentationId)), dto, "resultDoesNotMatchGradingMethod");
        }
    }

    @Nested
    class GradeScore {

        @ParameterizedTest
        @ValueSource(strings = { "passed", "ratings" })
        @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
        void resultOfAnotherMethod_shouldBeRejected(String otherResult) throws Exception {
            long presentationId = createPresentationAsInstructor(PresentationGradingMethod.SCORE, 20.0);
            var dto = batch(List.of(student(1)), 5.0, otherResult.equals("passed") ? true : null, otherResult.equals("ratings") ? unresolvedRatings() : null);

            expectError(MockMvcRequestBuilders.post(instancesUrl(presentationId)), dto, "resultDoesNotMatchGradingMethod");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
        void removingThePoints_shouldBeRejected() throws Exception {
            long presentationId = createPresentationAsInstructor(PresentationGradingMethod.SCORE, 20.0);
            PresentationAssessmentInstanceDTO instance = createInstances(presentationId, List.of(student(1)), 12.0, null, null).getFirst();

            expectError(MockMvcRequestBuilders.put(instanceUrl(presentationId, instance.id())), instanceUpdate(instance, student(1), null, null, null), "resultCannotBeRemoved");
        }
    }

    @Nested
    class GradeRubric {

        @Test
        @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
        void ratings_shouldBeStoredAndScored() throws Exception {
            long presentationId = createRubricPresentationAsInstructor();
            PresentationRubricDTO rubric = getRubric(presentationId);
            List<PresentationRubricRatingDTO> ratings = withComments(ratings(rubric, 3, 2, 1), "Clear structure", null, "Too much text");

            PresentationAssessmentInstanceDTO instance = createInstances(presentationId, List.of(student(1)), null, null, ratings).getFirst();

            // score = Σ weight · position / (level count − 1), with position 0 for the lowest level
            // = 50 % · 3/4 + 30 % · 2/2 + 20 % · 1/4 = 72.5 %
            assertThat(instance.rubricScore()).isEqualTo(72.5);
            assertThat(getRatings(presentationId, instance.id())).containsExactlyElementsOf(ratings);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
        void batch_shouldGiveEveryPresenterTheSameRatings() throws Exception {
            long presentationId = createRubricPresentationAsInstructor();
            List<PresentationRubricRatingDTO> ratings = ratings(getRubric(presentationId), 4, 0, 0);

            List<PresentationAssessmentInstanceDTO> instances = createInstances(presentationId, List.of(student(1), student(2)), null, null, ratings);

            assertThat(instances).hasSize(2).extracting(PresentationAssessmentInstanceDTO::rubricScore).containsOnly(50.0);
            for (PresentationAssessmentInstanceDTO instance : instances) {
                assertThat(getRatings(presentationId, instance.id())).containsExactlyElementsOf(ratings);
            }
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
        void withoutRatings_shouldStayUnassessed() throws Exception {
            long presentationId = createRubricPresentationAsInstructor();

            PresentationAssessmentInstanceDTO instance = createInstances(presentationId, List.of(student(1)), null, null, null).getFirst();

            assertThat(instance.rubricScore()).isNull();
            assertThat(getRatings(presentationId, instance.id())).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = { "missing", "duplicate", "foreign" })
        @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
        void incompleteRatings_shouldBeRejected(String problem) throws Exception {
            long presentationId = createRubricPresentationAsInstructor();
            List<PresentationRubricRatingDTO> ratings = new ArrayList<>(ratings(getRubric(presentationId), 3, 2, 1));
            switch (problem) {
                case "missing" -> ratings.removeLast();
                case "duplicate" -> ratings.set(2, ratings.getFirst());
                default -> ratings.set(2, ratings(getRubric(createRubricPresentationAsInstructor()), 0, 0, 0).getLast());
            }

            expectError(MockMvcRequestBuilders.post(instancesUrl(presentationId)), batch(List.of(student(1)), null, null, ratings), "rubricRatingsIncomplete");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
        void levelOfAnotherCriterion_shouldBeRejected() throws Exception {
            long presentationId = createRubricPresentationAsInstructor();
            PresentationRubricDTO rubric = getRubric(presentationId);
            List<PresentationRubricRatingDTO> ratings = new ArrayList<>(ratings(rubric, 3, 2, 1));
            ratings.set(0, new PresentationRubricRatingDTO(rubric.criteria().getFirst().id(), rubric.criteria().getLast().levels().getFirst().id(), null));

            expectError(MockMvcRequestBuilders.post(instancesUrl(presentationId)), batch(List.of(student(1)), null, null, ratings), "rubricLevelInvalid");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
        void changingTheRatings_shouldReplaceThemAndTheScore() throws Exception {
            long presentationId = createRubricPresentationAsInstructor();
            PresentationRubricDTO rubric = getRubric(presentationId);
            PresentationAssessmentInstanceDTO instance = createInstances(presentationId, List.of(student(1)), null, null, ratings(rubric, 3, 2, 1)).getFirst();
            List<PresentationRubricRatingDTO> changed = withComments(ratings(rubric, 4, 0, 0), null, "Hard to follow", null);

            PresentationAssessmentInstanceDTO updated = updateInstance(presentationId, instanceUpdate(instance, student(1), null, null, changed), HttpStatus.OK);

            assertThat(updated.rubricScore()).isEqualTo(50.0);
            assertThat(getRatings(presentationId, instance.id())).containsExactlyElementsOf(changed);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
        void removingTheRatings_shouldBeRejected() throws Exception {
            long presentationId = createRubricPresentationAsInstructor();
            PresentationAssessmentInstanceDTO instance = createInstances(presentationId, List.of(student(1)), null, null, ratings(getRubric(presentationId), 3, 2, 1)).getFirst();

            expectError(MockMvcRequestBuilders.put(instanceUrl(presentationId, instance.id())), instanceUpdate(instance, student(1), null, null, null), "resultCannotBeRemoved");
        }

        @ParameterizedTest
        @ValueSource(strings = { "points", "passed" })
        @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
        void resultOfAnotherMethod_shouldBeRejected(String otherResult) throws Exception {
            long presentationId = createRubricPresentationAsInstructor();
            var dto = batch(List.of(student(1)), otherResult.equals("points") ? 5.0 : null, otherResult.equals("passed") ? true : null,
                    ratings(getRubric(presentationId), 3, 2, 1));

            expectError(MockMvcRequestBuilders.post(instancesUrl(presentationId)), dto, "resultDoesNotMatchGradingMethod");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
        void score_shouldBeRoundedAsTheCourseRoundsScores() throws Exception {
            course.setAccuracyOfScores(0);
            courseRepository.save(course);
            var criteria = List.of(criterion("Content", 33, PresentationRubricScale.FIVE_POINT), criterion("Delivery", 67, PresentationRubricScale.FIVE_POINT));
            long presentationId = presentationAssessmentService.create(course.getId(), presentation(PresentationGradingMethod.RUBRIC, null, criteria)).id();

            PresentationAssessmentInstanceDTO instance = createInstances(presentationId, List.of(student(1)), null, null, ratings(getRubric(presentationId), 3, 0)).getFirst();

            // 33 % · 3/4 = 24.75, rounded to whole percent
            assertThat(instance.rubricScore()).isEqualTo(25.0);
        }

        @ParameterizedTest
        @ValueSource(strings = { "withoutLevel", "longComment" })
        @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
        void invalidRating_shouldBeRejected(String problem) throws Exception {
            long presentationId = createRubricPresentationAsInstructor();
            List<PresentationRubricRatingDTO> ratings = new ArrayList<>(ratings(getRubric(presentationId), 3, 2, 1));
            PresentationRubricRatingDTO first = ratings.getFirst();
            ratings.set(0, problem.equals("withoutLevel") ? new PresentationRubricRatingDTO(first.criterionId(), null, null)
                    : new PresentationRubricRatingDTO(first.criterionId(), first.levelId(), "a".repeat(1001)));

            request.postListWithResponseBody(instancesUrl(presentationId), batch(List.of(student(1)), null, null, ratings), PresentationAssessmentInstanceDTO.class,
                    HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
        void ratingsOfAnInstanceOfAnotherPresentation_shouldNotBeFound() throws Exception {
            long presentationId = createRubricPresentationAsInstructor();
            long otherPresentationId = createRubricPresentationAsInstructor();
            PresentationAssessmentInstanceDTO instance = createInstances(otherPresentationId, List.of(student(1)), null, null, null).getFirst();

            request.getList(instanceUrl(presentationId, instance.id()) + "/ratings", HttpStatus.NOT_FOUND, PresentationRubricRatingDTO.class);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void ratings_asStudent_shouldBeForbidden() throws Exception {
            long presentationId = createRubricPresentationAsInstructor();
            long instanceId = presentationAssessmentService.saveInstances(course.getId(), presentationId, batch(List.of(student(1)), null, null, null)).getFirst().getId();

            request.getList(instanceUrl(presentationId, instanceId) + "/ratings", HttpStatus.FORBIDDEN, PresentationRubricRatingDTO.class);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
        void ratings_withCourseSettingDisabled_shouldBeForbidden() throws Exception {
            long presentationId = createRubricPresentationAsInstructor();
            long instanceId = presentationAssessmentService.saveInstances(course.getId(), presentationId, batch(List.of(student(1)), null, null, null)).getFirst().getId();
            courseUtilService.setPresentationAssessmentsEnabled(course, false);

            request.getList(instanceUrl(presentationId, instanceId) + "/ratings", HttpStatus.FORBIDDEN, PresentationRubricRatingDTO.class);
        }
    }

    @Nested
    class StatisticsAndRows {

        @Test
        @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
        void statistics_shouldCountTheResultsOfEveryMethod() throws Exception {
            createAssessedInstanceOfEveryMethod();

            PresentationAssessmentStatisticsDTO statistics = request.get(baseUrl(course) + "/statistics", HttpStatus.OK, PresentationAssessmentStatisticsDTO.class);

            assertThat(statistics.totalCount()).isEqualTo(4);
            assertThat(statistics.assessedCount()).isEqualTo(3);
        }

        @ParameterizedTest
        @ValueSource(booleans = { true, false })
        @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
        void rows_shouldBeFilteredByTheResultOfEveryMethod(boolean assessed) throws Exception {
            createAssessedInstanceOfEveryMethod();
            var params = new LinkedMultiValueMap<String, String>();
            params.add("assessed", String.valueOf(assessed));

            List<PresentationAssessmentStudentRowDTO> rows = request.getList(baseUrl(course) + "/student-rows", HttpStatus.OK, PresentationAssessmentStudentRowDTO.class, params);

            if (assessed) {
                assertThat(rows).extracting(row -> row.presentationAssessment().gradingMethod()).containsExactlyInAnyOrder(PresentationGradingMethod.PASS_FAIL,
                        PresentationGradingMethod.SCORE, PresentationGradingMethod.RUBRIC);
                assertThat(rows).extracting(row -> row.instance().passed()).containsExactlyInAnyOrder(false, null, null);
                assertThat(rows).extracting(row -> row.instance().rubricScore()).containsExactlyInAnyOrder(null, null, 72.5);
            }
            else {
                assertThat(rows).singleElement().satisfies(row -> assertThat(row.presentationAssessment().gradingMethod()).isEqualTo(PresentationGradingMethod.PASS_FAIL));
            }
        }

        /**
         * One assessed instance per method, a failed pass/fail one so that false counts as a result, and one pending pass/fail instance.
         */
        private void createAssessedInstanceOfEveryMethod() throws Exception {
            long passFailId = createPresentationAsInstructor(PresentationGradingMethod.PASS_FAIL, null);
            long scoreId = createPresentationAsInstructor(PresentationGradingMethod.SCORE, 20.0);
            long rubricId = createRubricPresentationAsInstructor();
            createInstances(passFailId, List.of(student(1)), null, false, null);
            createInstances(passFailId, List.of(student(2)), null, null, null);
            createInstances(scoreId, List.of(student(1)), 0.0, null, null);
            createInstances(rubricId, List.of(student(1)), null, null, ratings(getRubric(rubricId), 3, 2, 1));
        }
    }

    @Nested
    class DeletionCascade {

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
        void deletingAnInstance_shouldDeleteItsRatings() throws Exception {
            long presentationId = createRubricPresentationAsInstructor();
            PresentationAssessmentInstanceDTO instance = createInstances(presentationId, List.of(student(1)), null, null, ratings(getRubric(presentationId), 3, 2, 1)).getFirst();

            request.delete(instanceUrl(presentationId, instance.id()), HttpStatus.NO_CONTENT);

            assertThat(presentationRubricRatingRepository.findAllByInstanceIdOrderByCriterionPosition(instance.id())).isEmpty();
            assertThat(presentationRubricCriterionRepository.findAllByPresentationAssessmentIdOrderByPosition(presentationId)).hasSize(3);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
        void deletingThePresentation_shouldDeleteItsRubricInstancesAndRatings() throws Exception {
            long presentationId = createRubricPresentationAsInstructor();
            PresentationRubricDTO rubric = getRubric(presentationId);
            PresentationAssessmentInstanceDTO instance = createInstances(presentationId, List.of(student(1)), null, null, ratings(rubric, 3, 2, 1)).getFirst();
            List<Long> criterionIds = rubric.criteria().stream().map(PresentationRubricCriterionDTO::id).toList();

            request.delete(assessmentUrl(course, presentationId), HttpStatus.NO_CONTENT);

            assertThat(presentationRubricCriterionRepository.findAllByPresentationAssessmentIdOrderByPosition(presentationId)).isEmpty();
            assertThat(presentationRubricLevelRepository.findAllByCriterionIdInOrderByPosition(criterionIds)).isEmpty();
            assertThat(presentationAssessmentInstanceRepository.findById(instance.id())).isEmpty();
            assertThat(presentationRubricRatingRepository.findAllByInstanceIdOrderByCriterionPosition(instance.id())).isEmpty();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
        void resettingTheCourse_shouldDeleteTheRatingsButKeepAnUnlockedRubric() throws Exception {
            PresentationAssessmentDTO created = createPresentation(PresentationGradingMethod.RUBRIC, null, standardRubric());
            PresentationRubricDTO rubric = getRubric(created.id());
            PresentationAssessmentInstanceDTO instance = createInstances(created.id(), List.of(student(1)), null, null, ratings(rubric, 3, 2, 1)).getFirst();

            // The statement the course reset uses for presentations
            presentationAssessmentInstanceRepository.deleteAllByCourseId(course.getId());

            assertThat(presentationRubricRatingRepository.findAllByInstanceIdOrderByCriterionPosition(instance.id())).isEmpty();
            assertThat(getRubric(created.id()).criteria()).isEqualTo(rubric.criteria());
            assertThat(getRubric(created.id()).instanceCount()).isZero();
            // Without instances, the structure of the rubric can change again
            updatePresentation(rubricUpdate(created, List.of(criterion("Structure", 100, PresentationRubricScale.THREE_POINT))), HttpStatus.OK);
        }
    }

    @Nested
    class Roles {

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "USER")
        void asEditor_shouldCreateARubricAndChangeItsWeights() throws Exception {
            PresentationAssessmentDTO created = createPresentation(PresentationGradingMethod.RUBRIC, null, standardRubric());

            updatePresentation(rubricUpdate(created, withWeights(getRubric(created.id()), 40, 40, 20)), HttpStatus.OK);

            assertThat(getRubric(created.id()).criteria()).extracting(PresentationRubricCriterionDTO::weight).containsExactly(40, 40, 20);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
        void asTutor_changingTheRubric_shouldBeForbidden() throws Exception {
            long presentationId = createRubricPresentationAsInstructor();
            PresentationRubricDTO rubric = getRubric(presentationId);
            var update = new PresentationAssessmentDTO(presentationId, "Final presentation", "Presentation of the project", PresentationGradingMethod.RUBRIC, null, null, null,
                    null, withWeights(rubric, 40, 40, 20));

            updatePresentation(update, HttpStatus.FORBIDDEN);

            assertThat(getRubric(presentationId).criteria()).extracting(PresentationRubricCriterionDTO::weight).containsExactly(50, 30, 20);
        }
    }

    /**
     * A change of the weights recalculates the scores of all assessed instances, so it must not overlap with grading.
     * Both orders are tested deterministically: the write that comes second read the version before the other one wrote.
     */
    @Nested
    class ConcurrentRubricChanges {

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
        void changingWeights_afterConcurrentGrading_shouldRejectStaleScores() throws Exception {
            PresentationAssessmentDTO created = createPresentation(PresentationGradingMethod.RUBRIC, null, standardRubric());
            PresentationRubricDTO rubric = getRubric(created.id());
            PresentationAssessmentInstanceDTO instance = createInstances(created.id(), List.of(student(1)), null, null, ratings(rubric, 3, 2, 1)).getFirst();
            long courseId = course.getId();

            // The change of the weights reads the version and recalculates the scores from the ratings before the tutor saves new ones.
            PresentationAssessment weightUpdate = presentationAssessmentRepository.findByIdElseThrow(created.id());
            long versionReadByWeightUpdate = weightUpdate.getVersion();
            PresentationRubricChanges changes = presentationRubricService.buildLockedUpdate(created.id(), withWeights(rubric, 40, 40, 20));

            presentationAssessmentService.updateInstance(courseId, created.id(), instance.id(), instanceUpdate(instance, student(1), null, null, ratings(rubric, 0, 0, 0)));

            assertThatThrownBy(
                    () -> presentationRubricCriterionRepository.updateWithRubricIfVersionMatches(courseId, created.id(), versionReadByWeightUpdate, weightUpdate, changes))
                    .isInstanceOf(ConflictException.class);
            assertThat(getRubric(created.id()).criteria()).extracting(PresentationRubricCriterionDTO::weight).containsExactly(50, 30, 20);
            assertThat(rubricScore(instance.id())).isEqualTo(0.0);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
        void grading_afterConcurrentWeightChange_shouldRejectTheStaleScore() throws Exception {
            PresentationAssessmentDTO created = createPresentation(PresentationGradingMethod.RUBRIC, null, standardRubric());
            PresentationRubricDTO rubric = getRubric(created.id());
            List<PresentationRubricRatingDTO> storedRatings = ratings(rubric, 3, 2, 1);
            long instanceId = createInstances(created.id(), List.of(student(1)), null, null, storedRatings).getFirst().id();
            long courseId = course.getId();

            // The tutor reads the version and calculates the score with the old weights before the weights change.
            long versionReadByGrading = presentationAssessmentRepository.findByIdElseThrow(created.id()).getVersion();
            PresentationAssessmentInstance staleInstance = presentationAssessmentInstanceRepository.findByIdElseThrow(instanceId);
            RubricGrade grade = presentationRubricService.grade(created.id(), ratings(rubric, 0, 0, 0));
            staleInstance.setRubricScore(grade.score());

            presentationAssessmentService.update(courseId, created.id(), rubricUpdate(created, withWeights(rubric, 40, 40, 20)));

            assertThatThrownBy(() -> presentationAssessmentInstanceRepository.updateInstanceIfVersionMatches(courseId, created.id(), versionReadByGrading, staleInstance,
                    grade.ratingsFor(staleInstance))).isInstanceOf(ConflictException.class);
            assertThat(rubricScore(instanceId)).isEqualTo(75.0);
            assertThat(getRatings(created.id(), instanceId)).containsExactlyElementsOf(storedRatings);
        }
    }

    // Requests

    private PresentationAssessmentDTO createPresentation(PresentationGradingMethod gradingMethod, Double maxPoints, List<PresentationRubricCriterionDTO> criteria)
            throws Exception {
        return request.postWithResponseBody(baseUrl(course), presentation(gradingMethod, maxPoints, criteria), PresentationAssessmentDTO.class, HttpStatus.CREATED);
    }

    /** A presentation graded by rubric that has an instance, so that the structure of its rubric is fixed. */
    private PresentationAssessmentDTO createLockedRubricPresentation() throws Exception {
        PresentationAssessmentDTO created = createPresentation(PresentationGradingMethod.RUBRIC, null, standardRubric());
        createInstances(created.id(), List.of(student(1)), null, null, null);
        return created;
    }

    /**
     * Creates a presentation graded by the standard rubric through the service behind the endpoint, for tests that run as a user who may not create presentations.
     */
    private long createRubricPresentationAsInstructor() {
        return presentationAssessmentService.create(course.getId(), presentation(PresentationGradingMethod.RUBRIC, null, standardRubric())).id();
    }

    private void updatePresentation(PresentationAssessmentDTO update, HttpStatus expectedStatus) throws Exception {
        request.putWithResponseBody(assessmentUrl(course, update.id()), update, PresentationAssessmentDTO.class, expectedStatus);
    }

    private PresentationRubricDTO getRubric(long presentationId) throws Exception {
        return request.get(assessmentUrl(course, presentationId) + "/rubric", HttpStatus.OK, PresentationRubricDTO.class);
    }

    private List<PresentationAssessmentInstanceDTO> createInstances(long presentationId, List<String> studentLogins, Double resultPoints, Boolean passed,
            List<PresentationRubricRatingDTO> ratings) throws Exception {
        return request.postListWithResponseBody(instancesUrl(presentationId), batch(studentLogins, resultPoints, passed, ratings), PresentationAssessmentInstanceDTO.class,
                HttpStatus.OK);
    }

    private long createPresentationAsInstructor(PresentationGradingMethod gradingMethod, Double maxPoints) {
        return presentationAssessmentService.create(course.getId(), presentation(gradingMethod, maxPoints, null)).id();
    }

    private PresentationAssessmentInstanceDTO updateInstance(long presentationId, PresentationAssessmentInstanceRequestDTO update, HttpStatus expectedStatus) throws Exception {
        return request.putWithResponseBody(instanceUrl(presentationId, update.id()), update, PresentationAssessmentInstanceDTO.class, expectedStatus);
    }

    private List<PresentationRubricRatingDTO> getRatings(long presentationId, long instanceId) throws Exception {
        return request.getList(instanceUrl(presentationId, instanceId) + "/ratings", HttpStatus.OK, PresentationRubricRatingDTO.class);
    }

    private static PresentationAssessmentInstancesBatchCreateDTO batch(List<String> studentLogins, Double resultPoints, Boolean passed, List<PresentationRubricRatingDTO> ratings) {
        return new PresentationAssessmentInstancesBatchCreateDTO(FIXED_DATE, resultPoints, passed, studentLogins, "en", PresentationAssessmentMode.IN_PERSON, null, null, null,
                ratings);
    }

    private static PresentationAssessmentInstanceRequestDTO instanceUpdate(PresentationAssessmentInstanceDTO instance, String studentLogin, Double resultPoints, Boolean passed,
            List<PresentationRubricRatingDTO> ratings) {
        return new PresentationAssessmentInstanceRequestDTO(instance.id(), FIXED_DATE, resultPoints, passed, studentLogin, "en", PresentationAssessmentMode.IN_PERSON, null, null,
                null, ratings);
    }

    /** Ratings that refer to no rubric, for requests that must be rejected before the ratings are resolved. */
    private static List<PresentationRubricRatingDTO> unresolvedRatings() {
        return List.of(new PresentationRubricRatingDTO(Long.MAX_VALUE, Long.MAX_VALUE, null));
    }

    private static List<PresentationRubricRatingDTO> withComments(List<PresentationRubricRatingDTO> ratings, String... comments) {
        return IntStream.range(0, ratings.size())
                .mapToObj(index -> new PresentationRubricRatingDTO(ratings.get(index).criterionId(), ratings.get(index).levelId(), comments[index])).toList();
    }

    private String instancesUrl(long presentationId) {
        return assessmentUrl(course, presentationId) + "/instances";
    }

    private String instanceUrl(long presentationId, long instanceId) {
        return instancesUrl(presentationId) + "/" + instanceId;
    }

    /** Sends the request and expects it to be rejected with the given error key. */
    private void expectError(MockHttpServletRequestBuilder builder, Object body, String errorKey) throws Exception {
        request.performMvcRequest(json(builder, body)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorKey").value(errorKey));
    }

    /** Sends the request and expects bean validation to reject the given field. */
    private void expectFieldError(MockHttpServletRequestBuilder builder, Object body, String field) throws Exception {
        request.performMvcRequest(json(builder, body)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("error.validation"))
                .andExpect(jsonPath("$.fieldErrors[*].field").value(hasItem(field)));
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, Object body) throws Exception {
        return builder.contentType(MediaType.APPLICATION_JSON).content(request.getObjectMapper().writeValueAsString(body));
    }

    // Data

    private static PresentationAssessmentDTO presentation(PresentationGradingMethod gradingMethod, Double maxPoints, List<PresentationRubricCriterionDTO> criteria) {
        return new PresentationAssessmentDTO(null, "Final presentation", "Presentation of the project", gradingMethod, maxPoints, null, null, null, criteria);
    }

    private static PresentationAssessmentDTO rubricUpdate(PresentationAssessmentDTO presentation, List<PresentationRubricCriterionDTO> criteria) {
        return new PresentationAssessmentDTO(presentation.id(), presentation.title(), presentation.description(), PresentationGradingMethod.RUBRIC, null, null, null, null,
                criteria);
    }

    /** A rubric with three criteria of different weights and scales: content 50 % on five points, delivery 30 % on three points, slides 20 % on five points. */
    private static List<PresentationRubricCriterionDTO> standardRubric() {
        return List.of(criterion("Content", 50, PresentationRubricScale.FIVE_POINT), criterion("Delivery", 30, PresentationRubricScale.THREE_POINT),
                criterion("Slides", 20, PresentationRubricScale.FIVE_POINT));
    }

    private static PresentationRubricCriterionDTO criterion(String name, int weight, PresentationRubricScale scale) {
        List<PresentationRubricLevelDTO> levels = IntStream.rangeClosed(1, scale.getLevelCount()).mapToObj(value -> new PresentationRubricLevelDTO(null, name + " level " + value))
                .toList();
        return new PresentationRubricCriterionDTO(null, name, name + " description", weight, scale, levels);
    }

    /** The stored criteria with new weights, keeping their ids, so that the update is allowed once the rubric is fixed. */
    private static List<PresentationRubricCriterionDTO> withWeights(PresentationRubricDTO rubric, int... weights) {
        return IntStream.range(0, weights.length).mapToObj(index -> {
            PresentationRubricCriterionDTO criterion = rubric.criteria().get(index);
            return new PresentationRubricCriterionDTO(criterion.id(), criterion.name(), criterion.description(), weights[index], criterion.scale(), criterion.levels());
        }).toList();
    }

    /** One rating per criterion of the rubric, at the given level positions (0 is the lowest level). */
    private static List<PresentationRubricRatingDTO> ratings(PresentationRubricDTO rubric, int... positions) {
        return IntStream.range(0, positions.length).mapToObj(index -> {
            PresentationRubricCriterionDTO criterion = rubric.criteria().get(index);
            return new PresentationRubricRatingDTO(criterion.id(), criterion.levels().get(positions[index]).id(), null);
        }).toList();
    }

    private Double rubricScore(long instanceId) {
        return presentationAssessmentInstanceRepository.findByIdElseThrow(instanceId).getRubricScore();
    }

    private static String student(int number) {
        return TEST_PREFIX + "student" + number;
    }

    private static String baseUrl(Course course) {
        return BASE_URL + course.getId() + "/presentation-assessments";
    }

    private static String assessmentUrl(Course course, long presentationId) {
        return baseUrl(course) + "/" + presentationId;
    }
}
