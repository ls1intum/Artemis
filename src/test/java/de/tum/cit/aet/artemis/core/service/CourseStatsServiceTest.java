package de.tum.cit.aet.artemis.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.admin.repository.StatisticsRepository;
import de.tum.cit.aet.artemis.assessment.domain.ComplaintType;
import de.tum.cit.aet.artemis.assessment.domain.GradingScale;
import de.tum.cit.aet.artemis.assessment.repository.ComplaintRepository;
import de.tum.cit.aet.artemis.assessment.repository.ParticipantScoreRepository;
import de.tum.cit.aet.artemis.assessment.repository.RatingRepository;
import de.tum.cit.aet.artemis.assessment.service.ComplaintService;
import de.tum.cit.aet.artemis.assessment.service.PresentationPointsCalculationService;
import de.tum.cit.aet.artemis.assessment.service.TutorLeaderboardService;
import de.tum.cit.aet.artemis.assessment.test_repository.ComplaintResponseTestRepository;
import de.tum.cit.aet.artemis.assessment.test_repository.ResultTestRepository;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.dto.DueDateStat;
import de.tum.cit.aet.artemis.core.dto.TutorLeaderboardDTO;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.core.test_repository.LLMTokenUsageTraceTestRepository;
import de.tum.cit.aet.artemis.core.util.TimeUtil;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.dto.CourseManagementDetailViewDTO;
import de.tum.cit.aet.artemis.course.service.CourseStatsService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseTestRepository;
import de.tum.cit.aet.artemis.exercise.team.TeamUtilService;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.SubmissionTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.util.TextExerciseFactory;

class CourseStatsServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "coursestatsservice";

    @Autowired
    private CourseStatsService courseStatsService;

    @Autowired
    private ExerciseTestRepository exerciseRepository;

    @Autowired
    private StudentParticipationTestRepository studentParticipationRepo;

    @Autowired
    private SubmissionTestRepository submissionRepository;

    @Autowired
    private TeamUtilService teamUtilService;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 2, 0, 0, 1);
    }

    static IntStream weekRangeProvider() {
        // test all weeks (including the year change from 52 or 53 to 0)
        return IntStream.range(0, 60);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @MethodSource("weekRangeProvider")
    void testGetActiveStudents(long weeks) {
        ZonedDateTime date = ZonedDateTime.now().minusWeeks(weeks);
        SecurityUtils.setAuthorizationObject();
        var course = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        var exercise = TextExerciseFactory.generateTextExercise(date, date, date, course);
        course.addExercises(exercise);
        exercise = exerciseRepository.save(exercise);

        var student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var participation1 = new StudentParticipation();
        participation1.setParticipant(student1);
        participation1.exercise(exercise);
        var student2 = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
        var participation2 = new StudentParticipation();
        participation2.setParticipant(student2);
        participation2.exercise(exercise);
        var participation3 = new StudentParticipation();
        participation3.setParticipant(student2);
        participation3.exercise(exercise);
        var participation4 = new StudentParticipation();
        participation4.setParticipant(student2);
        participation4.exercise(exercise);
        studentParticipationRepo.saveAll(Arrays.asList(participation1, participation2, participation3, participation4));

        var submission1 = new TextSubmission();
        submission1.text("text of text submission1");
        submission1.setLanguage(Language.ENGLISH);
        submission1.setSubmitted(true);
        submission1.setParticipation(participation1);
        submission1.setSubmissionDate(date);

        var submission2 = new TextSubmission();
        submission2.text("text of text submission2");
        submission2.setLanguage(Language.ENGLISH);
        submission2.setSubmitted(true);
        submission2.setParticipation(participation2);
        submission2.setSubmissionDate(date);

        var submission3 = new TextSubmission();
        submission3.text("text of text submission3");
        submission3.setLanguage(Language.ENGLISH);
        submission3.setSubmitted(true);
        submission3.setSubmissionDate(date.minusDays(14));
        submission3.setParticipation(participation3);

        var submission4 = new TextSubmission();
        submission4.text("text of text submission4");
        submission4.setLanguage(Language.ENGLISH);
        submission4.setSubmitted(true);
        submission4.setSubmissionDate(date.minusDays(7));
        submission4.setParticipation(participation4);

        submissionRepository.saveAll(Arrays.asList(submission1, submission2, submission3, submission4));

        var exerciseList = new HashSet<Long>();
        exerciseList.add(exercise.getId());
        var activeStudents = courseStatsService.getActiveStudents(exerciseList, 0, 4, date);
        assertThat(activeStudents).hasSize(4).containsExactly(0, 1, 1, 2);
    }

    @Test
    void testGetActiveStudents_UTCConversion() {
        ZonedDateTime date = ZonedDateTime.of(2022, 1, 2, 0, 0, 0, 0, ZonedDateTime.now().getZone());
        SecurityUtils.setAuthorizationObject();
        var course = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        var exercise = TextExerciseFactory.generateTextExercise(date, date, date, course);
        course.addExercises(exercise);
        exercise = exerciseRepository.save(exercise);

        var student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var participation1 = new StudentParticipation();
        participation1.setParticipant(student1);
        participation1.exercise(exercise);

        studentParticipationRepo.save(participation1);

        var submission1 = new TextSubmission();
        submission1.text("text of text submission1");
        submission1.setLanguage(Language.ENGLISH);
        submission1.setSubmitted(true);
        submission1.setParticipation(participation1);
        submission1.setSubmissionDate(date.plusDays(1).plusMinutes(59).plusSeconds(59).plusNanos(59));

        submissionRepository.save(submission1);

        var exerciseList = new HashSet<Long>();
        exerciseList.add(exercise.getId());
        var activeStudents = courseStatsService.getActiveStudents(exerciseList, 0, 4, ZonedDateTime.of(2022, 1, 25, 0, 0, 0, 0, ZoneId.systemDefault()));
        assertThat(activeStudents).hasSize(4).containsExactly(1, 0, 0, 0);
    }

    /**
     * Only individual participations count towards the active students chart. The chart keys its rows on the
     * participating student, and a team participation has none, so its submissions must not add a phantom student.
     */
    @Test
    void testGetActiveStudentsIgnoresTeamParticipations() {
        ZonedDateTime date = ZonedDateTime.now();
        SecurityUtils.setAuthorizationObject();
        var course = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
        var exercise = TextExerciseFactory.generateTextExercise(date, date, date, course);
        exercise.setMode(ExerciseMode.TEAM);
        course.addExercises(exercise);
        exercise = exerciseRepository.save(exercise);

        var student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var team = teamUtilService.createTeam(Set.of(student), userUtilService.getUserByLogin(TEST_PREFIX + "instructor1"), exercise, TEST_PREFIX + "team");
        var teamParticipation = new StudentParticipation();
        teamParticipation.setParticipant(team);
        teamParticipation.exercise(exercise);
        studentParticipationRepo.save(teamParticipation);

        var submission = new TextSubmission();
        submission.text("text of a team submission");
        submission.setLanguage(Language.ENGLISH);
        submission.setSubmitted(true);
        submission.setParticipation(teamParticipation);
        submission.setSubmissionDate(date);
        submissionRepository.save(submission);

        var activeStudents = courseStatsService.getActiveStudents(new HashSet<>(Set.of(exercise.getId())), 0, 4, date);

        assertThat(activeStudents).hasSize(4).containsExactly(0, 0, 0, 0);
    }

    @Test
    void detailViewCombinesManualAssessmentProgressAndFeedbackWithIncludedScoresAndUsdCosts() {
        var fixture = new StatisticsFixture();
        Course course = statisticsCourse();
        Exercise regular = statisticsExercise(course, 101L, 40.0, IncludedInOverallScore.INCLUDED_COMPLETELY);
        Exercise bonus = statisticsExercise(course, 102L, 10.0, IncludedInOverallScore.INCLUDED_AS_BONUS);
        Exercise practice = statisticsExercise(course, 103L, 100.0, IncludedInOverallScore.NOT_INCLUDED);
        Set<Long> exerciseIds = Set.of(101L, 102L, 103L);
        Set<Long> manualExerciseIds = Set.of(101L, 102L);
        when(fixture.exercises.findAllExercisesByCourseId(course.getId())).thenReturn(Set.of(regular, bonus, practice));
        when(fixture.exercises.findExerciseIdsWithManualAssessmentByCourseId(course.getId())).thenReturn(manualExerciseIds);
        when(fixture.scores.findAvgRatedScore(Set.of(regular, bonus))).thenReturn(62.345);
        when(fixture.results.countNumberOfAssessments(manualExerciseIds)).thenReturn(3L);
        when(fixture.submissions.countAllByExerciseIdsSubmittedBeforeDueDate(manualExerciseIds)).thenReturn(2L);
        when(fixture.programming.countAllSubmissionsByExerciseIdsSubmitted(manualExerciseIds)).thenReturn(1L);
        when(fixture.submissions.countAllByExerciseIdsSubmittedAfterDueDate(manualExerciseIds)).thenReturn(1L);
        when(fixture.complaints.countByExerciseIdsAndComplaintType(exerciseIds, ComplaintType.COMPLAINT)).thenReturn(3L);
        when(fixture.responses.countComplaintResponsesForExerciseIdsAndComplaintType(exerciseIds, ComplaintType.COMPLAINT)).thenReturn(2L);
        when(fixture.complaints.countByExerciseIdsAndComplaintType(exerciseIds, ComplaintType.MORE_FEEDBACK)).thenReturn(4L);
        when(fixture.responses.countComplaintResponsesForExerciseIdsAndComplaintType(exerciseIds, ComplaintType.MORE_FEEDBACK)).thenReturn(1L);
        when(fixture.costs.calculateTotalLlmCostInUsdForCourse(course.getId())).thenReturn(12.345678);
        when(fixture.costs.countUnpricedRequestsForCourse(course.getId())).thenReturn(2L);

        var stats = fixture.service.getStatsForDetailView(course, null);

        assertThat(stats.currentAbsoluteAssessments()).isEqualTo(3L);
        assertThat(stats.currentMaxAssessments()).as("manual programming submissions and late submissions share the assessment denominator").isEqualTo(4L);
        assertThat(stats.currentPercentageAssessments()).isEqualTo(75.0);
        assertThat(stats.currentAbsoluteComplaints()).isEqualTo(2L);
        assertThat(stats.currentMaxComplaints()).isEqualTo(3L);
        assertThat(stats.currentPercentageComplaints()).isEqualTo(66.7);
        assertThat(stats.currentAbsoluteMoreFeedbacks()).isEqualTo(1L);
        assertThat(stats.currentMaxMoreFeedbacks()).isEqualTo(4L);
        assertThat(stats.currentPercentageMoreFeedbacks()).isEqualTo(25.0);
        assertThat(stats.currentMaxAverageScore()).as("practice exercises do not inflate the score").isEqualTo(50.0);
        assertThat(stats.currentPercentageAverageScore()).isEqualTo(62.35);
        assertThat(stats.currentAbsoluteAverageScore()).isEqualTo(31.17);
        assertThat(stats.currentTotalLlmCostInUsd()).as("costs keep their precision independently of course score rounding").isEqualTo(12.345678);
        assertThat(stats.unpricedLlmRequestCount()).isEqualTo(2L);
        verify(fixture.scores).findAvgRatedScore(Set.of(regular, bonus));
    }

    @Test
    void detailViewIncludesGradedPresentationsWithoutUsingBonusPointsAsTheirBase() {
        var fixture = new StatisticsFixture();
        Course course = statisticsCourse();
        Exercise regular = statisticsExercise(course, 101L, 40.0, IncludedInOverallScore.INCLUDED_COMPLETELY);
        Exercise bonus = statisticsExercise(course, 102L, 10.0, IncludedInOverallScore.INCLUDED_AS_BONUS);
        Set<Exercise> exercises = Set.of(regular, bonus);
        when(fixture.exercises.findAllExercisesByCourseId(course.getId())).thenReturn(exercises);
        when(fixture.scores.findAvgRatedScore(exercises)).thenReturn(50.0);
        GradingScale gradingScale = new GradingScale();
        gradingScale.setCourse(course);
        gradingScale.setPresentationsNumber(2);
        gradingScale.setPresentationsWeight(20.0);
        when(fixture.presentations.calculateReachablePresentationPoints(gradingScale, 40.0)).thenReturn(10.0);
        when(fixture.participations.getAvgPresentationScoreByCourseId(course.getId())).thenReturn(80.0);

        var stats = fixture.service.getStatsForDetailView(course, gradingScale);

        assertThat(stats.currentMaxAverageScore()).isEqualTo(60.0);
        assertThat(stats.currentPercentageAverageScore()).isEqualTo(56.0);
        assertThat(stats.currentAbsoluteAverageScore()).isEqualTo(33.6);
        verify(fixture.presentations).calculateReachablePresentationPoints(gradingScale, 40.0);
    }

    @Test
    void detailViewWithOnlyPracticeAndNoSubmissionsOrFeedbackHasFiniteZeroProgressAndUnpricedCosts() {
        var fixture = new StatisticsFixture();
        Course course = statisticsCourse();
        Exercise practice = statisticsExercise(course, 101L, 100.0, IncludedInOverallScore.NOT_INCLUDED);
        when(fixture.exercises.findAllExercisesByCourseId(course.getId())).thenReturn(Set.of(practice));
        when(fixture.exercises.findExerciseIdsWithManualAssessmentByCourseId(course.getId())).thenReturn(Set.of(101L));
        when(fixture.scores.findAvgRatedScore(Set.of())).thenReturn(null);
        when(fixture.costs.countUnpricedRequestsForCourse(course.getId())).thenReturn(3L);

        var stats = fixture.service.getStatsForDetailView(course, null);

        assertThat(stats).isEqualTo(new CourseManagementDetailViewDTO(0.0, 0L, 0L, 0.0, 0L, 0L, 0.0, 0L, 0L, 0.0, 0.0, 0.0, 0.0, 3L));
        verify(fixture.scores).findAvgRatedScore(Set.of());
    }

    @Test
    void assessmentDashboardKeepsManualSubmissionTotalsAndSeparatesOpenRequestsAndPersonalLocks() {
        var fixture = new StatisticsFixture();
        Course course = statisticsCourse();
        Set<Long> exerciseIds = Set.of(101L, 102L, 103L);
        Set<Long> manualExerciseIds = Set.of(101L, 102L);
        when(fixture.exercises.findExerciseIdsByCourseId(course.getId())).thenReturn(exerciseIds);
        when(fixture.exercises.findExerciseIdsWithManualAssessmentByCourseId(course.getId())).thenReturn(manualExerciseIds);
        when(fixture.submissions.countAllByExerciseIdsSubmittedBeforeDueDate(manualExerciseIds)).thenReturn(2L);
        when(fixture.programming.countAllSubmissionsByExerciseIdsSubmitted(manualExerciseIds)).thenReturn(1L);
        when(fixture.submissions.countAllByExerciseIdsSubmittedAfterDueDate(manualExerciseIds)).thenReturn(1L);
        when(fixture.results.countNumberOfAssessments(manualExerciseIds)).thenReturn(3L);
        when(fixture.complaintService.countMoreFeedbackRequestsByExerciseIds(exerciseIds)).thenReturn(4L);
        when(fixture.complaintService.countMoreFeedbackRequestResponsesByExerciseIds(exerciseIds)).thenReturn(1L);
        when(fixture.complaintService.countComplaintsByExerciseIds(exerciseIds)).thenReturn(3L);
        when(fixture.complaintService.countComplaintResponsesByExerciseIds(exerciseIds)).thenReturn(2L);
        User tutor = new User();
        tutor.setId(7L);
        when(fixture.users.getUserWithAuthorities()).thenReturn(tutor);
        when(fixture.submissions.countLockedSubmissionsByUserIdAndCourseId(tutor.getId(), course.getId())).thenReturn(2L);
        when(fixture.submissions.countLockedSubmissionsByExerciseIds(exerciseIds)).thenReturn(5L);
        var leaderboardEntry = new TutorLeaderboardDTO(7L, "Tutor", 3L, 1L, 2L, 1L, 2L, 1L, 2L, 4.5, 75.0, 4.0, 2L);
        when(fixture.leaderboard.getCourseLeaderboard(course, manualExerciseIds)).thenReturn(List.of(leaderboardEntry));
        when(fixture.ratings.countByResultExerciseIds(exerciseIds)).thenReturn(2L);

        var stats = fixture.service.getStatsForDashboardDTO(course);

        assertThat(stats.getNumberOfSubmissions()).isEqualTo(new DueDateStat(3L, 1L));
        assertThat(stats.getTotalNumberOfAssessments()).isEqualTo(3L);
        assertThat(stats.getNumberOfMoreFeedbackRequests()).isEqualTo(4L);
        assertThat(stats.getNumberOfOpenMoreFeedbackRequests()).isEqualTo(3L);
        assertThat(stats.getNumberOfComplaints()).isEqualTo(3L);
        assertThat(stats.getNumberOfOpenComplaints()).isEqualTo(1L);
        assertThat(stats.getNumberOfAssessmentLocks()).isEqualTo(2L);
        assertThat(stats.getTotalNumberOfAssessmentLocks()).isEqualTo(5L);
        assertThat(stats.getTutorLeaderboardEntries()).containsExactly(leaderboardEntry);
        assertThat(stats.getNumberOfRatings()).isEqualTo(2L);
    }

    @Test
    void activeStudentWindowStopsAtCourseEndOrNow() {
        ZonedDateTime now = ZonedDateTime.parse("2026-03-30T10:00:00+02:00[Europe/Berlin]");
        TimeUtil.setClock(Clock.fixed(now.toInstant(), now.getZone()));
        try {
            Course course = statisticsCourse();
            course.setEndDate(now.minusDays(1));
            assertThat(courseStatsService.determineEndDateForActiveStudents(course)).isEqualTo(course.getEndDate());

            course.setEndDate(now.plusMonths(1));
            assertThat(courseStatsService.determineEndDateForActiveStudents(course)).isEqualTo(now);

            course.setEndDate(now);
            assertThat(courseStatsService.determineEndDateForActiveStudents(course)).isEqualTo(now);
        }
        finally {
            TimeUtil.resetClock();
        }
    }

    @Test
    void activeStudentWindowIncludesBoundaryWeeksNormalizesZonesAndCapsChartLength() {
        ZonedDateTime monday = ZonedDateTime.parse("2026-12-21T00:00:00+01:00[Europe/Berlin]");
        assertThat(courseStatsService.calculateWeeksBetweenDates(monday, monday.plusDays(6).plusHours(23).plusMinutes(59))).isEqualTo(1);
        assertThat(courseStatsService.calculateWeeksBetweenDates(monday, monday.plusWeeks(1))).isEqualTo(2);
        // Sunday in New York is already Monday in the chart's Berlin time zone.
        ZonedDateTime sameChartWeek = ZonedDateTime.parse("2026-12-27T18:30:00-05:00[America/New_York]");
        assertThat(courseStatsService.calculateWeeksBetweenDates(sameChartWeek, monday.plusWeeks(1).plusHours(12))).isEqualTo(1);

        Course course = statisticsCourse();
        course.setStartDate(monday);
        assertThat(courseStatsService.determineTimeSpanSizeForActiveStudents(course, monday.plusWeeks(2), 17)).isEqualTo(3);
        assertThat(courseStatsService.determineTimeSpanSizeForActiveStudents(course, monday.plusWeeks(2), 2)).isEqualTo(2);

        course.setStartDate(monday.plusWeeks(4));
        int futureCourseWindow = courseStatsService.determineTimeSpanSizeForActiveStudents(course, monday.plusWeeks(2), 17);
        assertThat(futureCourseWindow).isEqualTo(-1);
        assertThat(courseStatsService.getActiveStudents(Set.of(101L), 0, futureCourseWindow, monday.plusWeeks(2))).isEmpty();
    }

    @Test
    void previousActiveStudentPeriodUsesAWholeEarlierWindowAndEmptyCoursesHaveNoChart() {
        var fixture = new StatisticsFixture();
        ZonedDateTime now = ZonedDateTime.parse("2026-05-27T12:00:00Z");

        assertThat(fixture.service.getActiveStudents(Set.of(101L), -1, 4, now)).containsExactly(0, 0, 0, 0);
        verify(fixture.courses).getActiveStudents(Set.of(101L), ZonedDateTime.parse("2026-04-06T00:00:00Z"), ZonedDateTime.parse("2026-05-03T23:59:59Z"));
        assertThat(fixture.service.getActiveStudents(Set.of(), 0, 4, now)).isEmpty();
    }

    private static Course statisticsCourse() {
        Course course = new Course();
        course.setId(42L);
        course.setAccuracyOfScores(2);
        course.setMaxComplaintTimeDays(7);
        course.setMaxRequestMoreFeedbackTimeDays(7);
        return course;
    }

    private static Exercise statisticsExercise(Course course, long id, double maxPoints, IncludedInOverallScore included) {
        TextExercise exercise = new TextExercise();
        exercise.setId(id);
        exercise.setCourse(course);
        exercise.setMaxPoints(maxPoints);
        exercise.setIncludedInOverallScore(included);
        return exercise;
    }

    /** Real course and exercise values with controlled repository aggregates keep these arithmetic scenarios independent of asynchronous score updates. */
    private static final class StatisticsFixture {

        private final CourseTestRepository courses = mock(CourseTestRepository.class);

        private final UserTestRepository users = mock(UserTestRepository.class);

        private final ExerciseTestRepository exercises = mock(ExerciseTestRepository.class);

        private final ResultTestRepository results = mock(ResultTestRepository.class);

        private final SubmissionTestRepository submissions = mock(SubmissionTestRepository.class);

        private final PresentationPointsCalculationService presentations = mock(PresentationPointsCalculationService.class);

        private final ComplaintService complaintService = mock(ComplaintService.class);

        private final TutorLeaderboardService leaderboard = mock(TutorLeaderboardService.class);

        private final ParticipantScoreRepository scores = mock(ParticipantScoreRepository.class);

        private final StudentParticipationTestRepository participations = mock(StudentParticipationTestRepository.class);

        private final ComplaintRepository complaints = mock(ComplaintRepository.class);

        private final ComplaintResponseTestRepository responses = mock(ComplaintResponseTestRepository.class);

        private final ProgrammingExerciseTestRepository programming = mock(ProgrammingExerciseTestRepository.class);

        private final RatingRepository ratings = mock(RatingRepository.class);

        private final LLMTokenUsageTraceTestRepository costs = mock(LLMTokenUsageTraceTestRepository.class);

        private final CourseStatsService service = new CourseStatsService(courses, mock(StatisticsRepository.class), users, exercises, results, submissions, presentations,
                complaintService, leaderboard, scores, participations, complaints, responses, programming, ratings, costs);
    }
}
