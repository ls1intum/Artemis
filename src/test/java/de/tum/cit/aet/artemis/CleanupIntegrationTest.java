package de.tum.cit.aet.artemis;

import static de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismStatus.CONFIRMED;
import static de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismStatus.NONE;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.LinkedMultiValueMap;

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
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.assessment.repository.StudentScoreRepository;
import de.tum.cit.aet.artemis.assessment.repository.TeamScoreRepository;
import de.tum.cit.aet.artemis.assessment.repository.TextBlockRepository;
import de.tum.cit.aet.artemis.core.domain.CleanupJobExecution;
import de.tum.cit.aet.artemis.core.domain.CleanupJobType;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.CleanupServiceExecutionRecordDTO;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.cleanup.CleanupJobExecutionRepository;
import de.tum.cit.aet.artemis.core.repository.cleanup.DataCleanupRepository;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.programming.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.exercise.text.TextExerciseFactory;
import de.tum.cit.aet.artemis.exercise.text.TextExerciseUtilService;
import de.tum.cit.aet.artemis.participation.ParticipationFactory;
import de.tum.cit.aet.artemis.participation.ParticipationUtilService;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismComparison;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismMatch;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismSubmission;
import de.tum.cit.aet.artemis.plagiarism.domain.text.TextSubmissionElement;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismComparisonRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.text.domain.TextBlock;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.repository.TextExerciseRepository;

class CleanupIntegrationTest extends AbstractSpringIntegrationJenkinsGitlabTest {

    private static final String TEST_PREFIX = "cleanup";

    private static final ZonedDateTime DELETE_FROM = ZonedDateTime.now().minusMonths(12);

    private static final ZonedDateTime DELETE_TO = ZonedDateTime.now().minusMonths(6);

    @Autowired
    private CourseRepository courseRepository;

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
    private ResultRepository resultRepository;

    @Autowired
    private PlagiarismComparisonRepository plagiarismComparisonRepository;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private TextBlockRepository textBlockRepository;

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private DataCleanupRepository dataCleanupRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private ParticipantScoreRepository participantScoreRepository;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private TeamRepository teamRepository;

    private Course oldCourse;

    private Course newCourse;

    private User student;

    private User instructor;

