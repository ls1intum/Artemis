package de.tum.cit.aet.artemis.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.assessment.domain.Bonus;
import de.tum.cit.aet.artemis.assessment.domain.BonusStrategy;
import de.tum.cit.aet.artemis.assessment.domain.GradeStep;
import de.tum.cit.aet.artemis.assessment.domain.GradeType;
import de.tum.cit.aet.artemis.assessment.domain.GradingScale;
import de.tum.cit.aet.artemis.assessment.dto.BonusExampleDTO;
import de.tum.cit.aet.artemis.assessment.dto.BonusRequestDTO;
import de.tum.cit.aet.artemis.assessment.dto.BonusResponseDTO;
import de.tum.cit.aet.artemis.assessment.repository.BonusRepository;
import de.tum.cit.aet.artemis.assessment.repository.GradingScaleRepository;
import de.tum.cit.aet.artemis.assessment.util.BonusFactory;
import de.tum.cit.aet.artemis.assessment.util.GradingScaleFactory;
import de.tum.cit.aet.artemis.assessment.util.GradingScaleUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentBatchTest;
import de.tum.cit.aet.artemis.text.util.TextExerciseFactory;

class BonusIntegrationTest extends AbstractSpringIntegrationIndependentBatchTest {

    private static final String TEST_PREFIX = "bonusintegration";

    @Autowired
    private BonusRepository bonusRepository;

    @Autowired
    private ExamTestRepository examRepository;

    @Autowired
    private GradingScaleRepository gradingScaleRepository;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private GradingScaleUtilService gradingScaleUtilService;

    private Bonus courseBonus;

    private Course course;

    private GradingScale bonusToExamGradingScale;

    private GradingScale sourceExamGradingScale;

    private GradingScale courseGradingScale;

    private long courseId;

    private long examId;

