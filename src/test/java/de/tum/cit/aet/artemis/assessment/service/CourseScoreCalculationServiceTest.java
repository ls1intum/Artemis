package de.tum.cit.aet.artemis.assessment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.GradingScale;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.dto.BonusSourceResultDTO;
import de.tum.cit.aet.artemis.assessment.dto.ExerciseCourseScoreDTO;
import de.tum.cit.aet.artemis.assessment.dto.MaxAndReachablePointsDTO;
import de.tum.cit.aet.artemis.assessment.dto.score.StudentScoresDTO;
import de.tum.cit.aet.artemis.assessment.repository.GradingScaleRepository;
import de.tum.cit.aet.artemis.assessment.repository.ParticipantScoreRepository;
import de.tum.cit.aet.artemis.assessment.repository.StudentScoreRepository;
import de.tum.cit.aet.artemis.assessment.test_repository.ResultTestRepository;
import de.tum.cit.aet.artemis.assessment.util.GradingScaleFactory;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.dto.CourseForDashboardDTO;
import de.tum.cit.aet.artemis.course.dto.CourseScoresDTO;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVariantGroup;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.dto.CourseGradeScoreDTO;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismCase;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismVerdict;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentBatchTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

class CourseScoreCalculationServiceTest extends AbstractSpringIntegrationIndependentBatchTest {

    private static final String TEST_PREFIX = "cscservicetest";

    @Autowired
    private StudentParticipationTestRepository studentParticipationRepository;

    @Autowired
    private CourseScoreCalculationService courseScoreCalculationService;

    @Autowired
    private ResultTestRepository resultRepository;

    @Autowired
    private ParticipantScoreRepository participantScoreRepository;

    @Autowired
    private GradingScaleRepository gradingScaleRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private ParticipantScoreScheduleService participantScoreScheduleService;

    private Course course;

    @Autowired
    private StudentScoreRepository studentScoreRepository;

