package de.tum.in.www1.artemis.cleanup;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
 import java.util.List;
import java.util.Set;

import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.LongFeedbackText;
import de.tum.in.www1.artemis.domain.Rating;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.Team;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismComparison;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismSubmission;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextSubmissionElement;
import de.tum.in.www1.artemis.domain.scores.StudentScore;
import de.tum.in.www1.artemis.domain.scores.TeamScore;
import de.tum.in.www1.artemis.exercise.programming.ProgrammingExerciseUtilService;
import de.tum.in.www1.artemis.localvcci.AbstractLocalCILocalVCIntegrationTest;
import de.tum.in.www1.artemis.repository.FeedbackRepository;
import de.tum.in.www1.artemis.repository.LongFeedbackTextRepository;
import de.tum.in.www1.artemis.repository.RatingRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.StudentScoreRepository;
import de.tum.in.www1.artemis.repository.SubmissionRepository;
import de.tum.in.www1.artemis.repository.TeamScoreRepository;
import de.tum.in.www1.artemis.repository.cleanup.CleanupJobExecutionRepository;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismComparisonRepository;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismSubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.web.rest.dto.CleanupServiceExecutionRecordDTO;
import org.springframework.util.LinkedMultiValueMap;
import org.w3c.dom.Text;

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

        StudentScore orphanStudentScore = new StudentScore();
        orphanStudentScore = studentScoreRepository.save(orphanStudentScore);

        TeamScore orphanTeamScore = new TeamScore();
        orphanTeamScore = teamScoreRepository.save(orphanTeamScore);

        Result orphanResult = new Result();
        orphanResult = resultRepository.save(orphanResult);

        PlagiarismComparison<TextSubmissionElement> orphanPlagiarismComparison = new PlagiarismComparison<>();
        orphanPlagiarismComparison = plagiarismComparisonRepository.save(orphanPlagiarismComparison);

        //var submission = participationUtilService.addSubmission(course2.getExercises().stream().findFirst().orElseThrow(), new ModelingSubmission(), TEST_PREFIX + "student228");

        Feedback nonOrphanFeedback = new Feedback();
        Result resultWithFeedback = new Result();
        //resultWithFeedback.setParticipation(submission.getParticipation());
        //resultWithFeedback.setSubmission(submission);
        nonOrphanFeedback.setResult(resultWithFeedback);
        resultWithFeedback = resultRepository.save(resultWithFeedback);
        nonOrphanFeedback = feedbackRepository.save(nonOrphanFeedback);

        StudentScore nonOrphanStudentScore = new StudentScore();
        User user = new User();
        user.setGroups(Set.of("STUDENT"));
        user.setLogin("student228");
        nonOrphanStudentScore.setUser(user);
        userRepository.save(user);
        nonOrphanStudentScore = studentScoreRepository.save(nonOrphanStudentScore);

        TeamScore nonOrphanTeamScore = new TeamScore();
        Team team = new Team();
        team.setShortName("team");
        nonOrphanTeamScore.setTeam(team);
        teamRepository.save(team);
        nonOrphanTeamScore = teamScoreRepository.save(nonOrphanTeamScore);

        Rating nonOrphanRating = new Rating();
        nonOrphanRating.setResult(resultWithFeedback);
        nonOrphanRating = ratingRepository.save(nonOrphanRating);

        PlagiarismComparison<TextSubmissionElement> nonOrphanPlagiarismComparison = new PlagiarismComparison<>();
        nonOrphanPlagiarismComparison = plagiarismComparisonRepository.save(nonOrphanPlagiarismComparison);
        PlagiarismSubmission<TextSubmissionElement> submissionA = new PlagiarismSubmission<>();
        PlagiarismSubmission<TextSubmissionElement> submissionB = new PlagiarismSubmission<>();
        submissionA.setSubmissionId(0);
        submissionB.setSubmissionId(1);
        nonOrphanPlagiarismComparison.setSubmissionA(submissionA);
        nonOrphanPlagiarismComparison.setSubmissionB(submissionB);
        plagiarismSubmissionRepository.save(submissionA);
        plagiarismSubmissionRepository.save(submissionB);

        request.postWithoutResponseBody("/api/admin/delete-orphans", HttpStatus.OK, new LinkedMultiValueMap<>());

        assertThat(feedbackRepository.existsById(orphanFeedback.getId())).isFalse();
        assertThat(studentScoreRepository.existsById(orphanStudentScore.getId())).isFalse();
        assertThat(teamScoreRepository.existsById(orphanTeamScore.getId())).isFalse();
        assertThat(resultRepository.existsById(orphanResult.getId())).isFalse();
        assertThat(plagiarismComparisonRepository.existsById(orphanPlagiarismComparison.getId())).isFalse();

        assertThat(feedbackRepository.existsById(nonOrphanFeedback.getId())).isTrue();
        assertThat(studentScoreRepository.existsById(nonOrphanStudentScore.getId())).isTrue();
        assertThat(teamScoreRepository.existsById(nonOrphanTeamScore.getId())).isTrue();
        assertThat(ratingRepository.existsById(nonOrphanRating.getId())).isTrue();
        assertThat(plagiarismComparisonRepository.existsById(nonOrphanPlagiarismComparison.getId())).isTrue();
    }


