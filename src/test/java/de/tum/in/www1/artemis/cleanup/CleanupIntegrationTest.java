package de.tum.in.www1.artemis.cleanup;

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
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.LongFeedbackText;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.Rating;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.Team;
import de.tum.in.www1.artemis.domain.TextBlock;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.cleanup.CleanupJobExecution;
import de.tum.in.www1.artemis.domain.enumeration.CleanupJobType;
import de.tum.in.www1.artemis.domain.scores.StudentScore;
import de.tum.in.www1.artemis.domain.scores.TeamScore;
import de.tum.in.www1.artemis.exercise.programming.ProgrammingExerciseUtilService;
import de.tum.in.www1.artemis.localvcci.AbstractLocalCILocalVCIntegrationTest;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.FeedbackRepository;
import de.tum.in.www1.artemis.repository.LongFeedbackTextRepository;
import de.tum.in.www1.artemis.repository.RatingRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.StudentScoreRepository;
import de.tum.in.www1.artemis.repository.SubmissionRepository;
import de.tum.in.www1.artemis.repository.TeamScoreRepository;
import de.tum.in.www1.artemis.repository.TextBlockRepository;
import de.tum.in.www1.artemis.repository.cleanup.CleanupJobExecutionRepository;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismComparisonRepository;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismSubmissionRepository;
import de.tum.in.www1.artemis.web.rest.dto.CleanupServiceExecutionRecordDTO;

class CleanupIntegrationTest extends AbstractLocalCILocalVCIntegrationTest {

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
    private SubmissionRepository submissionRepository;

    @Autowired
    private PlagiarismSubmissionRepository plagiarismSubmissionRepository;

    @Autowired
    private TextBlockRepository textBlockRepository;

    // data for this course will be deleted
    private Course course1;

    // this is a new course whose data will not be deleted
    private Course course2;