    /**
     * Initialize variables
     */
    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 0, 0, 0, 1);
        course = courseUtilService.addEmptyCourse();
        course.setMaxPoints(100);
        courseRepository.saveAndFlush(course);

        // Sets the achievable points for the course to 200, even though the course's max points are still set to 100.
        Exercise exercise = TextExerciseFactory.generateTextExercise(ZonedDateTime.now().minusHours(3), ZonedDateTime.now().minusHours(2), ZonedDateTime.now().minusHours(1),
                course);
        exercise.setMaxPoints(200.0);
        exercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);
        exerciseRepository.save(exercise);
        assertThat(course.getMaxPoints()).isEqualTo(100);

        Exam targetExam = examUtilService.addExamWithExerciseGroup(course, true);
        targetExam.setExamMaxPoints(200);
        examRepository.save(targetExam);

        Exam sourceExam = examUtilService.addExamWithExerciseGroup(course, true);
        bonusToExamGradingScale = GradingScaleFactory.generateGradingScaleForExam(targetExam, GradeType.GRADE);

        sourceExamGradingScale = GradingScaleFactory.generateGradingScaleForExam(sourceExam, GradeType.BONUS);

        courseGradingScale = GradingScaleFactory.generateGradingScaleForCourse(course, GradeType.BONUS);

        gradingScaleRepository.saveAll(List.of(bonusToExamGradingScale, sourceExamGradingScale, courseGradingScale));

        courseBonus = BonusFactory.generateBonus(BonusStrategy.GRADES_CONTINUOUS, 1.0, courseGradingScale.getId(), bonusToExamGradingScale.getId());
        bonusRepository.save(courseBonus);

        bonusToExamGradingScale.setBonusStrategy(BonusStrategy.GRADES_CONTINUOUS);
        gradingScaleRepository.save(bonusToExamGradingScale);

        courseId = bonusToExamGradingScale.getExam().getCourse().getId();
        examId = bonusToExamGradingScale.getExam().getId();
    }

    private String bonusesUrl() {
        return "/api/assessment/courses/" + courseId + "/exams/" + examId + "/bonuses";
    }

    private static BonusRequestDTO bonusRequest(Long id, double weight, BonusStrategy bonusStrategy, long sourceGradingScaleId) {
        return new BonusRequestDTO(id, weight, bonusStrategy, new BonusRequestDTO.GradingScaleIdDTO(sourceGradingScaleId));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetBonusSourcesForTargetExamNotFound() throws Exception {
        bonusRepository.delete(courseBonus);

        request.get(bonusesUrl(), HttpStatus.NOT_FOUND, BonusResponseDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetBonusForTargetExam() throws Exception {

        BonusResponseDTO foundBonus = request.get(bonusesUrl(), HttpStatus.OK, BonusResponseDTO.class);

        assertThat(foundBonus.id()).isEqualTo(courseBonus.getId());
        assertThat(foundBonus.weight()).isEqualTo(courseBonus.getWeight());
        assertThat(foundBonus.bonusStrategy()).isEqualTo(BonusStrategy.GRADES_CONTINUOUS);

        // The source is a COURSE grading scale: its nested course must be serialized (the client reads course/exam off it),
        // and its max points must reflect the reachable points (200, from the 200-point exercise) rather than the course's
        // configured max points (100). The exam reference must be absent for a course grading scale.
        var source = foundBonus.sourceGradingScale();
        assertThat(source.id()).isEqualTo(courseGradingScale.getId());
        assertThat(source.exam()).isNull();
        assertThat(source.course()).isNotNull();
        assertThat(source.course().id()).isEqualTo(courseId);
        assertThat(source.course().maxPoints()).isEqualTo(200);
        // Source grade steps are excluded unless explicitly requested (omitted on the wire via @JsonInclude(NON_EMPTY)).
        assertThat(source.gradeSteps()).isNullOrEmpty();

        // The bonusTo is an EXAM grading scale: its nested exam (with owning course) must be serialized so the client can
        // derive the rounding settings, while its grade steps are always excluded from the bonus response.
        var bonusTo = foundBonus.bonusToGradingScale();
        assertThat(bonusTo.id()).isEqualTo(bonusToExamGradingScale.getId());
        assertThat(bonusTo.exam()).isNotNull();
        assertThat(bonusTo.exam().examMaxPoints()).isEqualTo(200);
        assertThat(bonusTo.exam().course()).isNotNull();
        assertThat(bonusTo.exam().course().id()).isEqualTo(courseId);
        assertThat(bonusTo.gradeSteps()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetBonusIncludesSourceGradeStepsOnlyWhenRequested() throws Exception {
        // Re-point the bonus to a source grading scale that actually has grade steps (the grading-key student view reads them).
        bonusRepository.delete(courseBonus);
        GradingScale sourceWithSteps = createSourceGradingScaleWithGradeStepsForGradesBonusStrategy(course);
        gradingScaleRepository.save(sourceWithSteps);
        request.postWithResponseBody(bonusesUrl(), bonusRequest(null, 1.0, BonusStrategy.GRADES_CONTINUOUS, sourceWithSteps.getId()), BonusResponseDTO.class, HttpStatus.CREATED);

        BonusResponseDTO without = request.get(bonusesUrl(), HttpStatus.OK, BonusResponseDTO.class);
        assertThat(without.sourceGradingScale().gradeSteps()).isNullOrEmpty();

        BonusResponseDTO with = request.get(bonusesUrl() + "?includeSourceGradeSteps=true", HttpStatus.OK, BonusResponseDTO.class);
        assertThat(with.sourceGradingScale().gradeSteps()).isNotEmpty();
        assertThat(with.sourceGradingScale().gradeSteps()).allSatisfy(gradeStep -> assertThat(gradeStep.gradeName()).isNotBlank());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSaveBonusForTargetExam() throws Exception {
        bonusRepository.delete(courseBonus);
        Exam newExam = examUtilService.addExamWithExerciseGroup(course, true);

        var newExamGradingScale = GradingScaleFactory.generateGradingScaleForExam(newExam, GradeType.BONUS);
        gradingScaleRepository.save(newExamGradingScale);

        BonusResponseDTO savedBonus = request.postWithResponseBody(bonusesUrl(), bonusRequest(null, -1.0, BonusStrategy.GRADES_CONTINUOUS, newExamGradingScale.getId()),
                BonusResponseDTO.class, HttpStatus.CREATED);

        assertThat(savedBonus.id()).isGreaterThan(0);
        assertThat(savedBonus.weight()).isEqualTo(-1.0);
        assertThat(savedBonus.bonusStrategy()).isEqualTo(BonusStrategy.GRADES_CONTINUOUS);
        assertThat(savedBonus.sourceGradingScale().id()).isEqualTo(newExamGradingScale.getId());

        // Hardened persistence check: exactly one bonus row exists for this bonusTo (guards against the cascade duplicate-insert
        // footgun) and the persisted bonus carries the correct source and bonusTo back-references when reloaded from the DB.
        Set<Bonus> persisted = bonusRepository.findAllByBonusToExamId(examId);
        assertThat(persisted).hasSize(1);
        Bonus persistedBonus = persisted.iterator().next();
        assertThat(persistedBonus.getId()).isEqualTo(savedBonus.id());
        assertThat(persistedBonus.getWeight()).isEqualTo(-1.0);
        assertThat(persistedBonus.getSourceGradingScale().getId()).isEqualTo(newExamGradingScale.getId());
        assertThat(persistedBonus.getBonusToGradingScale().getId()).isEqualTo(bonusToExamGradingScale.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSaveBonusForTargetExamDuplicateError() throws Exception {

        request.postWithResponseBody(bonusesUrl(), bonusRequest(null, 1.0, BonusStrategy.GRADES_CONTINUOUS, sourceExamGradingScale.getId()), BonusResponseDTO.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSaveBonusForGradeScaleTypeError() throws Exception {
        bonusRepository.delete(courseBonus);

        courseGradingScale.setGradeType(GradeType.GRADE);
        gradingScaleRepository.save(courseGradingScale);

        BonusRequestDTO newBonus = bonusRequest(null, -1.0, BonusStrategy.GRADES_CONTINUOUS, courseGradingScale.getId());

        // Source grading scale must have GradeType.BONUS.
        request.postWithResponseBody(bonusesUrl(), newBonus, BonusResponseDTO.class, HttpStatus.BAD_REQUEST);

        courseGradingScale.setGradeType(GradeType.BONUS);
        bonusToExamGradingScale.setGradeType(GradeType.BONUS);
        gradingScaleRepository.saveAll(List.of(courseGradingScale, bonusToExamGradingScale));

        // BonusTo grading scale must have GradeType.GRADE.
        request.postWithResponseBody(bonusesUrl(), newBonus, BonusResponseDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testCreateBonusIsNotAtLeastInstructorInCourseForbidden() throws Exception {
        request.postWithResponseBody(bonusesUrl(), bonusRequest(null, 1.0, BonusStrategy.GRADES_CONTINUOUS, sourceExamGradingScale.getId()), BonusResponseDTO.class,
                HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDeleteBonusIsNotAtLeastInstructorInCourseForbidden() throws Exception {
        request.delete(bonusesUrl() + "/" + courseBonus.getId(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUpdateBonusIsNotAtLeastInstructorInCourseForbidden() throws Exception {
        request.put(bonusesUrl() + "/" + courseBonus.getId(), bonusRequest(courseBonus.getId(), 1.0, BonusStrategy.GRADES_CONTINUOUS, courseGradingScale.getId()),
                HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateBonusWithMismatchingIdsInPathAndBodyConflict() throws Exception {
        // The path id ("{id}1") deliberately differs from the body id to trigger the mismatch conflict.
        request.put(bonusesUrl() + "/" + courseBonus.getId() + 1, bonusRequest(courseBonus.getId(), 1.0, BonusStrategy.GRADES_CONTINUOUS, courseGradingScale.getId()),
                HttpStatus.CONFLICT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateBonusWithoutChangingSourceGradingScale() throws Exception {

        BonusResponseDTO foundBonus = request.get(bonusesUrl(), HttpStatus.OK, BonusResponseDTO.class);

        double newWeight = -foundBonus.weight();
        long sourceId = foundBonus.sourceGradingScale().id();
        request.put(bonusesUrl() + "/" + foundBonus.id(), bonusRequest(foundBonus.id(), newWeight, BonusStrategy.POINTS, sourceId), HttpStatus.OK);

        BonusResponseDTO updatedBonus = request.get(bonusesUrl(), HttpStatus.OK, BonusResponseDTO.class);
        assertThat(updatedBonus.id()).isEqualTo(foundBonus.id());
        assertThat(updatedBonus.bonusStrategy()).isEqualTo(BonusStrategy.POINTS);
        assertThat(updatedBonus.weight()).isEqualTo(newWeight);
        assertThat(updatedBonus.sourceGradingScale().id()).isEqualTo(sourceId);

        // Reload from the DB and confirm the update persisted without creating a second bonus row.
        Bonus persisted = bonusRepository.findByIdElseThrow(foundBonus.id());
        assertThat(persisted.getWeight()).isEqualTo(newWeight);
        assertThat(persisted.getSourceGradingScale().getId()).isEqualTo(sourceId);
        assertThat(bonusRepository.findAllByBonusToExamId(examId)).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateBonusWithChangingSourceGradingScale() throws Exception {

        BonusResponseDTO foundBonus = request.get(bonusesUrl(), HttpStatus.OK, BonusResponseDTO.class);
        assertThat(foundBonus.sourceGradingScale().id()).isNotEqualTo(sourceExamGradingScale.getId());

        request.put(bonusesUrl() + "/" + foundBonus.id(), bonusRequest(foundBonus.id(), -foundBonus.weight(), BonusStrategy.POINTS, sourceExamGradingScale.getId()), HttpStatus.OK);

        BonusResponseDTO updatedBonus = request.get(bonusesUrl(), HttpStatus.OK, BonusResponseDTO.class);
        assertThat(updatedBonus.id()).isEqualTo(foundBonus.id());
        assertThat(updatedBonus.sourceGradingScale().id()).isEqualTo(sourceExamGradingScale.getId());
        // The new source is an EXAM grading scale, so its exam reference must now be serialized.
        assertThat(updatedBonus.sourceGradingScale().exam()).isNotNull();

        Bonus persisted = bonusRepository.findByIdElseThrow(foundBonus.id());
        assertThat(persisted.getSourceGradingScale().getId()).isEqualTo(sourceExamGradingScale.getId());
        assertThat(bonusRepository.findAllByBonusToExamId(examId)).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteBonus() throws Exception {

        BonusResponseDTO foundBonus = request.get(bonusesUrl(), HttpStatus.OK, BonusResponseDTO.class);

        request.delete(bonusesUrl() + "/" + foundBonus.id(), HttpStatus.OK);

        // The bonus row is actually gone, and the endpoint reports it as missing.
        assertThat(bonusRepository.findAllByBonusToExamId(examId)).isEmpty();
        request.get(bonusesUrl(), HttpStatus.NOT_FOUND, BonusResponseDTO.class);
    }

    @NonNull
    private GradingScale createBonusToGradingScale(Exam bonusToExam) {
        GradingScale bonusToGradingScale = gradingScaleUtilService.generateGradingScaleWithStickyStep(new double[] { 40, 20, 15, 15, 10, 100 },
                Optional.of(new String[] { "5.0", "4.0", "3.0", "2.0", "1.0", "1.0" }), true, 1);

        bonusToGradingScale.setGradeType(GradeType.GRADE);
        bonusToGradingScale.setExam(bonusToExam);
        return bonusToGradingScale;
    }

    @NonNull
    private GradingScale createSourceGradingScaleWithGradeStepsForGradesBonusStrategy(Course sourceCourse) {
        GradingScale sourceGradingScale = gradingScaleUtilService.generateGradingScaleWithStickyStep(new double[] { 30, 40, 70 }, Optional.of(new String[] { "0", "0.1", "0.2" }),
                true, 1);

        sourceGradingScale.setGradeType(GradeType.BONUS);
        sourceGradingScale.setCourse(sourceCourse);
        return sourceGradingScale;
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = "admin", roles = "ADMIN")
    @EnumSource(value = BonusStrategy.class, names = { "GRADES_DISCRETE" }, mode = EnumSource.Mode.EXCLUDE)
    void testCalculateRawBonus(BonusStrategy bonusStrategy) throws Exception {
        // Calculation results should be consistent with bonus.service.spec.ts

        boolean isContinuous = bonusStrategy == BonusStrategy.GRADES_CONTINUOUS;
        double weight = isContinuous ? -1 : 1;

        Exam bonusToExam = bonusToExamGradingScale.getExam();
        Course sourceCourse = courseGradingScale.getCourse();

        bonusRepository.delete(courseBonus);
        // Line below is needed to prevent EntityNotFoundException for the Bonus instance deleted above.
        bonusToExamGradingScale = gradingScaleRepository.findWithEagerBonusFromByExamId(bonusToExam.getId()).orElseThrow();
        gradingScaleRepository.deleteAll(List.of(bonusToExamGradingScale, courseGradingScale));

        GradingScale sourceGradingScale;
        if (isContinuous) {
            sourceGradingScale = createSourceGradingScaleWithGradeStepsForGradesBonusStrategy(sourceCourse);
        }
        else {
            sourceGradingScale = createSourceGradingScaleWithGradeStepsForPointsBonusStrategy(sourceCourse);
        }

        GradingScale bonusToGradingScale = createBonusToGradingScale(bonusToExam);
        gradingScaleRepository.saveAll(List.of(bonusToGradingScale, sourceGradingScale));

        double bonusToPoints = 50;
        double sourcePoints = 100;
        String expectedExamGrade = "5.0";
        double expectedBonusGrade = 0.0;
        Double expectedFinalPoints = 50.0;
        String expectedFinalGrade = "5.0";
        boolean expectedExceedsMax = false;

        calculateFinalGradeAtServer(bonusStrategy, weight, bonusToPoints, sourcePoints, expectedExamGrade, expectedBonusGrade, expectedFinalPoints, expectedFinalGrade,
                expectedExceedsMax, sourceGradingScale.getId());

        bonusToPoints = 120;
        sourcePoints = 75;
        expectedExamGrade = "3.0";
        expectedBonusGrade = isContinuous ? 0.1 : 10.0;
        expectedFinalPoints = isContinuous ? null : 130.0;
        expectedFinalGrade = isContinuous ? "2.9" : "3.0";

        calculateFinalGradeAtServer(bonusStrategy, weight, bonusToPoints, sourcePoints, expectedExamGrade, expectedBonusGrade, expectedFinalPoints, expectedFinalGrade,
                expectedExceedsMax, sourceGradingScale.getId());

        bonusToPoints = 200;
        sourcePoints = 200;
        expectedExamGrade = "1.0";
        expectedBonusGrade = isContinuous ? 0.2 : 20.0;
        expectedFinalPoints = isContinuous ? null : 200.0;
        expectedFinalGrade = "1.0";
        expectedExceedsMax = true;

        calculateFinalGradeAtServer(bonusStrategy, weight, bonusToPoints, sourcePoints, expectedExamGrade, expectedBonusGrade, expectedFinalPoints, expectedFinalGrade,
                expectedExceedsMax, sourceGradingScale.getId());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testCalculateRawBonusWithGradesContinuousBonusStrategy_nonNumericGrades() throws Exception {
        // Calculation results should be consistent with bonus.service.spec.ts

        BonusStrategy bonusStrategy = BonusStrategy.GRADES_CONTINUOUS;
        double weight = -1;

        Exam bonusToExam = bonusToExamGradingScale.getExam();
        Course sourceCourse = courseGradingScale.getCourse();

        bonusRepository.delete(courseBonus);
        // Line below is needed to prevent EntityNotFoundException for the Bonus instance deleted above.
        bonusToExamGradingScale = gradingScaleRepository.findWithEagerBonusFromByExamId(bonusToExam.getId()).orElseThrow();
        gradingScaleRepository.deleteAll(List.of(bonusToExamGradingScale, courseGradingScale));

        GradingScale sourceGradingScale = createSourceGradingScaleWithGradeStepsForGradesBonusStrategy(sourceCourse);
        GradingScale bonusToGradingScale = createBonusToGradingScale(bonusToExam);

        GradeStep matchedBonusSourceGradeStep = sourceGradingScale.getGradeSteps().stream().filter(gradeStep -> "0.1".equals(gradeStep.getGradeName())).findFirst().orElseThrow();
        matchedBonusSourceGradeStep.setGradeName("NonNumericBonusSourceGrade");

        GradeStep matchedBonusToGradeStep = bonusToGradingScale.getGradeSteps().stream().filter(gradeStep -> "4.0".equals(gradeStep.getGradeName())).findFirst().orElseThrow();
        matchedBonusToGradeStep.setGradeName("NonNumericBonusToGrade");

        gradingScaleRepository.saveAll(List.of(bonusToGradingScale, sourceGradingScale));

        double bonusToPoints = 125;
        double sourcePoints = 150;
        String expectedExamGrade = "3.0";
        double expectedBonusGrade = 0.2;
        Double expectedFinalPoints = null;
        String expectedFinalGrade = "2.8";
        boolean expectedExceedsMax = false;

        // First assert there is a working source and bonusTo grade step combinations before checking BAD_REQUEST errors.
        calculateFinalGradeAtServer(bonusStrategy, weight, bonusToPoints, sourcePoints, expectedExamGrade, expectedBonusGrade, expectedFinalPoints, expectedFinalGrade,
                expectedExceedsMax, sourceGradingScale.getId());

        // Test getting an error due to non-numeric bonus source grade step.
        bonusToPoints = 125;
        sourcePoints = 75;
        request.get(
                "/api/assessment/courses/" + courseId + "/exams/" + examId + "/bonuses/calculate-raw?bonusStrategy=" + bonusStrategy + "&calculationSign=" + weight
                        + "&sourceGradingScaleId=" + sourceGradingScale.getId() + "&bonusToPoints=" + bonusToPoints + "&sourcePoints=" + sourcePoints,
                HttpStatus.BAD_REQUEST, BonusExampleDTO.class);

        // Test getting an error due to non-numeric bonusTo grade step.
        bonusToPoints = 110;
        sourcePoints = 150;
        request.get(
                "/api/assessment/courses/" + courseId + "/exams/" + examId + "/bonuses/calculate-raw?bonusStrategy=" + bonusStrategy + "&calculationSign=" + weight
                        + "&sourceGradingScaleId=" + sourceGradingScale.getId() + "&bonusToPoints=" + bonusToPoints + "&sourcePoints=" + sourcePoints,
                HttpStatus.BAD_REQUEST, BonusExampleDTO.class);

    }

    @NonNull
    private BonusExampleDTO calculateFinalGradeAtServer(BonusStrategy bonusStrategy, double weight, double bonusToPoints, double sourcePoints, String expectedExamGrade,
            double expectedBonusGrade, Double expectedFinalPoints, String expectedFinalGrade, boolean expectedExceedsMax, long sourceGradingScaleId) throws Exception {
        BonusExampleDTO bonusExample = request.get(
                "/api/assessment/courses/" + courseId + "/exams/" + examId + "/bonuses/calculate-raw?bonusStrategy=" + bonusStrategy + "&calculationSign=" + weight
                        + "&sourceGradingScaleId=" + sourceGradingScaleId + "&bonusToPoints=" + bonusToPoints + "&sourcePoints=" + sourcePoints,
                HttpStatus.OK, BonusExampleDTO.class);
        assertThat(bonusExample.examGrade()).isEqualTo(expectedExamGrade);
        assertThat(bonusExample.bonusGrade()).isEqualTo(expectedBonusGrade);
        assertThat(bonusExample.finalPoints()).isEqualTo(expectedFinalPoints);
        assertThat(bonusExample.finalGrade()).isEqualTo(expectedFinalGrade);
        assertThat(bonusExample.exceedsMax()).isEqualTo(expectedExceedsMax);
        return bonusExample;
    }

    @NonNull
    private GradingScale createSourceGradingScaleWithGradeStepsForPointsBonusStrategy(Course sourceCourse) {
        GradingScale sourceGradingScale = gradingScaleUtilService.generateGradingScaleWithStickyStep(new double[] { 30, 40, 70 }, Optional.of(new String[] { "0", "10", "20" }),
                true, 1);

        sourceGradingScale.setGradeType(GradeType.BONUS);
        sourceGradingScale.setCourse(sourceCourse);
        return sourceGradingScale;
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testCalculateRawBonusWithPointsBonusStrategy_nonNumericGrades() throws Exception {
        BonusStrategy bonusStrategy = BonusStrategy.POINTS;
        double weight = 1;

        Exam bonusToExam = bonusToExamGradingScale.getExam();
        Course sourceCourse = courseGradingScale.getCourse();

        bonusRepository.delete(courseBonus);
        // Line below is needed to prevent EntityNotFoundException for the Bonus instance deleted above.
        bonusToExamGradingScale = gradingScaleRepository.findWithEagerBonusFromByExamId(bonusToExam.getId()).orElseThrow();
        gradingScaleRepository.deleteAll(List.of(bonusToExamGradingScale, courseGradingScale));

        GradingScale sourceGradingScale = createSourceGradingScaleWithGradeStepsForPointsBonusStrategy(sourceCourse);
        GradingScale bonusToGradingScale = createBonusToGradingScale(bonusToExam);

        GradeStep matchedBonusSourceGradeStep = sourceGradingScale.getGradeSteps().stream().filter(gradeStep -> "10".equals(gradeStep.getGradeName())).findFirst().orElseThrow();
        matchedBonusSourceGradeStep.setGradeName("NonNumericBonusSourceGrade");

        gradingScaleRepository.saveAll(List.of(bonusToGradingScale, sourceGradingScale));

        double bonusToPoints = 120;
        double sourcePoints = 75;

        request.get(
                "/api/assessment/courses/" + courseId + "/exams/" + examId + "/bonuses/calculate-raw?bonusStrategy=" + bonusStrategy + "&calculationSign=" + weight
                        + "&sourceGradingScaleId=" + sourceGradingScale.getId() + "&bonusToPoints=" + bonusToPoints + "&sourcePoints=" + sourcePoints,
                HttpStatus.BAD_REQUEST, BonusExampleDTO.class);

    }

}
