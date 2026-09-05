package de.tum.cit.aet.artemis.core;

import static de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismStatus.CONFIRMED;
import static de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismStatus.NONE;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.admin.domain.CleanupJobExecution;
import de.tum.cit.aet.artemis.admin.domain.CleanupJobType;
import de.tum.cit.aet.artemis.admin.dto.CleanupServiceExecutionRecordDTO;
import de.tum.cit.aet.artemis.admin.dto.NonLatestNonRatedResultsCleanupCountDTO;
import de.tum.cit.aet.artemis.admin.dto.NonLatestRatedResultsCleanupCountDTO;
import de.tum.cit.aet.artemis.admin.dto.NotEnrolledUsersCleanupCountDTO;
import de.tum.cit.aet.artemis.admin.dto.OldCoursesCleanupCountDTO;
import de.tum.cit.aet.artemis.admin.dto.OldFeedbackCleanupCountDTO;
import de.tum.cit.aet.artemis.admin.dto.OrphanCleanupCountDTO;
import de.tum.cit.aet.artemis.admin.dto.PlagiarismCasesCleanupCountDTO;
import de.tum.cit.aet.artemis.admin.dto.PlagiarismComparisonCleanupCountDTO;
import de.tum.cit.aet.artemis.admin.dto.SubmissionVersionsCleanupCountDTO;
import de.tum.cit.aet.artemis.admin.repository.CleanupJobExecutionRepository;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.LongFeedbackText;
import de.tum.cit.aet.artemis.assessment.domain.Rating;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.domain.StudentScore;
import de.tum.cit.aet.artemis.assessment.domain.TeamScore;
import de.tum.cit.aet.artemis.assessment.repository.FeedbackRepository;
import de.tum.cit.aet.artemis.assessment.repository.LongFeedbackTextRepository;
import de.tum.cit.aet.artemis.assessment.repository.ParticipantScoreRepository;
import de.tum.cit.aet.artemis.assessment.repository.RatingRepository;
import de.tum.cit.aet.artemis.assessment.repository.StudentScoreRepository;
import de.tum.cit.aet.artemis.assessment.repository.TeamScoreRepository;
import de.tum.cit.aet.artemis.assessment.repository.TextBlockRepository;
import de.tum.cit.aet.artemis.assessment.test_repository.ResultTestRepository;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionVersion;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseTestRepository;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionVersionRepository;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.SubmissionTestRepository;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismComparison;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismMatch;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismResult;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismSubmission;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismComparisonRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationJenkinsLocalVCTest;
import de.tum.cit.aet.artemis.text.domain.TextBlock;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.repository.TextExerciseRepository;
import de.tum.cit.aet.artemis.text.util.TextExerciseFactory;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class CleanupIntegrationTest extends AbstractSpringIntegrationJenkinsLocalVCTest {

    private static final String TEST_PREFIX = "cleanup";

    private static final ZonedDateTime DELETE_FROM = ZonedDateTime.now().minusMonths(12);

    private static final ZonedDateTime DELETE_TO = ZonedDateTime.now().minusMonths(6);

    @Autowired
    private CourseTestRepository courseRepository;

    @Autowired
    private CleanupJobExecutionRepository cleanupJobExecutionRepository;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private LongFeedbackTextRepository longFeedbackTextRepository;

    @Autowired
    private StudentScoreRepository studentScoreRepository;

    @Autowired
    private TeamScoreRepository teamScoreRepository;

    @Autowired
    private RatingRepository ratingRepository;

    @Autowired
    private ResultTestRepository resultRepository;

    @Autowired
    private PlagiarismComparisonRepository plagiarismComparisonRepository;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private TextBlockRepository textBlockRepository;

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @Autowired
    private ExerciseTestRepository exerciseRepository;

    @Autowired
    private ParticipantScoreRepository participantScoreRepository;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private SubmissionTestRepository submissionRepository;

    @Autowired
    private SubmissionVersionRepository submissionVersionRepository;

    private Course oldCourse;

    private Course newCourse;

    private User student;

    private User instructor;

    @BeforeEach
    void initTestCase() {
        ZonedDateTime now = ZonedDateTime.now();

        oldCourse = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExercise(TEST_PREFIX);
        oldCourse.setStartDate(now.minusMonths(12).plusDays(2));
        oldCourse.setEndDate(now.minusMonths(6).minusDays(2));
        TextExercise finishedTextExercise1 = TextExerciseFactory.generateTextExercise(now.minusMonths(12).plusDays(2), now.minusMonths(12).plusDays(2).plusHours(12),
                now.minusMonths(12).plusDays(2).plusHours(24), oldCourse);
        finishedTextExercise1.setTitle("Finished");
        oldCourse.addExercises(finishedTextExercise1);
        oldCourse = courseRepository.save(oldCourse);
        exerciseRepository.save(finishedTextExercise1);

        newCourse = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExercise(TEST_PREFIX);
        newCourse.setStartDate(now);
        newCourse.setEndDate(now.plusMonths(6));
        TextExercise finishedTextExercise2 = TextExerciseFactory.generateTextExercise(now.minusMonths(12).plusDays(2), now.minusMonths(12).plusDays(2).plusHours(12),
                now.minusMonths(12).plusDays(2).plusHours(24), newCourse);
        finishedTextExercise2.setTitle("Finished");
        newCourse.addExercises(finishedTextExercise2);
        newCourse = courseRepository.save(newCourse);
        exerciseRepository.save(finishedTextExercise2);
        userUtilService.addUsers(TEST_PREFIX, 4, 0, 0, 1);
        // The admin endpoints resolve the authenticated login against the database, so the account the tests
        // authenticate as has to exist there with the admin authority rather than only in the mock security context.
        userUtilService.addAdmin(TEST_PREFIX);
        student = userUtilService.getUserByLogin(TEST_PREFIX + "student4");
        instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testDeleteOrphans() throws Exception {
        var oldExercise = textExerciseRepository.findByCourseIdWithCategories(oldCourse.getId()).getFirst();

        var orphanFeedback = createFeedbackWithLinkedLongFeedback();
        var orphanTextBlock = createTextBlockForFeedback(orphanFeedback);

        StudentScore orphanStudentScore = new StudentScore();
        orphanStudentScore.setExercise(oldExercise);
        orphanStudentScore = studentScoreRepository.save(orphanStudentScore);

        TeamScore orphanTeamScore = new TeamScore();
        orphanTeamScore.setExercise(oldExercise);
        orphanTeamScore = teamScoreRepository.save(orphanTeamScore);

        // A result always belongs to a submission, so an orphan result is one whose submission has no participation.
        var submissionWithoutParticipation = submissionRepository.save(new TextSubmission());
        var orphanResult = new Result();
        orphanResult.setExerciseId(oldExercise.getId());
        orphanResult.setSubmission(submissionWithoutParticipation);
        orphanResult = resultRepository.save(orphanResult);

        orphanFeedback.setResult(orphanResult);
        orphanFeedback = feedbackRepository.save(orphanFeedback);

        var submission = participationUtilService.addSubmission(textExerciseRepository.findByCourseIdWithCategories(newCourse.getId()).getFirst(), new ProgrammingSubmission(),
                student.getLogin());

        var nonOrphanFeedback = createFeedbackWithLinkedLongFeedback();
        var nonOrphanTextBlock = createTextBlockForFeedback(nonOrphanFeedback);

        Result nonOrphanResult = new Result();
        nonOrphanResult.setSubmission(submission);
        nonOrphanResult.setExerciseId(submission.getParticipation().getExercise().getId());
        nonOrphanFeedback.setResult(nonOrphanResult);
        nonOrphanResult = resultRepository.save(nonOrphanResult);

        nonOrphanFeedback.setResult(nonOrphanResult);
        nonOrphanFeedback = feedbackRepository.save(nonOrphanFeedback);

        StudentScore nonOrphanStudentScore = new StudentScore();
        nonOrphanStudentScore.setUser(student);
        nonOrphanStudentScore.setExercise(oldExercise);
        nonOrphanStudentScore = studentScoreRepository.save(nonOrphanStudentScore);

        TeamScore nonOrphanTeamScore = new TeamScore();
        nonOrphanTeamScore.setExercise(oldExercise);
        Team team = new Team();
        team.setShortName("team");
        team.setExercise(oldExercise);
        nonOrphanTeamScore.setTeam(team);
        teamRepository.save(team);
        nonOrphanTeamScore = teamScoreRepository.save(nonOrphanTeamScore);

        Rating nonOrphanRating = new Rating();
        nonOrphanRating.setResult(nonOrphanResult);
        nonOrphanRating = ratingRepository.save(nonOrphanRating);

        Rating orphanRating = new Rating();
        orphanRating.setResult(orphanResult);
        orphanRating = ratingRepository.save(orphanRating);

        var counts = request.get("/api/admin/cleanup/orphans/count", HttpStatus.OK, OrphanCleanupCountDTO.class);

        assertThat(counts).isNotNull();
        assertThat(counts.orphanFeedback()).isEqualTo(0);
        assertThat(counts.orphanLongFeedbackText()).isEqualTo(0);
        assertThat(counts.orphanTextBlock()).isEqualTo(0);
        assertThat(counts.orphanStudentScore()).isEqualTo(1);
        assertThat(counts.orphanTeamScore()).isEqualTo(1);
        assertThat(counts.orphanFeedbackForOrphanResults()).isEqualTo(1);
        assertThat(counts.orphanLongFeedbackTextForOrphanResults()).isEqualTo(1);
        assertThat(counts.orphanTextBlockForOrphanResults()).isEqualTo(1);
        assertThat(counts.orphanRating()).isEqualTo(1);
        assertThat(counts.orphanResultsWithoutParticipation()).isEqualTo(1);
        // no unreferenced message is old enough to be collected yet, see the grace period
        assertThat(counts.orphanFeedbackMessage()).isZero();

        var responseBody = request.delete("/api/admin/cleanup/orphans", new LinkedMultiValueMap<>(), null, CleanupServiceExecutionRecordDTO.class, HttpStatus.OK);

        assertThat(responseBody.jobType()).isEqualTo("deleteOrphans");
        assertThat(responseBody.executionDate()).isNotNull();

        assertThat(longFeedbackTextRepository.existsById(orphanFeedback.getLongFeedback().orElseThrow().getId())).isFalse();
        assertThat(textBlockRepository.existsById(orphanTextBlock.getId())).isFalse();
        assertThat(feedbackRepository.existsById(orphanFeedback.getId())).isFalse();
        assertThat(studentScoreRepository.existsById(orphanStudentScore.getId())).isFalse();
        assertThat(teamScoreRepository.existsById(orphanTeamScore.getId())).isFalse();
        assertThat(resultRepository.existsById(orphanResult.getId())).isFalse();
        assertThat(ratingRepository.existsById(orphanRating.getId())).isFalse();

        assertThat(textBlockRepository.existsById(nonOrphanTextBlock.getId())).isTrue();
        assertThat(longFeedbackTextRepository.existsById(nonOrphanFeedback.getLongFeedback().orElseThrow().getId())).isTrue();
        assertThat(feedbackRepository.existsById(nonOrphanFeedback.getId())).isTrue();
        assertThat(studentScoreRepository.existsById(nonOrphanStudentScore.getId())).isTrue();
        assertThat(teamScoreRepository.existsById(nonOrphanTeamScore.getId())).isTrue();
        assertThat(ratingRepository.existsById(nonOrphanRating.getId())).isTrue();
        assertThat(resultRepository.existsById(nonOrphanResult.getId())).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testDeletePlagiarismComparisons() throws Exception {
        // old course, should delete undecided plagiarism comparisons
        var textExercise1 = textExerciseRepository.findByCourseIdWithCategories(oldCourse.getId()).getFirst();
        var textPlagiarismResult1 = textExerciseUtilService.createPlagiarismResultForExercise(textExercise1);

        var submission1 = participationUtilService.addSubmission(textExercise1, ParticipationFactory.generateTextSubmission("", Language.GERMAN, true), TEST_PREFIX + "student1");
        var submission2 = participationUtilService.addSubmission(textExercise1, ParticipationFactory.generateTextSubmission("", Language.GERMAN, true), TEST_PREFIX + "student2");
        var submission3 = participationUtilService.addSubmission(textExercise1, ParticipationFactory.generateTextSubmission("", Language.GERMAN, true), TEST_PREFIX + "student3");
        var plagiarismComparison1 = getPlagiarismSubmissionElementPlagiarismComparison(textPlagiarismResult1, submission1, submission2);
        plagiarismComparison1 = plagiarismComparisonRepository.save(plagiarismComparison1);

        var plagiarismComparison2 = getSubmissionElementPlagiarismComparison(textPlagiarismResult1, submission2, submission3);
        plagiarismComparison2 = plagiarismComparisonRepository.save(plagiarismComparison2);

        // new course, should not delete undecided plagiarism comparisons
        var textExercise2 = textExerciseRepository.findByCourseIdWithCategories(newCourse.getId()).getFirst();
        var textPlagiarismResult2 = textExerciseUtilService.createPlagiarismResultForExercise(textExercise2);

        var submission4 = participationUtilService.addSubmission(textExercise2, ParticipationFactory.generateTextSubmission("", Language.GERMAN, true), TEST_PREFIX + "student1");
        var submission5 = participationUtilService.addSubmission(textExercise2, ParticipationFactory.generateTextSubmission("", Language.GERMAN, true), TEST_PREFIX + "student2");
        var submission6 = participationUtilService.addSubmission(textExercise2, ParticipationFactory.generateTextSubmission("", Language.GERMAN, true), TEST_PREFIX + "student3");
        var plagiarismComparison3 = getPlagiarismSubmissionElementPlagiarismComparison(textPlagiarismResult2, submission4, submission5);
        plagiarismComparison3 = plagiarismComparisonRepository.save(plagiarismComparison3);

        var plagiarismComparison4 = getSubmissionElementPlagiarismComparison(textPlagiarismResult2, submission2, submission6);
        plagiarismComparison4 = plagiarismComparisonRepository.save(plagiarismComparison4);

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("deleteFrom", DELETE_FROM.toString());
        params.add("deleteTo", DELETE_TO.toString());

        var counts = request.get("/api/admin/cleanup/plagiarism-comparisons/count", HttpStatus.OK, PlagiarismComparisonCleanupCountDTO.class, params);

        assertThat(counts).isNotNull();
        assertThat(counts.plagiarismComparison()).isEqualTo(1);
        assertThat(counts.plagiarismElements()).isEqualTo(0);
        assertThat(counts.plagiarismMatches()).isEqualTo(1);
        assertThat(counts.plagiarismSubmissions()).isEqualTo(2);

        var responseBody = request.delete("/api/admin/cleanup/plagiarism-comparisons", params, null, CleanupServiceExecutionRecordDTO.class, HttpStatus.OK);

        assertThat(responseBody.jobType()).isEqualTo("deletePlagiarismComparisons");
        assertThat(responseBody.executionDate()).isNotNull();

        assertThat(plagiarismComparisonRepository.existsById(plagiarismComparison2.getId())).isFalse();
        assertThat(plagiarismComparisonRepository.existsById(plagiarismComparison1.getId())).isTrue();

        assertThat(plagiarismComparisonRepository.existsById(plagiarismComparison4.getId())).isTrue();
        assertThat(plagiarismComparisonRepository.existsById(plagiarismComparison3.getId())).isTrue();

    }

    @NonNull
    private static PlagiarismComparison getSubmissionElementPlagiarismComparison(PlagiarismResult textPlagiarismResult1, Submission submission2, Submission submission3) {
        PlagiarismComparison plagiarismComparison2 = new PlagiarismComparison();
        plagiarismComparison2.setPlagiarismResult(textPlagiarismResult1);
        plagiarismComparison2.setStatus(NONE);
        var plagiarismSubmissionA2 = new PlagiarismSubmission();
        plagiarismSubmissionA2.setStudentLogin(TEST_PREFIX + "student2");
        plagiarismSubmissionA2.setSubmissionId(submission2.getId());
        var plagiarismSubmissionB2 = new PlagiarismSubmission();
        plagiarismSubmissionB2.setStudentLogin(TEST_PREFIX + "student3");
        plagiarismSubmissionB2.setSubmissionId(submission3.getId());
        plagiarismComparison2.setSubmissionA(plagiarismSubmissionA2);
        plagiarismComparison2.setSubmissionB(plagiarismSubmissionB2);
        plagiarismComparison2.setMatches(Set.of(new PlagiarismMatch()));
        return plagiarismComparison2;
    }

    @NonNull
    private static PlagiarismComparison getPlagiarismSubmissionElementPlagiarismComparison(PlagiarismResult textPlagiarismResult1, Submission submission1, Submission submission2) {
        PlagiarismComparison plagiarismComparison1 = new PlagiarismComparison();
        plagiarismComparison1.setPlagiarismResult(textPlagiarismResult1);
        plagiarismComparison1.setStatus(CONFIRMED);
        var plagiarismSubmissionA1 = new PlagiarismSubmission();
        plagiarismSubmissionA1.setStudentLogin(TEST_PREFIX + "student1");
        plagiarismSubmissionA1.setSubmissionId(submission1.getId());
        var plagiarismSubmissionB1 = new PlagiarismSubmission();
        plagiarismSubmissionB1.setStudentLogin(TEST_PREFIX + "student2");
        plagiarismSubmissionB1.setSubmissionId(submission2.getId());
        plagiarismComparison1.setSubmissionA(plagiarismSubmissionA1);
        plagiarismComparison1.setSubmissionB(plagiarismSubmissionB1);
        plagiarismComparison1.setMatches(Set.of(new PlagiarismMatch()));
        return plagiarismComparison1;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testDeleteNonRatedResults() throws Exception {
        // create non rated results for an old course
        var oldExercise = textExerciseRepository.findByCourseIdWithCategories(oldCourse.getId()).getFirst();
        var oldStudentParticipation = participationUtilService.createAndSaveParticipationForExercise(oldExercise, student.getLogin());
        var oldSubmission = participationUtilService.addSubmission(oldStudentParticipation, ParticipationFactory.generateProgrammingSubmission(true));
        var oldResult1 = participationUtilService.generateResult(oldSubmission, instructor);
        oldResult1.setRated(false);
        var oldResult2 = participationUtilService.generateResult(oldSubmission, instructor);
        oldResult2.setRated(false);

        var oldFeedback1 = createFeedbackWithLinkedLongFeedback();
        var oldTextBlock1 = createTextBlockForFeedback(oldFeedback1);
        participationUtilService.addFeedbackToResult(oldFeedback1, oldResult1);

        var oldFeedback2 = createFeedbackWithLinkedLongFeedback();
        var oldTextBlock2 = createTextBlockForFeedback(oldFeedback2);
        participationUtilService.addFeedbackToResult(oldFeedback2, oldResult2);

        StudentScore oldParticipantScore1 = new StudentScore();
        oldParticipantScore1.setExercise(oldExercise);
        oldParticipantScore1.setUser(student);
        oldParticipantScore1.setLastRatedResult(oldResult1);
        studentScoreRepository.save(oldParticipantScore1);

        StudentScore oldParticipantScore2 = new StudentScore();
        oldParticipantScore2.setUser(student);
        oldParticipantScore2.setExercise(oldExercise);
        oldParticipantScore2.setLastResult(oldResult2);
        studentScoreRepository.save(oldParticipantScore2);

        // create non rated results for the new course
        var newExercise = textExerciseRepository.findByCourseIdWithCategories(newCourse.getId()).getFirst();
        var newStudentParticipation = participationUtilService.createAndSaveParticipationForExercise(newExercise, student.getLogin());
        var newSubmission = participationUtilService.addSubmission(newStudentParticipation, ParticipationFactory.generateProgrammingSubmission(true));
        var newResult1 = participationUtilService.generateResult(newSubmission, instructor);
        newResult1.setRated(false);
        var newResult2 = participationUtilService.generateResult(newSubmission, instructor);
        newResult2.setRated(false);

        var newFeedback1 = createFeedbackWithLinkedLongFeedback();
        var newTextBlock1 = createTextBlockForFeedback(newFeedback1);
        participationUtilService.addFeedbackToResult(newFeedback1, newResult1);

        var newFeedback2 = createFeedbackWithLinkedLongFeedback();
        var newTextBlock2 = createTextBlockForFeedback(newFeedback2);
        participationUtilService.addFeedbackToResult(newFeedback2, newResult2);

        StudentScore newParticipantScore1 = new StudentScore();
        newParticipantScore1.setUser(student);
        newParticipantScore1.setExercise(newExercise);
        newParticipantScore1.setLastRatedResult(newResult1);
        newParticipantScore1 = studentScoreRepository.save(newParticipantScore1);

        StudentScore newParticipantScore2 = new StudentScore();
        newParticipantScore2.setUser(student);
        newParticipantScore2.setExercise(newExercise);
        newParticipantScore2.setLastResult(newResult2);
        newParticipantScore2 = studentScoreRepository.save(newParticipantScore2);

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("deleteFrom", DELETE_FROM.toString());
        params.add("deleteTo", DELETE_TO.toString());

        var counts = request.get("/api/admin/cleanup/non-rated-results/count", HttpStatus.OK, NonLatestNonRatedResultsCleanupCountDTO.class, params);

        assertThat(counts).isNotNull();
        assertThat(counts.longFeedbackText()).isEqualTo(1);
        assertThat(counts.textBlock()).isEqualTo(1);
        assertThat(counts.feedback()).isEqualTo(1);

        var responseBody = request.delete("/api/admin/cleanup/non-rated-results", params, null, CleanupServiceExecutionRecordDTO.class, HttpStatus.OK);

        assertThat(responseBody.jobType()).isEqualTo("deleteNonRatedResults");
        assertThat(responseBody.executionDate()).isNotNull();

        assertThat(feedbackRepository.findByResult(oldResult1)).isEmpty();
        assertThat(textBlockRepository.findById(oldTextBlock1.getId())).isEmpty();

        assertThat(resultRepository.findById(oldResult2.getId())).isNotEmpty();
        assertThat(feedbackRepository.findByResult(oldResult2)).isNotEmpty();
        assertThat(textBlockRepository.findById(oldTextBlock2.getId())).isNotEmpty();

        assertThat(participantScoreRepository.findById(newParticipantScore1.getId())).isNotEmpty();
        assertThat(participantScoreRepository.findById(newParticipantScore2.getId())).isNotEmpty();
        assertThat(resultRepository.findById(newResult1.getId())).isPresent();
        assertThat(feedbackRepository.findByResult(newResult1)).isNotEmpty();
        assertThat(textBlockRepository.findById(newTextBlock1.getId())).isNotEmpty();

        assertThat(resultRepository.findById(newResult2.getId())).isNotEmpty();
        assertThat(feedbackRepository.findByResult(newResult2)).isNotEmpty();
        assertThat(textBlockRepository.findById(newTextBlock2.getId())).isNotEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testDeleteOldRatedResults() throws Exception {
        // create rated results for an old course
        var oldExercise = textExerciseRepository.findByCourseIdWithCategories(oldCourse.getId()).getFirst();
        var oldStudentParticipation = participationUtilService.createAndSaveParticipationForExercise(oldExercise, student.getLogin());
        var oldSubmission = participationUtilService.addSubmission(oldStudentParticipation, ParticipationFactory.generateProgrammingSubmission(true));
        var oldResult1 = participationUtilService.generateResult(oldSubmission, instructor); // should be deleted, with all associated entities
        var oldResult2 = participationUtilService.generateResult(oldSubmission, instructor);

        var oldFeedback1 = createFeedbackWithLinkedLongFeedback();
        var oldTextBlock1 = createTextBlockForFeedback(oldFeedback1);
        participationUtilService.addFeedbackToResult(oldFeedback1, oldResult1);

        var oldFeedback2 = createFeedbackWithLinkedLongFeedback();
        var oldTextBlock2 = createTextBlockForFeedback(oldFeedback2);
        participationUtilService.addFeedbackToResult(oldFeedback2, oldResult2);

        StudentScore oldParticipantScore1 = new StudentScore();
        oldParticipantScore1.setUser(student);
        oldParticipantScore1.setExercise(oldExercise);
        oldParticipantScore1.setLastRatedResult(oldResult1);
        studentScoreRepository.save(oldParticipantScore1);

        StudentScore oldParticipantScore2 = new StudentScore();
        oldParticipantScore2.setExercise(oldExercise);
        oldParticipantScore2.setLastResult(oldResult2);
        oldParticipantScore2.setUser(student);
        oldParticipantScore2 = studentScoreRepository.save(oldParticipantScore2);

        // create rated results for the new course
        var newExercise = textExerciseRepository.findByCourseIdWithCategories(newCourse.getId()).getFirst();
        var newStudentParticipation = participationUtilService.createAndSaveParticipationForExercise(newExercise, student.getLogin());
        var newSubmission = participationUtilService.addSubmission(newStudentParticipation, ParticipationFactory.generateProgrammingSubmission(true));
        var newResult1 = participationUtilService.generateResult(newSubmission, instructor); // should not be deleted, with all associated entities
        var newResult2 = participationUtilService.generateResult(newSubmission, instructor);

        var newFeedback1 = createFeedbackWithLinkedLongFeedback();
        var newTextBlock1 = createTextBlockForFeedback(newFeedback1);
        participationUtilService.addFeedbackToResult(newFeedback1, newResult1);

        var newFeedback2 = createFeedbackWithLinkedLongFeedback();
        var newTextBlock2 = createTextBlockForFeedback(newFeedback2);
        participationUtilService.addFeedbackToResult(newFeedback2, newResult2);

        StudentScore newParticipantScore1 = new StudentScore();
        newParticipantScore1.setUser(student);
        newParticipantScore1.setExercise(newExercise);
        newParticipantScore1.setLastRatedResult(newResult1);
        newParticipantScore1 = studentScoreRepository.save(newParticipantScore1);

        StudentScore newParticipantScore2 = new StudentScore();
        newParticipantScore2.setUser(student);
        newParticipantScore2.setExercise(newExercise);
        newParticipantScore2.setLastResult(newResult2);
        newParticipantScore2 = studentScoreRepository.save(newParticipantScore2);

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("deleteFrom", DELETE_FROM.toString());
        params.add("deleteTo", DELETE_TO.toString());

        var counts = request.get("/api/admin/cleanup/old-rated-results/count", HttpStatus.OK, NonLatestRatedResultsCleanupCountDTO.class, params);

        assertThat(counts).isNotNull();
        assertThat(counts.longFeedbackText()).isEqualTo(1);
        assertThat(counts.textBlock()).isEqualTo(1);
        assertThat(counts.feedback()).isEqualTo(1);

        var responseBody = request.delete("/api/admin/cleanup/old-rated-results", params, null, CleanupServiceExecutionRecordDTO.class, HttpStatus.OK);

        assertThat(responseBody.jobType()).isEqualTo("deleteRatedResults");
        assertThat(responseBody.executionDate()).isNotNull();

        // assertThat(participantScoreRepository.findById(oldParticipantScore1.getId())).isEmpty();
        assertThat(participantScoreRepository.findById(oldParticipantScore2.getId())).isPresent();
        // assertThat(resultRepository.findById(oldResult1.getId())).isEmpty();
        assertThat(feedbackRepository.findByResult(oldResult1)).isEmpty();
        assertThat(textBlockRepository.findById(oldTextBlock1.getId())).isEmpty();

        assertThat(resultRepository.findById(oldResult2.getId())).isPresent();
        assertThat(feedbackRepository.findByResult(oldResult2)).isNotEmpty();
        assertThat(textBlockRepository.findById(oldTextBlock2.getId())).isNotEmpty();

        assertThat(participantScoreRepository.findById(newParticipantScore1.getId())).isPresent();
        assertThat(participantScoreRepository.findById(newParticipantScore2.getId())).isPresent();
        assertThat(resultRepository.findById(newResult1.getId())).isPresent();
        assertThat(feedbackRepository.findByResult(newResult1)).isNotEmpty();
        assertThat(textBlockRepository.findById(newTextBlock1.getId())).isNotEmpty();

        assertThat(resultRepository.findById(newResult2.getId())).isPresent();
        assertThat(feedbackRepository.findByResult(newResult2)).isNotEmpty();
        assertThat(textBlockRepository.findById(newTextBlock2.getId())).isNotEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testDeleteOldSubmissionVersions() throws Exception {

        TextSubmission submission = ParticipationFactory.generateTextSubmission("submissionText", Language.ENGLISH, true);
        submission = submissionRepository.save(submission);
        SubmissionVersion submissionVersion1 = ParticipationFactory.generateSubmissionVersion("test1", submission, student);
        submissionVersion1 = submissionVersionRepository.save(submissionVersion1);
        SubmissionVersion submissionVersion2 = ParticipationFactory.generateSubmissionVersion("test2", submission, student);
        submissionVersion2 = submissionVersionRepository.save(submissionVersion2);
        SubmissionVersion submissionVersion3 = ParticipationFactory.generateSubmissionVersion("test2", submission, student);
        submissionVersion3 = submissionVersionRepository.save(submissionVersion3);

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("deleteFrom", ZonedDateTime.now().minusMonths(1).toString());
        params.add("deleteTo", ZonedDateTime.now().plusMonths(1).toString());

        var counts = request.get("/api/admin/cleanup/old-submission-versions/count", HttpStatus.OK, SubmissionVersionsCleanupCountDTO.class, params);

        assertThat(counts).isNotNull();
        assertThat(counts.submissionVersions()).isEqualTo(3);

        var responseBody = request.delete("/api/admin/cleanup/old-submission-versions", params, null, CleanupServiceExecutionRecordDTO.class, HttpStatus.OK);

        assertThat(responseBody.jobType()).isEqualTo("deleteSubmissionVersions");
        assertThat(responseBody.executionDate()).isNotNull();

        assertThat(submissionVersionRepository.findById(submissionVersion1.getId())).isEmpty();
        assertThat(submissionVersionRepository.findById(submissionVersion2.getId())).isEmpty();
        assertThat(submissionVersionRepository.findById(submissionVersion3.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testDeleteOldFeedbackKeepsLatestRatedAndNonRatedResultFeedback() throws Exception {
        // One participation on the (old) course with a mixed rated/non-rated result history in creation (id) order:
        // r1 non-rated, r2 rated, r3 non-rated (latest non-rated), r4 rated (latest rated AND overall newest id).
        // This is the case that motivated the FeedbackCleanupRepository r2.rated=FALSE fix: because r4 (rated) is the
        // overall newest result, a MAX(id)-over-all-results "keep" would wrongly delete the latest NON-rated result's
        // feedback (r3). The age-based cleanup must keep the latest rated (r4) AND the latest non-rated (r3) feedback.
        // The old-feedback count aggregates across all old courses, so capture the baseline and assert the delta this
        // test introduces, keeping it robust against data left over from other tests.
        int initialFeedbackCount = request.get("/api/admin/cleanup/old-feedback/count", HttpStatus.OK, OldFeedbackCleanupCountDTO.class).feedback();

        var oldExercise = textExerciseRepository.findByCourseIdWithCategories(oldCourse.getId()).getFirst();
        var participation = participationUtilService.createAndSaveParticipationForExercise(oldExercise, student.getLogin());
        var submission = participationUtilService.addSubmission(participation, ParticipationFactory.generateProgrammingSubmission(true));

        var nonLatestNonRated = participationUtilService.generateResult(submission, instructor);
        nonLatestNonRated.setRated(false);
        var feedbackNonLatestNonRated = createFeedbackWithLinkedLongFeedback();
        createTextBlockForFeedback(feedbackNonLatestNonRated);
        participationUtilService.addFeedbackToResult(feedbackNonLatestNonRated, nonLatestNonRated);

        var nonLatestRated = participationUtilService.generateResult(submission, instructor); // rated by default
        var feedbackNonLatestRated = createFeedbackWithLinkedLongFeedback();
        createTextBlockForFeedback(feedbackNonLatestRated);
        participationUtilService.addFeedbackToResult(feedbackNonLatestRated, nonLatestRated);

        var latestNonRated = participationUtilService.generateResult(submission, instructor);
        latestNonRated.setRated(false);
        var feedbackLatestNonRated = createFeedbackWithLinkedLongFeedback();
        createTextBlockForFeedback(feedbackLatestNonRated);
        participationUtilService.addFeedbackToResult(feedbackLatestNonRated, latestNonRated);

        var latestRated = participationUtilService.generateResult(submission, instructor); // rated, overall newest id
        var feedbackLatestRated = createFeedbackWithLinkedLongFeedback();
        createTextBlockForFeedback(feedbackLatestRated);
        participationUtilService.addFeedbackToResult(feedbackLatestRated, latestRated);

        // Only the two non-latest results' feedback (fb of nonLatestNonRated + nonLatestRated) should be added to the count.
        var counts = request.get("/api/admin/cleanup/old-feedback/count", HttpStatus.OK, OldFeedbackCleanupCountDTO.class);
        assertThat(counts).isNotNull();
        assertThat(counts.feedback()).isEqualTo(initialFeedbackCount + 2);

        var responseBody = request.delete("/api/admin/cleanup/old-feedback", new LinkedMultiValueMap<>(), null, CleanupServiceExecutionRecordDTO.class, HttpStatus.OK);
        assertThat(responseBody.jobType()).isEqualTo("deleteFeedback");
        assertThat(responseBody.executionDate()).isNotNull();

        // Non-latest results' feedback deleted
        assertThat(feedbackRepository.findByResult(nonLatestNonRated)).isEmpty();
        assertThat(feedbackRepository.findByResult(nonLatestRated)).isEmpty();
        // Latest non-rated (r3) AND latest rated (r4) feedback kept; r3 being kept proves the r2.rated=FALSE fix.
        assertThat(feedbackRepository.findByResult(latestNonRated)).isNotEmpty();
        assertThat(feedbackRepository.findByResult(latestRated)).isNotEmpty();
        // Results themselves are never deleted by this operation.
        assertThat(resultRepository.existsById(nonLatestNonRated.getId())).isTrue();
        assertThat(resultRepository.existsById(nonLatestRated.getId())).isTrue();
        assertThat(resultRepository.existsById(latestNonRated.getId())).isTrue();
        assertThat(resultRepository.existsById(latestRated.getId())).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testDeleteOldCourseSubmissionVersionsByCourseEndDate() throws Exception {
        // The automated cleanup deletes submission versions by the OWNING COURSE's end date (submission -> participation
        // -> exercise -> course), which is a different query than the createdDate-range endpoint covered by
        // testDeleteOldSubmissionVersions. A version whose course (oldCourse) ended well before the cutoff must be
        // deleted; a version whose course (newCourse) has not ended must be kept.
        // The count aggregates across all old courses, so capture the baseline before seeding and assert the delta this
        // test introduces (exactly the one old-course version), keeping it robust against versions left by other tests.
        int baseline = request.get("/api/admin/cleanup/old-course-submission-versions/count", HttpStatus.OK, SubmissionVersionsCleanupCountDTO.class).submissionVersions();

        var oldExercise = textExerciseRepository.findByCourseIdWithCategories(oldCourse.getId()).getFirst();
        var oldParticipation = participationUtilService.createAndSaveParticipationForExercise(oldExercise, student.getLogin());
        var oldSubmission = participationUtilService.addSubmission(oldParticipation, ParticipationFactory.generateTextSubmission("old", Language.ENGLISH, true));
        var oldVersion = submissionVersionRepository.save(ParticipationFactory.generateSubmissionVersion("old-content", oldSubmission, student));

        var newExercise = textExerciseRepository.findByCourseIdWithCategories(newCourse.getId()).getFirst();
        var newParticipation = participationUtilService.createAndSaveParticipationForExercise(newExercise, student.getLogin());
        var newSubmission = participationUtilService.addSubmission(newParticipation, ParticipationFactory.generateTextSubmission("new", Language.ENGLISH, true));
        var newVersion = submissionVersionRepository.save(ParticipationFactory.generateSubmissionVersion("new-content", newSubmission, student));

        // Only the old-course version is counted; the not-yet-ended course's version must not be.
        int afterSeeding = request.get("/api/admin/cleanup/old-course-submission-versions/count", HttpStatus.OK, SubmissionVersionsCleanupCountDTO.class).submissionVersions();
        assertThat(afterSeeding).isEqualTo(baseline + 1);

        var responseBody = request.delete("/api/admin/cleanup/old-course-submission-versions", new LinkedMultiValueMap<>(), null, CleanupServiceExecutionRecordDTO.class,
                HttpStatus.OK);
        assertThat(responseBody.jobType()).isEqualTo(CleanupJobType.OLD_COURSE_SUBMISSION_VERSIONS.label());
        assertThat(responseBody.executionDate()).isNotNull();

        // Only the version of the course that already ended is deleted; the ongoing course's version survives.
        assertThat(submissionVersionRepository.findById(oldVersion.getId())).isEmpty();
        assertThat(submissionVersionRepository.findById(newVersion.getId())).isPresent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testDeleteOldFeedbackKeepsRecentCourseFeedback() throws Exception {
        // Cutoff-boundary guard: the age-based feedback cleanup must only touch courses that ended before the cutoff.
        // Feedback of a non-latest result of a course that has NOT yet ended (newCourse) must neither be counted nor
        // deleted, so ongoing/recent courses can never lose feedback by accident.
        int baseline = request.get("/api/admin/cleanup/old-feedback/count", HttpStatus.OK, OldFeedbackCleanupCountDTO.class).feedback();

        var newExercise = textExerciseRepository.findByCourseIdWithCategories(newCourse.getId()).getFirst();
        var participation = participationUtilService.createAndSaveParticipationForExercise(newExercise, student.getLogin());
        var submission = participationUtilService.addSubmission(participation, ParticipationFactory.generateProgrammingSubmission(true));
        // Two rated results: the first is non-latest (a deletion candidate for an OLD course), the second is latest.
        var nonLatestRated = participationUtilService.generateResult(submission, instructor);
        var feedbackNonLatest = createFeedbackWithLinkedLongFeedback();
        createTextBlockForFeedback(feedbackNonLatest);
        participationUtilService.addFeedbackToResult(feedbackNonLatest, nonLatestRated);
        var latestRated = participationUtilService.generateResult(submission, instructor);
        var feedbackLatest = createFeedbackWithLinkedLongFeedback();
        participationUtilService.addFeedbackToResult(feedbackLatest, latestRated);

        // The not-yet-ended course contributes nothing to the count, even though it has a non-latest result.
        int afterSeeding = request.get("/api/admin/cleanup/old-feedback/count", HttpStatus.OK, OldFeedbackCleanupCountDTO.class).feedback();
        assertThat(afterSeeding).isEqualTo(baseline);

        var responseBody = request.delete("/api/admin/cleanup/old-feedback", new LinkedMultiValueMap<>(), null, CleanupServiceExecutionRecordDTO.class, HttpStatus.OK);
        assertThat(responseBody.jobType()).isEqualTo(CleanupJobType.FEEDBACK.label());

        // The recent course's feedback (both non-latest and latest) survives the cleanup.
        assertThat(feedbackRepository.findByResult(nonLatestRated)).isNotEmpty();
        assertThat(feedbackRepository.findByResult(latestRated)).isNotEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testGetLastExecutions() throws Exception {

        var now = ZonedDateTime.now();

        var jobExecution = new CleanupJobExecution();
        jobExecution.setCleanupJobType(CleanupJobType.ORPHANS);
        jobExecution.setDeletionTimestamp(now);
        cleanupJobExecutionRepository.save(jobExecution);

        var response = request.getList("/api/admin/cleanup/last-executions", HttpStatus.OK, CleanupServiceExecutionRecordDTO.class);

        List<String> enumJobTypes = Arrays.stream(CleanupJobType.values()).map(CleanupJobType::label).toList();

        assertThat(response).isNotNull();
        assertThat(response).extracting(CleanupServiceExecutionRecordDTO::jobType).containsAll(enumJobTypes);

        var orphansJob = response.stream().filter(elem -> elem.jobType().equals(CleanupJobType.ORPHANS.label())).findFirst();

        assertThat(orphansJob).isPresent();
        assertThat(now).isNotNull();
    }

    @Test
    @WithMockUser(roles = "USER")
    void testUnauthorizedAccess() throws Exception {
        request.delete("/api/admin/cleanup/orphans", HttpStatus.FORBIDDEN, CleanupServiceExecutionRecordDTO.class);
        request.get("/api/admin/cleanup/orphans/count", HttpStatus.FORBIDDEN, OrphanCleanupCountDTO.class);
        request.delete("/api/admin/cleanup/plagiarism-comparisons", HttpStatus.FORBIDDEN, CleanupServiceExecutionRecordDTO.class);
        request.get("/api/admin/cleanup/plagiarism-comparisons/count", HttpStatus.FORBIDDEN, PlagiarismComparisonCleanupCountDTO.class);
        request.delete("/api/admin/cleanup/non-rated-results", HttpStatus.FORBIDDEN, CleanupServiceExecutionRecordDTO.class);
        request.get("/api/admin/cleanup/non-rated-results/count", HttpStatus.FORBIDDEN, NonLatestRatedResultsCleanupCountDTO.class);
        request.delete("/api/admin/cleanup/old-rated-results", HttpStatus.FORBIDDEN, CleanupServiceExecutionRecordDTO.class);
        request.get("/api/admin/cleanup/old-rated-results/count", HttpStatus.FORBIDDEN, NonLatestRatedResultsCleanupCountDTO.class);
        request.delete("/api/admin/cleanup/old-submission-versions", HttpStatus.FORBIDDEN, CleanupServiceExecutionRecordDTO.class);
        request.get("/api/admin/cleanup/old-submission-versions/count", HttpStatus.FORBIDDEN, SubmissionVersionsCleanupCountDTO.class);

        // new data-privacy cleanup endpoints
        request.postWithoutResponseBody("/api/admin/cleanup/old-courses/warn", null, HttpStatus.FORBIDDEN);
        request.get("/api/admin/cleanup/old-courses/warn/count", HttpStatus.FORBIDDEN, OldCoursesCleanupCountDTO.class);
        request.delete("/api/admin/cleanup/old-courses/reset", HttpStatus.FORBIDDEN, CleanupServiceExecutionRecordDTO.class);
        request.get("/api/admin/cleanup/old-courses/reset/count", HttpStatus.FORBIDDEN, OldCoursesCleanupCountDTO.class);
        request.delete("/api/admin/cleanup/old-feedback", HttpStatus.FORBIDDEN, CleanupServiceExecutionRecordDTO.class);
        request.get("/api/admin/cleanup/old-feedback/count", HttpStatus.FORBIDDEN, OldFeedbackCleanupCountDTO.class);
        request.delete("/api/admin/cleanup/old-course-submission-versions", HttpStatus.FORBIDDEN, CleanupServiceExecutionRecordDTO.class);
        request.get("/api/admin/cleanup/old-course-submission-versions/count", HttpStatus.FORBIDDEN, SubmissionVersionsCleanupCountDTO.class);
        request.postWithoutResponseBody("/api/admin/cleanup/not-enrolled-users/warn", null, HttpStatus.FORBIDDEN);
        request.get("/api/admin/cleanup/not-enrolled-users/warn/count", HttpStatus.FORBIDDEN, NotEnrolledUsersCleanupCountDTO.class);
        request.delete("/api/admin/cleanup/not-enrolled-users", HttpStatus.FORBIDDEN, CleanupServiceExecutionRecordDTO.class);
        request.get("/api/admin/cleanup/not-enrolled-users/count", HttpStatus.FORBIDDEN, NotEnrolledUsersCleanupCountDTO.class);
        request.delete("/api/admin/cleanup/plagiarism-cases", HttpStatus.FORBIDDEN, CleanupServiceExecutionRecordDTO.class);
        request.get("/api/admin/cleanup/plagiarism-cases/count", HttpStatus.FORBIDDEN, PlagiarismCasesCleanupCountDTO.class);

        request.get("/api/admin/cleanup/last-executions", HttpStatus.FORBIDDEN, List.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testCountNewDataPrivacyOperationsAsAdmin() throws Exception {
        // the count (preview) endpoints are read-only and must be wired, authorized for admins, and serialize correctly
        assertThat(request.get("/api/admin/cleanup/old-courses/warn/count", HttpStatus.OK, OldCoursesCleanupCountDTO.class)).isNotNull();
        assertThat(request.get("/api/admin/cleanup/old-courses/reset/count", HttpStatus.OK, OldCoursesCleanupCountDTO.class)).isNotNull();
        assertThat(request.get("/api/admin/cleanup/old-feedback/count", HttpStatus.OK, OldFeedbackCleanupCountDTO.class)).isNotNull();
        assertThat(request.get("/api/admin/cleanup/old-course-submission-versions/count", HttpStatus.OK, SubmissionVersionsCleanupCountDTO.class)).isNotNull();
        assertThat(request.get("/api/admin/cleanup/not-enrolled-users/warn/count", HttpStatus.OK, NotEnrolledUsersCleanupCountDTO.class)).isNotNull();
        assertThat(request.get("/api/admin/cleanup/not-enrolled-users/count", HttpStatus.OK, NotEnrolledUsersCleanupCountDTO.class)).isNotNull();
        assertThat(request.get("/api/admin/cleanup/plagiarism-cases/count", HttpStatus.OK, PlagiarismCasesCleanupCountDTO.class)).isNotNull();
    }

    private Feedback createFeedbackWithLinkedLongFeedback() {
        Feedback feedback = new Feedback();
        feedback = feedbackRepository.save(feedback);

        LongFeedbackText longFeedback = new LongFeedbackText();
        longFeedback.setFeedback(feedback);
        longFeedback.setText("text" + longFeedback.hashCode());
        longFeedbackTextRepository.save(longFeedback);

        feedback.setLongFeedbackText(Set.of(longFeedback));

        return feedbackRepository.save(feedback);
    }

    private TextBlock createTextBlockForFeedback(Feedback feedback) {
        TextBlock textBlock = new TextBlock();
        textBlock.setFeedback(feedback);
        // Use the persisted feedback id (not feedback.hashCode(), which is now a constant for HashSet stability)
        // so each TextBlock has a unique text → unique computeId() → no PK collisions on save.
        textBlock.setText("text" + feedback.getId());
        textBlock.computeId();
        return textBlockRepository.save(textBlock);
    }
}