    @BeforeEach
    void initTestCase() {
        ZonedDateTime now = ZonedDateTime.now();

        oldCourse = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        oldCourse.setStartDate(now.minusMonths(12).plusDays(2));
        oldCourse.setEndDate(now.minusMonths(6).minusDays(2));
        TextExercise finishedTextExercise1 = TextExerciseFactory.generateTextExercise(now.minusMonths(12).plusDays(2), now.minusMonths(12).plusDays(2).plusHours(12),
                now.minusMonths(12).plusDays(2).plusHours(24), oldCourse);
        finishedTextExercise1.setTitle("Finished");
        oldCourse.addExercises(finishedTextExercise1);
        oldCourse = courseRepository.save(oldCourse);
        exerciseRepository.save(finishedTextExercise1);

        newCourse = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        newCourse.setStartDate(now);
        newCourse.setEndDate(now.plusMonths(6));
        TextExercise finishedTextExercise2 = TextExerciseFactory.generateTextExercise(now.minusMonths(12).plusDays(2), now.minusMonths(12).plusDays(2).plusHours(12),
                now.minusMonths(12).plusDays(2).plusHours(24), newCourse);
        finishedTextExercise2.setTitle("Finished");
        newCourse.addExercises(finishedTextExercise2);
        newCourse = courseRepository.save(newCourse);
        exerciseRepository.save(finishedTextExercise2);
        userUtilService.addUsers(TEST_PREFIX, 4, 0, 0, 1);
        student = userUtilService.getUserByLogin(TEST_PREFIX + "student4");
        instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
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

        var orphanResult = new Result();
        orphanResult = resultRepository.save(orphanResult);

        orphanFeedback.setResult(orphanResult);
        orphanFeedback = feedbackRepository.save(orphanFeedback);

        var submission = participationUtilService.addSubmission(textExerciseRepository.findByCourseIdWithCategories(newCourse.getId()).getFirst(), new ProgrammingSubmission(),
                student.getLogin());

        var nonOrphanFeedback = createFeedbackWithLinkedLongFeedback();
        var nonOrphanTextBlock = createTextBlockForFeedback(nonOrphanFeedback);

        Result nonOrphanResult = new Result();
        nonOrphanResult.setParticipation(submission.getParticipation());
        nonOrphanResult.setSubmission(submission);
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
        nonOrphanTeamScore.setTeam(team);
        teamRepository.save(team);
        nonOrphanTeamScore = teamScoreRepository.save(nonOrphanTeamScore);

        Rating nonOrphanRating = new Rating();
        nonOrphanRating.setResult(nonOrphanResult);
        nonOrphanRating = ratingRepository.save(nonOrphanRating);

        Rating orphanRating = new Rating();
        orphanRating.setResult(orphanResult);
        orphanRating = ratingRepository.save(orphanRating);

        var responseBody = request.postWithResponseBody("/api/admin/cleanup/delete-orphans", null, CleanupServiceExecutionRecordDTO.class, HttpStatus.OK, null, null,
                new LinkedMultiValueMap<>());

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
    @WithMockUser(roles = "ADMIN")
    void testDeletePlagiarismComparisons() throws Exception {
        // old course, should delete undecided plagiarism comparisons
        var textExercise1 = textExerciseRepository.findByCourseIdWithCategories(oldCourse.getId()).getFirst();
        var textPlagiarismResult1 = textExerciseUtilService.createTextPlagiarismResultForExercise(textExercise1);

        var submission1 = participationUtilService.addSubmission(textExercise1, ParticipationFactory.generateTextSubmission("", Language.GERMAN, true), TEST_PREFIX + "student1");
        var submission2 = participationUtilService.addSubmission(textExercise1, ParticipationFactory.generateTextSubmission("", Language.GERMAN, true), TEST_PREFIX + "student2");
        var submission3 = participationUtilService.addSubmission(textExercise1, ParticipationFactory.generateTextSubmission("", Language.GERMAN, true), TEST_PREFIX + "student3");
        PlagiarismComparison<TextSubmissionElement> plagiarismComparison1 = new PlagiarismComparison<>();
        plagiarismComparison1.setPlagiarismResult(textPlagiarismResult1);
        plagiarismComparison1.setStatus(CONFIRMED);
        var plagiarismSubmissionA1 = new PlagiarismSubmission<TextSubmissionElement>();
        plagiarismSubmissionA1.setStudentLogin(TEST_PREFIX + "student1");
        plagiarismSubmissionA1.setSubmissionId(submission1.getId());
        var plagiarismSubmissionB1 = new PlagiarismSubmission<TextSubmissionElement>();
        plagiarismSubmissionB1.setStudentLogin(TEST_PREFIX + "student2");
        plagiarismSubmissionB1.setSubmissionId(submission2.getId());
        plagiarismComparison1.setSubmissionA(plagiarismSubmissionA1);
        plagiarismComparison1.setSubmissionB(plagiarismSubmissionB1);
        plagiarismComparison1.setMatches(Set.of(new PlagiarismMatch()));
        plagiarismComparison1 = plagiarismComparisonRepository.save(plagiarismComparison1);

        PlagiarismComparison<TextSubmissionElement> plagiarismComparison2 = new PlagiarismComparison<>();
        plagiarismComparison2.setPlagiarismResult(textPlagiarismResult1);
        plagiarismComparison2.setStatus(NONE);
        var plagiarismSubmissionA2 = new PlagiarismSubmission<TextSubmissionElement>();
        plagiarismSubmissionA2.setStudentLogin(TEST_PREFIX + "student2");
        plagiarismSubmissionA2.setSubmissionId(submission2.getId());
        var plagiarismSubmissionB2 = new PlagiarismSubmission<TextSubmissionElement>();
        plagiarismSubmissionB2.setStudentLogin(TEST_PREFIX + "student3");
        plagiarismSubmissionB2.setSubmissionId(submission3.getId());
        plagiarismComparison2.setSubmissionA(plagiarismSubmissionA2);
        plagiarismComparison2.setSubmissionB(plagiarismSubmissionB2);
        plagiarismComparison2.setMatches(Set.of(new PlagiarismMatch()));
        plagiarismComparison2 = plagiarismComparisonRepository.save(plagiarismComparison2);

        // new course, should not delete undecided plagiarism comparisons
        var textExercise2 = textExerciseRepository.findByCourseIdWithCategories(newCourse.getId()).getFirst();
        var textPlagiarismResult2 = textExerciseUtilService.createTextPlagiarismResultForExercise(textExercise2);

        var submission4 = participationUtilService.addSubmission(textExercise2, ParticipationFactory.generateTextSubmission("", Language.GERMAN, true), TEST_PREFIX + "student1");
        var submission5 = participationUtilService.addSubmission(textExercise2, ParticipationFactory.generateTextSubmission("", Language.GERMAN, true), TEST_PREFIX + "student2");
        var submission6 = participationUtilService.addSubmission(textExercise2, ParticipationFactory.generateTextSubmission("", Language.GERMAN, true), TEST_PREFIX + "student3");
        PlagiarismComparison<TextSubmissionElement> plagiarismComparison3 = new PlagiarismComparison<>();
        plagiarismComparison3.setPlagiarismResult(textPlagiarismResult2);
        plagiarismComparison3.setStatus(CONFIRMED);
        var plagiarismSubmissionA3 = new PlagiarismSubmission<TextSubmissionElement>();
        plagiarismSubmissionA1.setStudentLogin(TEST_PREFIX + "student1");
        plagiarismSubmissionA1.setSubmissionId(submission4.getId());
        var plagiarismSubmissionB3 = new PlagiarismSubmission<TextSubmissionElement>();
        plagiarismSubmissionB1.setStudentLogin(TEST_PREFIX + "student2");
        plagiarismSubmissionB1.setSubmissionId(submission5.getId());
        plagiarismComparison3.setSubmissionA(plagiarismSubmissionA3);
        plagiarismComparison3.setSubmissionB(plagiarismSubmissionB3);
        plagiarismComparison3.setMatches(Set.of(new PlagiarismMatch()));
        plagiarismComparison3 = plagiarismComparisonRepository.save(plagiarismComparison3);

        PlagiarismComparison<TextSubmissionElement> plagiarismComparison4 = new PlagiarismComparison<>();
        plagiarismComparison4.setPlagiarismResult(textPlagiarismResult2);
        plagiarismComparison4.setStatus(NONE);
        var plagiarismSubmissionA4 = new PlagiarismSubmission<TextSubmissionElement>();
        plagiarismSubmissionA2.setStudentLogin(TEST_PREFIX + "student2");
        plagiarismSubmissionA2.setSubmissionId(submission2.getId());
        var plagiarismSubmissionB4 = new PlagiarismSubmission<TextSubmissionElement>();
        plagiarismSubmissionB2.setStudentLogin(TEST_PREFIX + "student3");
        plagiarismSubmissionB2.setSubmissionId(submission6.getId());
        plagiarismComparison4.setSubmissionA(plagiarismSubmissionA4);
        plagiarismComparison4.setSubmissionB(plagiarismSubmissionB4);
        plagiarismComparison4.setMatches(Set.of(new PlagiarismMatch()));
        plagiarismComparison4 = plagiarismComparisonRepository.save(plagiarismComparison4);

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("deleteFrom", DELETE_FROM.toString());
        params.add("deleteTo", DELETE_TO.toString());
        var responseBody = request.postWithResponseBody("/api/admin/cleanup/delete-plagiarism-comparisons", null, CleanupServiceExecutionRecordDTO.class, HttpStatus.OK, null, null,
                params);

        assertThat(responseBody.jobType()).isEqualTo("deletePlagiarismComparisons");
        assertThat(responseBody.executionDate()).isNotNull();

        assertThat(plagiarismComparisonRepository.existsById(plagiarismComparison2.getId())).isFalse();
        assertThat(plagiarismComparisonRepository.existsById(plagiarismComparison1.getId())).isTrue();

        assertThat(plagiarismComparisonRepository.existsById(plagiarismComparison4.getId())).isTrue();
        assertThat(plagiarismComparisonRepository.existsById(plagiarismComparison3.getId())).isTrue();

    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testDeleteNonRatedResults() throws Exception {
        // create non rated results for an old course
        var oldExercise = textExerciseRepository.findByCourseIdWithCategories(oldCourse.getId()).getFirst();
        var oldStudentParticipation = participationUtilService.createAndSaveParticipationForExercise(oldExercise, student.getLogin());
        var oldSubmission = participationUtilService.addSubmission(oldStudentParticipation, ParticipationFactory.generateProgrammingSubmission(true));
        ;
        var oldResult1 = participationUtilService.generateResult(oldSubmission, instructor);
        oldResult1.setParticipation(oldStudentParticipation);
        oldResult1.setRated(false);
        var oldResult2 = participationUtilService.generateResult(oldSubmission, instructor);
        oldResult2.setParticipation(oldStudentParticipation);
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
        oldParticipantScore1 = studentScoreRepository.save(oldParticipantScore1);

        StudentScore oldParticipantScore2 = new StudentScore();
        oldParticipantScore2.setUser(student);
        oldParticipantScore2.setExercise(oldExercise);
        oldParticipantScore2.setLastResult(oldResult2);
        oldParticipantScore2 = studentScoreRepository.save(oldParticipantScore1);

        // create non rated results for the new course
        var newExercise = textExerciseRepository.findByCourseIdWithCategories(newCourse.getId()).getFirst();
        var newStudentParticipation = participationUtilService.createAndSaveParticipationForExercise(newExercise, student.getLogin());
        var newSubmission = participationUtilService.addSubmission(newStudentParticipation, ParticipationFactory.generateProgrammingSubmission(true));
        ;
        var newResult1 = participationUtilService.generateResult(newSubmission, instructor);
        newResult1.setParticipation(newStudentParticipation);
        newResult1.setRated(false);
        var newResult2 = participationUtilService.generateResult(newSubmission, instructor);
        newResult2.setParticipation(newStudentParticipation);
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
        var responseBody = request.postWithResponseBody("/api/admin/cleanup/delete-non-rated-results", null, CleanupServiceExecutionRecordDTO.class, HttpStatus.OK, null, null,
                params);

        assertThat(responseBody.jobType()).isEqualTo("deleteNonRatedResults");
        assertThat(responseBody.executionDate()).isNotNull();

        assertThat(participantScoreRepository.findById(oldParticipantScore1.getId())).isEmpty();
        assertThat(participantScoreRepository.findById(oldParticipantScore2.getId())).isEmpty();
        assertThat(resultRepository.findById(oldResult1.getId())).isEmpty();
        assertThat(feedbackRepository.findByResult(oldResult1)).isEmpty();
        assertThat(textBlockRepository.findById(oldTextBlock1.getId())).isEmpty();

        assertThat(resultRepository.findById(oldResult2.getId())).isEmpty();
        assertThat(feedbackRepository.findByResult(oldResult2)).isEmpty();
        assertThat(textBlockRepository.findById(oldTextBlock2.getId())).isEmpty();

        assertThat(participantScoreRepository.findById(newParticipantScore1.getId())).isNotEmpty();
        assertThat(participantScoreRepository.findById(newParticipantScore2.getId())).isNotEmpty();
        assertThat(resultRepository.findById(newResult1.getId())).isPresent();
        assertThat(feedbackRepository.findByResult(newResult1)).isNotEmpty();
        assertThat(textBlockRepository.findById(newTextBlock1.getId())).isNotEmpty();

        assertThat(resultRepository.findById(newResult2.getId())).isPresent();
        assertThat(feedbackRepository.findByResult(newResult2)).isNotEmpty();
        assertThat(textBlockRepository.findById(newTextBlock2.getId())).isNotEmpty();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testDeleteOldRatedResults() throws Exception {
        // create rated results for an old course
        var oldExercise = textExerciseRepository.findByCourseIdWithCategories(oldCourse.getId()).getFirst();
        var oldStudentParticipation = participationUtilService.createAndSaveParticipationForExercise(oldExercise, student.getLogin());
        var oldSubmission = participationUtilService.addSubmission(oldStudentParticipation, ParticipationFactory.generateProgrammingSubmission(true));
        ;
        var oldResult1 = participationUtilService.generateResult(oldSubmission, instructor); // should be deleted, with all associated entities
        oldResult1.setParticipation(oldStudentParticipation);
        var oldResult2 = participationUtilService.generateResult(oldSubmission, instructor);
        oldResult2.setParticipation(oldStudentParticipation);

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
        oldParticipantScore1 = studentScoreRepository.save(oldParticipantScore1);

        StudentScore oldParticipantScore2 = new StudentScore();
        oldParticipantScore2.setExercise(oldExercise);
        oldParticipantScore2.setLastResult(oldResult2);
        oldParticipantScore2.setUser(student);
        oldParticipantScore2 = studentScoreRepository.save(oldParticipantScore2);

        // create rated results for the new course
        var newExercise = textExerciseRepository.findByCourseIdWithCategories(newCourse.getId()).getFirst();
        var newStudentParticipation = participationUtilService.createAndSaveParticipationForExercise(newExercise, student.getLogin());
        var newSubmission = participationUtilService.addSubmission(newStudentParticipation, ParticipationFactory.generateProgrammingSubmission(true));
        ;
        var newResult1 = participationUtilService.generateResult(newSubmission, instructor); // should not be deleted, with all associated entities
        newResult1.setParticipation(newStudentParticipation);
        var newResult2 = participationUtilService.generateResult(newSubmission, instructor);
        newResult2.setParticipation(newStudentParticipation);

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
        var responseBody = request.postWithResponseBody("/api/admin/cleanup/delete-old-rated-results", null, CleanupServiceExecutionRecordDTO.class, HttpStatus.OK, null, null,
                params);

        assertThat(responseBody.jobType()).isEqualTo("deleteRatedResults");
        assertThat(responseBody.executionDate()).isNotNull();

        assertThat(participantScoreRepository.findById(oldParticipantScore1.getId())).isEmpty();
        assertThat(participantScoreRepository.findById(oldParticipantScore2.getId())).isPresent();
        assertThat(resultRepository.findById(oldResult1.getId())).isEmpty();
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
    @WithMockUser(roles = "ADMIN")
    void testGetLastExecutions() throws Exception {

        var now = ZonedDateTime.now();

        var jobExecution = new CleanupJobExecution();
        jobExecution.setCleanupJobType(CleanupJobType.ORPHANS);
        jobExecution.setDeletionTimestamp(now);
        cleanupJobExecutionRepository.save(jobExecution);

        var response = request.getList("/api/admin/cleanup/get-last-executions", HttpStatus.OK, CleanupServiceExecutionRecordDTO.class);

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
        request.postWithoutResponseBody("/api/admin/cleanup/delete-orphans", HttpStatus.FORBIDDEN, new LinkedMultiValueMap<>());
        request.postWithoutResponseBody("/api/admin/cleanup/delete-plagiarism-comparisons", HttpStatus.FORBIDDEN, new LinkedMultiValueMap<>());
        request.postWithoutResponseBody("/api/admin/cleanup/delete-non-rated-results", HttpStatus.FORBIDDEN, new LinkedMultiValueMap<>());
        request.postWithoutResponseBody("/api/admin/cleanup/delete-old-rated-results", HttpStatus.FORBIDDEN, new LinkedMultiValueMap<>());
        request.postWithoutResponseBody("/api/admin/cleanup/delete-old-submission-versions", HttpStatus.FORBIDDEN, new LinkedMultiValueMap<>());
        request.postWithoutResponseBody("/api/admin/cleanup/delete-old-feedback", HttpStatus.FORBIDDEN, new LinkedMultiValueMap<>());
        request.get("/api/admin/cleanup/get-last-executions", HttpStatus.FORBIDDEN, List.class);
    }

    @Test
    void testTransactionalBehavior() {

        long count = resultRepository.count();

        var orphanResult = new Result();
        resultRepository.save(orphanResult);

        transactionTemplate.executeWithoutResult(status -> {
            dataCleanupRepository.deleteOrphans();
            status.setRollbackOnly();
        });

        assertThat(resultRepository.count()).as("Expected no entities get deleted after rollback").isEqualTo(count + 1);
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
        textBlock.setText("text" + feedback.hashCode());
        textBlock.computeId();
        return textBlockRepository.save(textBlock);
    }
}