//    @Test
//    @WithMockUser(username = "admin", roles = "ADMIN", setupBefore = TEST_EXECUTION)
//    @Transactional
//    void testDeletePlagiarismComparisons() throws Exception {
//        // Проверяем, что данные созданы
//        ZonedDateTime from = ZonedDateTime.now().minusMonths(1);
//        ZonedDateTime to = ZonedDateTime.now();
//
//        // Вызов эндпоинта
//        var response = request.post("/api/admin/delete-plagiarism-comparisons?deleteFrom=" + from + "&deleteTo=" + to,
//            null, HttpStatus.OK, CleanupServiceExecutionRecordDTO.class);
//
//        // Проверяем, что операция выполнена успешно
//        assertThat(response).isNotNull();
//        assertThat(response.getRecordsDeleted()).isGreaterThan(0);
//    }
//
//    @Test
//    @WithMockUser(username = "admin", roles = "ADMIN", setupBefore = TEST_EXECUTION)
//    @Transactional
//    void testDeleteNonRatedResults() throws Exception {
//        ZonedDateTime from = ZonedDateTime.now().minusMonths(1);
//        ZonedDateTime to = ZonedDateTime.now();
//
//        // Вызов эндпоинта
//        var response = request.post("/api/admin/delete-non-rated-results?deleteFrom=" + from + "&deleteTo=" + to,
//            null, HttpStatus.OK, CleanupServiceExecutionRecordDTO.class);
//
//        // Проверяем успешное выполнение операции
//        assertThat(response).isNotNull();
//        assertThat(response.getRecordsDeleted()).isGreaterThan(0);
//    }
//
//    @Test
//    @WithMockUser(username = "admin", roles = "ADMIN", setupBefore = TEST_EXECUTION)
//    @Transactional
//    void testDeleteOldRatedResults() throws Exception {
//        ZonedDateTime from = ZonedDateTime.now().minusMonths(1);
//        ZonedDateTime to = ZonedDateTime.now();
//
//        // Вызов эндпоинта
//        var response = request.post("/api/admin/delete-old-rated-results?deleteFrom=" + from + "&deleteTo=" + to,
//            null, HttpStatus.OK, CleanupServiceExecutionRecordDTO.class);
//
//        // Проверка успешного выполнения операции
//        assertThat(response).isNotNull();
//        assertThat(response.getRecordsDeleted()).isGreaterThan(0);
//    }
//
//    @Test
//    @WithMockUser(username = "admin", roles = "ADMIN", setupBefore = TEST_EXECUTION)
//    @Transactional
//    void testDeleteSubmissionVersions() throws Exception {
//        ZonedDateTime from = ZonedDateTime.now().minusMonths(1);
//        ZonedDateTime to = ZonedDateTime.now();
//
//        // Вызов эндпоинта
//        var response = request.post("/api/admin/delete-old-submission-versions?deleteFrom=" + from + "&deleteTo=" + to,
//            null, HttpStatus.OK, CleanupServiceExecutionRecordDTO.class);
//
//        // Проверка успешного выполнения операции
//        assertThat(response).isNotNull();
//        assertThat(response.getRecordsDeleted()).isGreaterThan(0);
//    }
//
//    @Test
//    @WithMockUser(username = "admin", roles = "ADMIN", setupBefore = TEST_EXECUTION)
//    @Transactional
//    void testDeleteOldFeedback() throws Exception {
//        ZonedDateTime from = ZonedDateTime.now().minusMonths(1);
//        ZonedDateTime to = ZonedDateTime.now();
//
//        // Вызов эндпоинта
//        var response = request.post("/api/admin/delete-old-feedback?deleteFrom=" + from + "&deleteTo=" + to,
//            null, HttpStatus.OK, CleanupServiceExecutionRecordDTO.class);
//
//        // Проверка успешного выполнения операции
//        assertThat(response).isNotNull();
//        assertThat(response.getRecordsDeleted()).isGreaterThan(0);
//    }
//
//    @Test
//    @WithMockUser(username = "admin", roles = "ADMIN", setupBefore = TEST_EXECUTION)
//    @Transactional
//    void testGetLastExecutions() throws Exception {
//        // Создаем запись выполнения
//        cleanupServiceExecutionRecordRepository.save(new CleanupServiceExecutionRecordDTO(ZonedDateTime.now(), 10L));
//
//        // Вызов эндпоинта для получения записей
//        var response = request.getList("/api/admin/get-last-executions", HttpStatus.OK, CleanupServiceExecutionRecordDTO.class);
//
//        // Проверяем, что записи получены
//        assertThat(response).isNotNull();
//        assertThat(response).isNotEmpty(); // Должны быть записи
//    }
//
//    // Тестируем, что только админ может вызывать эндпоинты
//    @Test
//    @WithMockUser(username = DEFAULT_USERNAME, roles = "USER")
//    void testUnauthorizedAccess() throws Exception {
//        // Пробуем вызвать эндпоинт без прав администратора
//        request.post("/api/admin/delete-orphans", null, HttpStatus.FORBIDDEN);
//        request.post("/api/admin/delete-plagiarism-comparisons", null, HttpStatus.FORBIDDEN);
//        request.post("/api/admin/delete-non-rated-results", null, HttpStatus.FORBIDDEN);
//        request.post("/api/admin/delete-old-rated-results", null, HttpStatus.FORBIDDEN);
//        request.post("/api/admin/delete-old-submission-versions", null, HttpStatus.FORBIDDEN);
//        request.post("/api/admin/delete-old-feedback", null, HttpStatus.FORBIDDEN);
//        request.get("/api/admin/get-last-executions", HttpStatus.FORBIDDEN);
//    }
}