    @BeforeEach
    void initTestCase() {
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime startDate = now.minusMonths(12).plusDays(2);
        ZonedDateTime endDate = now.minusMonths(6).minusDays(2);

        course1 = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        course1.setStartDate(startDate);
        course1.setEndDate(endDate);
        courseRepository.save(course1);

        course2 = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        course2.setStartDate(now);
        course2.setEndDate(now.plusMonths(6));
        courseRepository.save(course2);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testDeleteOrphans() throws Exception {
        Feedback orphanFeedback = new Feedback();
        orphanFeedback = feedbackRepository.save(orphanFeedback);

        TextBlock textBlockForOrphanFeedback = new TextBlock();
        textBlockForOrphanFeedback.setFeedback(orphanFeedback);
        textBlockForOrphanFeedback.setText("text");
        textBlockForOrphanFeedback.computeId();
        textBlockRepository.save(textBlockForOrphanFeedback);

        LongFeedbackText orphanLongFeedbackText = new LongFeedbackText();
        orphanLongFeedbackText.setFeedback(orphanFeedback);
        orphanLongFeedbackText.setText("text");
        longFeedbackTextRepository.save(orphanLongFeedbackText);

        StudentScore orphanStudentScore = new StudentScore();
        orphanStudentScore = studentScoreRepository.save(orphanStudentScore);

        TeamScore orphanTeamScore = new TeamScore();
        orphanTeamScore = teamScoreRepository.save(orphanTeamScore);

        Result orphanResult = new Result();
        orphanResult = resultRepository.save(orphanResult);

        orphanFeedback.setResult(orphanResult);
        orphanFeedback = feedbackRepository.save(orphanFeedback);

        User user = new User();
        user.setGroups(Set.of("STUDENT"));
        user.setLogin("student228");
        userRepository.save(user);

        var submission = participationUtilService.addSubmission(course2.getExercises().stream().findFirst().orElseThrow(), new ProgrammingSubmission(), "student228");

        Feedback nonOrphanFeedback = new Feedback();
        nonOrphanFeedback = feedbackRepository.save(nonOrphanFeedback);

        TextBlock textBlockForNonOrphanFeedback = new TextBlock();
        textBlockForNonOrphanFeedback.setFeedback(nonOrphanFeedback);
        textBlockForNonOrphanFeedback.setText("newText");
        textBlockForNonOrphanFeedback.computeId();
        textBlockRepository.save(textBlockForNonOrphanFeedback);

        LongFeedbackText nonOrphanLongFeedbackText = new LongFeedbackText();
        nonOrphanLongFeedbackText.setFeedback(nonOrphanFeedback);
        nonOrphanLongFeedbackText.setText("newText");
        longFeedbackTextRepository.save(nonOrphanLongFeedbackText);

        Result nonOrphanResult = new Result();
        nonOrphanResult.setParticipation(submission.getParticipation());
        nonOrphanResult.setSubmission(submission);
        nonOrphanFeedback.setResult(nonOrphanResult);
        nonOrphanResult = resultRepository.save(nonOrphanResult);

        nonOrphanFeedback.setResult(nonOrphanResult);
        nonOrphanFeedback = feedbackRepository.save(nonOrphanFeedback);

        StudentScore nonOrphanStudentScore = new StudentScore();
        nonOrphanStudentScore.setUser(user);
        nonOrphanStudentScore = studentScoreRepository.save(nonOrphanStudentScore);

        TeamScore nonOrphanTeamScore = new TeamScore();
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

        request.postWithoutResponseBody("/api/admin/delete-orphans", HttpStatus.OK, new LinkedMultiValueMap<>());

        assertThat(longFeedbackTextRepository.existsById(orphanLongFeedbackText.getId())).isFalse();
        assertThat(textBlockRepository.existsById(textBlockForOrphanFeedback.getId())).isFalse();
        assertThat(feedbackRepository.existsById(orphanFeedback.getId())).isFalse();
        assertThat(studentScoreRepository.existsById(orphanStudentScore.getId())).isFalse();
        assertThat(teamScoreRepository.existsById(orphanTeamScore.getId())).isFalse();
        assertThat(resultRepository.existsById(orphanResult.getId())).isFalse();
        assertThat(ratingRepository.existsById(orphanRating.getId())).isFalse();

        assertThat(textBlockRepository.existsById(textBlockForNonOrphanFeedback.getId())).isTrue();
        assertThat(longFeedbackTextRepository.existsById(nonOrphanLongFeedbackText.getId())).isTrue();
        assertThat(feedbackRepository.existsById(nonOrphanFeedback.getId())).isTrue();
        assertThat(studentScoreRepository.existsById(nonOrphanStudentScore.getId())).isTrue();
        assertThat(teamScoreRepository.existsById(nonOrphanTeamScore.getId())).isTrue();
        assertThat(ratingRepository.existsById(nonOrphanRating.getId())).isTrue();
        assertThat(resultRepository.existsById(nonOrphanResult.getId())).isTrue();
    }

    // @Test
    // @WithMockUser(username = "admin", roles = "ADMIN", setupBefore = TEST_EXECUTION)
    // @Transactional
    // void testDeletePlagiarismComparisons() throws Exception {
    // // Проверяем, что данные созданы
    // ZonedDateTime from = ZonedDateTime.now().minusMonths(1);
    // ZonedDateTime to = ZonedDateTime.now();
    //
    // // Вызов эндпоинта
    // var response = request.post("/api/admin/delete-plagiarism-comparisons?deleteFrom=" + from + "&deleteTo=" + to,
    // null, HttpStatus.OK, CleanupServiceExecutionRecordDTO.class);
    //
    // // Проверяем, что операция выполнена успешно
    // assertThat(response).isNotNull();
    // assertThat(response.getRecordsDeleted()).isGreaterThan(0);
    // }
    //
    // @Test
    // @WithMockUser(username = "admin", roles = "ADMIN", setupBefore = TEST_EXECUTION)
    // @Transactional
    // void testDeleteNonRatedResults() throws Exception {
    // ZonedDateTime from = ZonedDateTime.now().minusMonths(1);
    // ZonedDateTime to = ZonedDateTime.now();
    //
    // // Вызов эндпоинта
    // var response = request.post("/api/admin/delete-non-rated-results?deleteFrom=" + from + "&deleteTo=" + to,
    // null, HttpStatus.OK, CleanupServiceExecutionRecordDTO.class);
    //
    // // Проверяем успешное выполнение операции
    // assertThat(response).isNotNull();
    // assertThat(response.getRecordsDeleted()).isGreaterThan(0);
    // }
    //
    // @Test
    // @WithMockUser(username = "admin", roles = "ADMIN", setupBefore = TEST_EXECUTION)
    // @Transactional
    // void testDeleteOldRatedResults() throws Exception {
    // ZonedDateTime from = ZonedDateTime.now().minusMonths(1);
    // ZonedDateTime to = ZonedDateTime.now();
    //
    // // Вызов эндпоинта
    // var response = request.post("/api/admin/delete-old-rated-results?deleteFrom=" + from + "&deleteTo=" + to,
    // null, HttpStatus.OK, CleanupServiceExecutionRecordDTO.class);
    //
    // // Проверка успешного выполнения операции
    // assertThat(response).isNotNull();
    // assertThat(response.getRecordsDeleted()).isGreaterThan(0);
    // }
    //
    // @Test
    // @WithMockUser(username = "admin", roles = "ADMIN", setupBefore = TEST_EXECUTION)
    // @Transactional
    // void testDeleteSubmissionVersions() throws Exception {
    // ZonedDateTime from = ZonedDateTime.now().minusMonths(1);
    // ZonedDateTime to = ZonedDateTime.now();
    //
    // // Вызов эндпоинта
    // var response = request.post("/api/admin/delete-old-submission-versions?deleteFrom=" + from + "&deleteTo=" + to,
    // null, HttpStatus.OK, CleanupServiceExecutionRecordDTO.class);
    //
    // // Проверка успешного выполнения операции
    // assertThat(response).isNotNull();
    // assertThat(response.getRecordsDeleted()).isGreaterThan(0);
    // }
    //
    // @Test
    // @WithMockUser(username = "admin", roles = "ADMIN", setupBefore = TEST_EXECUTION)
    // @Transactional
    // void testDeleteOldFeedback() throws Exception {
    // ZonedDateTime from = ZonedDateTime.now().minusMonths(1);
    // ZonedDateTime to = ZonedDateTime.now();
    //
    // // Вызов эндпоинта
    // var response = request.post("/api/admin/delete-old-feedback?deleteFrom=" + from + "&deleteTo=" + to,
    // null, HttpStatus.OK, CleanupServiceExecutionRecordDTO.class);
    //
    // // Проверка успешного выполнения операции
    // assertThat(response).isNotNull();
    // assertThat(response.getRecordsDeleted()).isGreaterThan(0);
    // }
    //
    @Test
    @WithMockUser(roles = "ADMIN")
    void testGetLastExecutions() throws Exception {

        var now = ZonedDateTime.now();

        var jobExecution = new CleanupJobExecution();
        jobExecution.setCleanupJobType(CleanupJobType.ORPHANS);
        jobExecution.setDeletionTimestamp(now);
        cleanupJobExecutionRepository.save(jobExecution);

        var response = request.getList("/api/admin/get-last-executions", HttpStatus.OK, CleanupServiceExecutionRecordDTO.class);

        List<String> enumJobTypes = Arrays.stream(CleanupJobType.values()).map(CleanupJobType::getName).toList();

        assertThat(response).isNotNull();
        assertThat(response).extracting(CleanupServiceExecutionRecordDTO::jobType).containsAll(enumJobTypes);

        var orphansJob = response.stream().filter(elem -> elem.jobType().equals(CleanupJobType.ORPHANS.getName())).findFirst();

        assertThat(orphansJob).isPresent();
        assertThat(now).isEqualTo(orphansJob.get().executionDate());
    }

    @Test
    @WithMockUser(roles = "USER")
    void testUnauthorizedAccess() throws Exception {
        request.postWithoutResponseBody("/api/admin/delete-orphans", HttpStatus.FORBIDDEN, new LinkedMultiValueMap<>());
        request.postWithoutResponseBody("/api/admin/delete-plagiarism-comparisons", HttpStatus.FORBIDDEN, new LinkedMultiValueMap<>());
        request.postWithoutResponseBody("/api/admin/delete-non-rated-results", HttpStatus.FORBIDDEN, new LinkedMultiValueMap<>());
        request.postWithoutResponseBody("/api/admin/delete-old-rated-results", HttpStatus.FORBIDDEN, new LinkedMultiValueMap<>());
        request.postWithoutResponseBody("/api/admin/delete-old-submission-versions", HttpStatus.FORBIDDEN, new LinkedMultiValueMap<>());
        request.postWithoutResponseBody("/api/admin/delete-old-feedback", HttpStatus.FORBIDDEN, new LinkedMultiValueMap<>());
        request.get("/api/admin/get-last-executions", HttpStatus.FORBIDDEN, List.class);
    }
}