    @BeforeEach
    void init() {
        studentScoreRepository.deleteAll();
        userUtilService.addUsers(TEST_PREFIX, 2, 2, 0, 1);
        course = courseUtilService.createCourseWithAllExerciseTypesAndParticipationsAndSubmissionsAndResults(TEST_PREFIX, false);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void calculateCourseScoresForExamBonusSourceWithNotIncludedExercises() {
        var exerciseList = new ArrayList<>(course.getExercises());
        exerciseList.sort(Comparator.comparing(Exercise::getId));

        var exercise = exerciseList.getFirst();
        exercise.setDueDate(null);
        exercise.setIncludedInOverallScore(IncludedInOverallScore.NOT_INCLUDED);

        // Other exercises should have due dates in the future which is also not included.
        ZonedDateTime now = ZonedDateTime.now();
        exerciseList.stream().skip(1).forEach(ex -> assertThat(ex.getDueDate()).isAfter(now));

        exerciseRepository.save(exercise);

        User student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        Map<Long, BonusSourceResultDTO> bonusSourceResultDTOMap = courseScoreCalculationService.calculateCourseScoresForExamBonusSource(course, null, List.of(student.getId()));
        assertThat(bonusSourceResultDTOMap).hasSize(1);
        BonusSourceResultDTO bonusSourceResultDTO = bonusSourceResultDTOMap.get(student.getId());
        assertThat(bonusSourceResultDTO.achievedPoints()).isZero();
        assertThat(bonusSourceResultDTO.presentationScoreThreshold()).isEqualTo(course.getPresentationScore());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void calculateCourseScoreForExamBonusSourceWithoutExercises() {
        Course course = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);

        User student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        var courseResult = courseScoreCalculationService.calculateCourseScoresForExamBonusSource(course, null, List.of(student.getId()));
        assertThat(courseResult).isNull();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void calculateCourseScoreForExamBonusSourceWithMultipleResultsInParticipation(boolean withDueDate) {

        ZonedDateTime dueDate = withDueDate ? ZonedDateTime.now().plusDays(1) : null;
        course.getExercises().forEach(ex -> ex.setDueDate(dueDate));

        exerciseRepository.saveAll(course.getExercises());

        User student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        var studentParticipations = studentParticipationRepository.findByCourseIdAndStudentIdWithEagerRatedResults(course.getId(), student.getId());

        assertThat(studentParticipations).isNotEmpty();

        // Test with multiple results to assert they are sorted.
        StudentParticipation studentParticipation = studentParticipations.getFirst();
        participationUtilService.createSubmissionAndResult(studentParticipation, 50, true);
        participationUtilService.createSubmissionAndResult(studentParticipation, 40, true);
        participationUtilService.createSubmissionAndResult(studentParticipation, 60, true);

        participantScoreScheduleService.executeScheduledTasks();

        // Wait for service to schedule tasks
        await().atMost(1, TimeUnit.MINUTES).until(participantScoreScheduleService::isIdle);

        // Wait for tasks to complete, default SCHEDULED_TASKS_WAITING_TIME (500ms) is too long for this test.
        await().pollDelay(100, TimeUnit.MILLISECONDS).until(() -> true);

        studentParticipations = studentParticipationRepository.findByCourseIdAndStudentIdWithEagerRatedResults(course.getId(), student.getId());

        // Test with null result set.
        Set<Result> results = participationUtilService.getResultsForParticipation(studentParticipations.get(1));

        // Clear participant scores before deleting results
        for (Long id : studentParticipations.stream().map(StudentParticipation::getExercise).map(Exercise::getId).collect(Collectors.toSet())) {
            participantScoreRepository.deleteAllByExerciseId(id);
        }

        resultRepository.deleteAll(results);

        // Test with empty result set.
        resultRepository.saveAll(participationUtilService.getResultsForParticipation(studentParticipations.get(2)));

        // Test with null score in result.

        // QuizExercise is selected because it has already a score of 0 in the initial test data and we have one participation for each exercise type.
        // Besides that, exercise type is irrelevant for this test.
        StudentParticipation studentParticipationWithZeroScore = studentParticipations.stream().filter(participation -> participation.getExercise() instanceof QuizExercise)
                .findFirst().orElseThrow();
        Result result = participationUtilService.getResultsForParticipation(studentParticipationWithZeroScore).iterator().next();
        assertThat(result.getScore()).isZero();
        result.score(null);
        resultRepository.save(result);

        var courseScores = studentParticipationRepository.findGradeScoresForAllExercisesForCourseAndStudent(course.getId(), student.getId());
        Set<ExerciseCourseScoreDTO> courseExercises = course.getExercises().stream().map(ExerciseCourseScoreDTO::from).collect(Collectors.toSet());

        StudentScoresDTO studentScoresDTO = courseScoreCalculationService.calculateCourseScoreForStudent(course, null, student.getId(), courseScores,
                new MaxAndReachablePointsDTO(25.0, 5.0, 0.0), List.of(), courseExercises);
        if (withDueDate) {
            assertThat(studentScoresDTO.absoluteScore()).isEqualTo(0.0);
            assertThat(studentScoresDTO.relativeScore()).isEqualTo(0.0);
            assertThat(studentScoresDTO.currentRelativeScore()).isEqualTo(0.0);
        }
        else {
            // The text participation contributes nothing because its results were deleted above. That deletion used to
            // be a no-op: the results were an ordered list whose position column stayed at its database default for
            // every result saved through the result repository, so the list came back with a null in it, and the null
            // was filtered out before the delete. The expected values used to encode that.
            assertThat(studentScoresDTO.absoluteScore()).isEqualTo(6.0);
            assertThat(studentScoresDTO.relativeScore()).isEqualTo(24.0);
            assertThat(studentScoresDTO.currentRelativeScore()).isEqualTo(120.0);
        }

        Map<Long, BonusSourceResultDTO> bonusSourceResultDTOMap = courseScoreCalculationService.calculateCourseScoresForExamBonusSource(course, null, List.of(student.getId()));

        assertThat(bonusSourceResultDTOMap).hasSize(1);
        BonusSourceResultDTO bonusSourceResultDTO = bonusSourceResultDTOMap.get(student.getId());
        assertThat(bonusSourceResultDTO.achievedPoints()).isZero();
        assertThat(bonusSourceResultDTO.achievedPresentationScore()).isZero();
        assertThat(bonusSourceResultDTO.mostSeverePlagiarismVerdict()).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getScoresAndParticipationResultsWithNotIncludedExercise() {
        var exerciseList = new ArrayList<>(course.getExercises());
        exerciseList.sort(Comparator.comparing(Exercise::getId));

        var exercise = exerciseList.getFirst();
        exercise.setDueDate(null);
        exercise.setIncludedInOverallScore(IncludedInOverallScore.NOT_INCLUDED);

        exerciseRepository.save(exercise);

        User student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        CourseForDashboardDTO courseForDashboard = courseScoreCalculationService.getScoresAndParticipationResults(course, null, student.getId(), false);
        assertThat(courseForDashboard.course()).isEqualTo(course);
        CourseScoresDTO totalCourseScores = courseForDashboard.totalScores();
        assertThat(totalCourseScores.maxPoints()).isZero();
        assertThat(totalCourseScores.reachablePoints()).isZero();
        assertThat(totalCourseScores.reachablePresentationPoints()).isZero();
        assertThat(totalCourseScores.studentScores().absoluteScore()).isZero();
        assertThat(totalCourseScores.studentScores().relativeScore()).isZero();
        assertThat(totalCourseScores.studentScores().currentRelativeScore()).isZero();

        assertThat(courseForDashboard.participationResults().size()).isEqualTo(5);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getScoresAndParticipationResultsForPastCourse() {
        // Create course with assessment due date passed.
        Course pastCourse = courseUtilService.createCourseWithAllExerciseTypesAndParticipationsAndSubmissionsAndResults(TEST_PREFIX, true);
        User student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        CourseForDashboardDTO courseForDashboard = courseScoreCalculationService.getScoresAndParticipationResults(pastCourse, null, student.getId(), false);
        assertThat(courseForDashboard.course()).isEqualTo(pastCourse);
        CourseScoresDTO totalCourseScores = courseForDashboard.totalScores();
        assertThat(totalCourseScores.maxPoints()).isEqualTo(5.0);
        assertThat(totalCourseScores.reachablePoints()).isEqualTo(5.0);
        assertThat(totalCourseScores.reachablePresentationPoints()).isZero();
        assertThat(totalCourseScores.studentScores().absoluteScore()).isZero();
        assertThat(totalCourseScores.studentScores().relativeScore()).isZero();
        assertThat(totalCourseScores.studentScores().currentRelativeScore()).isZero();

        CourseScoresDTO programmingExerciseScores = courseForDashboard.programmingScores();
        assertThat(programmingExerciseScores.maxPoints()).isZero();
        assertThat(programmingExerciseScores.reachablePoints()).isZero();
        assertThat(programmingExerciseScores.studentScores().absoluteScore()).isZero();
        assertThat(programmingExerciseScores.studentScores().relativeScore()).isZero();
        assertThat(programmingExerciseScores.studentScores().currentRelativeScore()).isZero();

        CourseScoresDTO quizExerciseScores = courseForDashboard.quizScores();
        assertThat(quizExerciseScores.maxPoints()).isEqualTo(5.0);
        assertThat(quizExerciseScores.reachablePoints()).isEqualTo(5.0);
        assertThat(quizExerciseScores.studentScores().absoluteScore()).isZero();
        assertThat(quizExerciseScores.studentScores().relativeScore()).isZero();
        assertThat(quizExerciseScores.studentScores().currentRelativeScore()).isZero();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getScoresAndParticipationResultsForPastCourseWithGradedPresentations() {
        Course pastCourse = courseUtilService.createCourseWithAllExerciseTypesAndParticipationsAndSubmissionsAndResults(TEST_PREFIX, true);
        pastCourse.setPresentationScore(null);

        GradingScale gradingScale = GradingScaleFactory.generateGradingScaleForCourse(pastCourse, 5, 37.5);
        gradingScaleRepository.save(gradingScale);

        User student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        pastCourse.getExercises().forEach(exercise -> exercise.getStudentParticipations().forEach(participation -> {
            participation.setPresentationScore(100.0);
            studentParticipationRepository.save(participation);
        }));

        CourseForDashboardDTO courseForDashboard = courseScoreCalculationService.getScoresAndParticipationResults(pastCourse, gradingScale, student.getId(), false);
        assertThat(courseForDashboard.course()).isEqualTo(pastCourse);
        CourseScoresDTO totalCourseScores = courseForDashboard.totalScores();
        assertThat(totalCourseScores.maxPoints()).isEqualTo(8.0);
        assertThat(totalCourseScores.reachablePoints()).isEqualTo(8.0);
        assertThat(totalCourseScores.reachablePresentationPoints()).isEqualTo(3.0);
        assertThat(totalCourseScores.studentScores().absoluteScore()).isEqualTo(3.0);
        assertThat(totalCourseScores.studentScores().relativeScore()).isEqualTo(37.5);
        assertThat(totalCourseScores.studentScores().currentRelativeScore()).isEqualTo(37.5);

        CourseScoresDTO programmingExerciseScores = courseForDashboard.programmingScores();
        assertThat(programmingExerciseScores.studentScores().presentationScore()).isZero();

        CourseScoresDTO quizExerciseScores = courseForDashboard.quizScores();
        assertThat(quizExerciseScores.maxPoints()).isEqualTo(5.0);
        assertThat(quizExerciseScores.reachablePoints()).isEqualTo(5.0);
        assertThat(quizExerciseScores.studentScores().absoluteScore()).isZero();
        assertThat(quizExerciseScores.studentScores().relativeScore()).isZero();
        assertThat(quizExerciseScores.studentScores().currentRelativeScore()).isZero();
    }

    @Test
    void calculateCourseScoreWithNoParticipations() {
        User student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        Set<ExerciseCourseScoreDTO> courseExercises = course.getExercises().stream().map(ExerciseCourseScoreDTO::from).collect(Collectors.toSet());
        StudentScoresDTO studentScore = courseScoreCalculationService.calculateCourseScoreForStudent(course, null, student.getId(), List.of(),
                new MaxAndReachablePointsDTO(100.00, 100.00, 0.0), List.of(), courseExercises);
        assertThat(studentScore.absoluteScore()).isZero();
        assertThat(studentScore.relativeScore()).isZero();
        assertThat(studentScore.currentRelativeScore()).isZero();
        assertThat(studentScore.presentationScore()).isZero();

        PlagiarismVerdict mostSeverePlagiarismVerdict = courseScoreCalculationService.findMostServerePlagiarismVerdict(List.of());
        assertThat(mostSeverePlagiarismVerdict).isNull();
        boolean presentationScorePassed = courseScoreCalculationService.isPresentationScoreSufficientForBonus(studentScore.presentationScore(), course.getPresentationScore());
        assertThat(presentationScorePassed).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void variantGroupPointsAreCappedInAchievedScore() {
        User student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        // Two interchangeable variants of the same group, each worth 5 points, but the group is capped at 5 points.
        long variantGroupId = 1L;
        double variantGroupCap = 5.0;
        var variant1 = new ExerciseCourseScoreDTO(101L, ExerciseType.TEXT, IncludedInOverallScore.INCLUDED_COMPLETELY, AssessmentType.MANUAL, null, null, null, 5.0, 0.0,
                course.getId(), variantGroupId, variantGroupCap);
        var variant2 = new ExerciseCourseScoreDTO(102L, ExerciseType.TEXT, IncludedInOverallScore.INCLUDED_COMPLETELY, AssessmentType.MANUAL, null, null, null, 5.0, 0.0,
                course.getId(), variantGroupId, variantGroupCap);
        Set<ExerciseCourseScoreDTO> courseExercises = Set.of(variant1, variant2);

        // The student fully solved both variants (100% each), which would be 10 points without the cap.
        var gradeScores = List.of(new CourseGradeScoreDTO(1L, student.getId(), 101L, 100.0, true, null, ExerciseType.TEXT),
                new CourseGradeScoreDTO(2L, student.getId(), 102L, 100.0, true, null, ExerciseType.TEXT));

        StudentScoresDTO studentScores = courseScoreCalculationService.calculateCourseScoreForStudent(course, null, student.getId(), gradeScores,
                new MaxAndReachablePointsDTO(5.0, 5.0, 0.0), List.of(), courseExercises);

        // The credited score caps the group's combined contribution at 5 points instead of summing to 10.
        assertThat(studentScores.absoluteScore()).isEqualTo(5.0);
        // The total score reports the uncapped 10 points for transparency.
        assertThat(studentScores.absoluteScoreTotal()).isEqualTo(10.0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void totalScoreEqualsCreditedScoreWithoutVariantGroups() {
        User student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        // Two standalone (non-variant) exercises worth 5 points each.
        var exercise1 = new ExerciseCourseScoreDTO(101L, ExerciseType.TEXT, IncludedInOverallScore.INCLUDED_COMPLETELY, AssessmentType.MANUAL, null, null, null, 5.0, 0.0,
                course.getId(), null, null);
        var exercise2 = new ExerciseCourseScoreDTO(102L, ExerciseType.TEXT, IncludedInOverallScore.INCLUDED_COMPLETELY, AssessmentType.MANUAL, null, null, null, 5.0, 0.0,
                course.getId(), null, null);
        Set<ExerciseCourseScoreDTO> courseExercises = Set.of(exercise1, exercise2);

        var gradeScores = List.of(new CourseGradeScoreDTO(1L, student.getId(), 101L, 100.0, true, null, ExerciseType.TEXT),
                new CourseGradeScoreDTO(2L, student.getId(), 102L, 100.0, true, null, ExerciseType.TEXT));

        StudentScoresDTO studentScores = courseScoreCalculationService.calculateCourseScoreForStudent(course, null, student.getId(), gradeScores,
                new MaxAndReachablePointsDTO(10.0, 10.0, 0.0), List.of(), courseExercises);

        // Without any capped variant group, the total score equals the credited score.
        assertThat(studentScores.absoluteScore()).isEqualTo(10.0);
        assertThat(studentScores.absoluteScoreTotal()).isEqualTo(10.0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void variantGroupMaxPointsAreCappedInReachablePoints() {
        long variantGroupId = 1L;
        double variantGroupCap = 5.0;
        ZonedDateTime past = ZonedDateTime.now().minusDays(1);

        // Two variants worth 5 points each in a group capped at 5, plus a standalone exercise worth 3 points.
        var variant1 = new ExerciseCourseScoreDTO(101L, ExerciseType.TEXT, IncludedInOverallScore.INCLUDED_COMPLETELY, AssessmentType.MANUAL, past, past, null, 5.0, 0.0,
                course.getId(), variantGroupId, variantGroupCap);
        var variant2 = new ExerciseCourseScoreDTO(102L, ExerciseType.TEXT, IncludedInOverallScore.INCLUDED_COMPLETELY, AssessmentType.MANUAL, past, past, null, 5.0, 0.0,
                course.getId(), variantGroupId, variantGroupCap);
        var standalone = new ExerciseCourseScoreDTO(103L, ExerciseType.TEXT, IncludedInOverallScore.INCLUDED_COMPLETELY, AssessmentType.MANUAL, past, past, null, 3.0, 0.0,
                course.getId(), null, null);

        double reachablePoints = courseScoreCalculationService.calculateReachablePoints(null, Set.of(variant1, variant2, standalone));

        // The group contributes min(5 + 5, 5) = 5, plus the standalone 3, for 8 instead of 13.
        assertThat(reachablePoints).isEqualTo(8.0);
    }

    @Test
    void variantGroupPointsAreCappedInParticipationBasedScore() {
        // The participation-based path reads the cap straight off each exercise's variant group entity (rather than from a
        // pre-built DTO), so it needs its own coverage next to the DTO-based calculateCourseScoreForStudent tests above.
        Course variantCourse = new Course();
        variantCourse.setId(1L);
        variantCourse.setAccuracyOfScores(1);

        // Two interchangeable text variants worth 5 points each, in a group capped at 5 points, both already past due.
        ExerciseVariantGroup group = new ExerciseVariantGroup();
        group.setId(200L);
        group.setMaxPoints(5.0);
        TextExercise variant1 = variantExercise(101L, variantCourse, group);
        TextExercise variant2 = variantExercise(102L, variantCourse, group);

        // The student earned full marks on both variants: 5 + 5 = 10 points before the group cap is applied.
        StudentParticipation participation1 = ratedParticipation(variant1, 100.0);
        StudentParticipation participation2 = ratedParticipation(variant2, 100.0);

        StudentScoresDTO scores = courseScoreCalculationService.calculateCourseScoreForStudentParticipations(variantCourse, null, 1L, List.of(participation1, participation2),
                new MaxAndReachablePointsDTO(5.0, 5.0, 0.0), List.of());

        // The credited score caps the group's combined contribution at 5, while the total score keeps the uncapped 10.
        assertThat(scores.absoluteScore()).isEqualTo(5.0);
        assertThat(scores.absoluteScoreTotal()).isEqualTo(10.0);
    }

    @Test
    void cappedPointsPerGroupReturnsMinOfSumAndCapPerGroupExcludingUngrouped() {
        VariantGroupCappedSum cappedSum = new VariantGroupCappedSum();
        cappedSum.add(1L, 5.0, 4.0); // group 1: 4
        cappedSum.add(1L, 5.0, 4.0); // group 1 sum 8, capped to 5
        cappedSum.add(2L, 10.0, 3.0); // group 2: 3 (below its cap)
        cappedSum.add(null, null, 7.0); // ungrouped contribution: not part of any group

        assertThat(cappedSum.cappedPointsPerGroup()).containsExactlyInAnyOrderEntriesOf(Map.of(1L, 5.0, 2L, 3.0));
    }

    @Test
    void cappedPointsPerGroupIncludesUncappedGroupsAtRawSum() {
        VariantGroupCappedSum cappedSum = new VariantGroupCappedSum();
        cappedSum.add(1L, null, 4.0); // group 1 has no configured cap
        cappedSum.add(1L, null, 4.0); // group 1 sum 8, not capped

        assertThat(cappedSum.cappedPointsPerGroup()).containsExactlyInAnyOrderEntriesOf(Map.of(1L, 8.0));
        assertThat(cappedSum.total()).isEqualTo(8.0);
    }

    @Test
    void achievedPointsPerVariantGroupAreCappedAtGroupMaxPoints() {
        ExerciseVariantGroup group = cappedGroup(200L, 5.0);
        Course variantCourse = variantScoringCourse();
        var participations = List.of(ratedParticipation(variantExercise(101L, variantCourse, group), 100.0),
                ratedParticipation(variantExercise(102L, variantCourse, group), 100.0));

        Map<Long, Double> perGroup = courseScoreCalculationService.calculateAchievedPointsPerVariantGroup(1L, participations, List.of());

        // 5 + 5 earned, capped at the group's 5.
        assertThat(perGroup).containsExactlyInAnyOrderEntriesOf(Map.of(200L, 5.0));
    }

    @Test
    void achievedPointsPerVariantGroupApplyPerExercisePlagiarismDeduction() {
        // Cap high enough not to bind, so the deduction (not the cap) drives the result.
        ExerciseVariantGroup group = cappedGroup(200L, 100.0);
        Course variantCourse = variantScoringCourse();
        TextExercise variant1 = variantExercise(101L, variantCourse, group);
        TextExercise variant2 = variantExercise(102L, variantCourse, group);
        User student = new User();
        student.setId(1L);
        // A 50% point-deduction verdict on variant1 halves its 5 points to 2.5; variant2 keeps its 5 → 7.5 total.
        PlagiarismCase deduction = plagiarismCase(student, variant1, PlagiarismVerdict.POINT_DEDUCTION, 50);

        Map<Long, Double> perGroup = courseScoreCalculationService.calculateAchievedPointsPerVariantGroup(1L,
                List.of(ratedParticipation(variant1, 100.0), ratedParticipation(variant2, 100.0)), List.of(deduction));

        assertThat(perGroup).containsExactlyInAnyOrderEntriesOf(Map.of(200L, 7.5));
    }

    @Test
    void achievedPointsPerVariantGroupIncludesUncappedGroupsAtRawSum() {
        // A group with no configured maxPoints is deliberately uncapped; it must still appear in the map (the
        // group-detail page reads it and treats a missing entry as 0), just without a cap applied.
        ExerciseVariantGroup group = uncappedGroup(200L);
        Course variantCourse = variantScoringCourse();
        var participations = List.of(ratedParticipation(variantExercise(101L, variantCourse, group), 100.0),
                ratedParticipation(variantExercise(102L, variantCourse, group), 100.0));

        Map<Long, Double> perGroup = courseScoreCalculationService.calculateAchievedPointsPerVariantGroup(1L, participations, List.of());

        // 5 + 5 earned, no cap to apply.
        assertThat(perGroup).containsExactlyInAnyOrderEntriesOf(Map.of(200L, 10.0));
    }

    @Test
    void achievedPointsPerVariantGroupAreEmptyOnPlagiarismVerdict() {
        ExerciseVariantGroup group = cappedGroup(200L, 5.0);
        Course variantCourse = variantScoringCourse();
        TextExercise variant1 = variantExercise(101L, variantCourse, group);
        TextExercise variant2 = variantExercise(102L, variantCourse, group);
        User student = new User();
        student.setId(1L);
        // A full PLAGIARISM verdict zeroes the whole course, so no group contributes anything.
        PlagiarismCase plagiarism = plagiarismCase(student, variant1, PlagiarismVerdict.PLAGIARISM, 0);

        Map<Long, Double> perGroup = courseScoreCalculationService.calculateAchievedPointsPerVariantGroup(1L,
                List.of(ratedParticipation(variant1, 100.0), ratedParticipation(variant2, 100.0)), List.of(plagiarism));

        assertThat(perGroup).isEmpty();
    }

    /** A capped variant group with the given id and maxPoints. */
    private static ExerciseVariantGroup cappedGroup(long id, double maxPoints) {
        ExerciseVariantGroup group = new ExerciseVariantGroup();
        group.setId(id);
        group.setMaxPoints(maxPoints);
        return group;
    }

    /** A variant group with the given id and no configured maxPoints (deliberately uncapped). */
    private static ExerciseVariantGroup uncappedGroup(long id) {
        ExerciseVariantGroup group = new ExerciseVariantGroup();
        group.setId(id);
        return group;
    }

    /** An in-memory course used for variant-group score rounding (accuracy of scores set so results are exact). */
    private static Course variantScoringCourse() {
        Course course = new Course();
        course.setId(1L);
        course.setAccuracyOfScores(1);
        return course;
    }

    /** A plagiarism case assigning the given verdict (and point deduction) to the student for the given exercise. */
    private static PlagiarismCase plagiarismCase(User student, TextExercise exercise, PlagiarismVerdict verdict, int pointDeduction) {
        PlagiarismCase plagiarismCase = new PlagiarismCase();
        plagiarismCase.setStudent(student);
        plagiarismCase.setExercise(exercise);
        plagiarismCase.setVerdict(verdict);
        plagiarismCase.setVerdictPointDeduction(pointDeduction);
        return plagiarismCase;
    }

    /** Builds an in-memory text exercise worth 5 points that belongs to the given (capped) variant group and is already due. */
    private static TextExercise variantExercise(long id, Course course, ExerciseVariantGroup group) {
        TextExercise exercise = new TextExercise();
        exercise.setId(id);
        exercise.setCourse(course);
        exercise.setMaxPoints(5.0);
        exercise.setBonusPoints(0.0);
        exercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);
        exercise.setDueDate(ZonedDateTime.now().minusDays(1));
        exercise.setExerciseVariantGroup(group);
        return exercise;
    }

    /** Builds an in-memory participation for the given exercise carrying a single rated result with the given score. */
    private static StudentParticipation ratedParticipation(TextExercise exercise, double score) {
        StudentParticipation participation = new StudentParticipation();
        participation.setExercise(exercise);
        TextSubmission submission = new TextSubmission();
        Result result = ParticipationFactory.generateResult(true, score);
        result.setCompletionDate(ZonedDateTime.now().minusHours(1));
        submission.addResult(result);
        participation.addSubmission(submission);
        return participation;
    }

    @Test
    void getResultsForParticipationEdgeCases() {
        ZonedDateTime dueDate = ZonedDateTime.now().plusSeconds(10);
        course.getExercises().forEach(ex -> ex.setDueDate(dueDate));

        exerciseRepository.saveAll(course.getExercises());

        // Test null participation case.
        assertThat(courseScoreCalculationService.getResultForParticipation(null, dueDate)).isNull();

        User student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        var studentParticipations = studentParticipationRepository.findByCourseIdAndStudentIdWithEagerRatedResults(course.getId(), student.getId());

        assertThat(studentParticipations).isNotEmpty();

        // Test with multiple results to assert they are sorted.
        StudentParticipation studentParticipation = studentParticipations.getFirst();
        participationUtilService.createSubmissionAndResult(studentParticipation, 50, true);
        participationUtilService.createSubmissionAndResult(studentParticipation, 40, true);
        Result latestResult = participationUtilService.createSubmissionAndResult(studentParticipation, 60, true);

        // Test getting the latest rated result.
        studentParticipations = studentParticipationRepository.findByCourseIdAndStudentIdWithEagerRatedResults(course.getId(), student.getId());
        assertThat(courseScoreCalculationService.getResultForParticipation(studentParticipations.getFirst(), dueDate).getScore()).isEqualTo(latestResult.getScore());

        // Test with latest rated result after the due date and grace period.
        latestResult.setCompletionDate(dueDate.plusSeconds(30L)); // Due date was set 10 seconds in the future, add more than that.
        resultRepository.save(latestResult);

        studentParticipations = studentParticipationRepository.findByCourseIdAndStudentIdWithEagerRatedResults(course.getId(), student.getId());
        // Should retrieve the latest result before the due date.
        assertThat(courseScoreCalculationService.getResultForParticipation(studentParticipations.getFirst(), dueDate).getScore()).isEqualTo(40L);
    }
}
