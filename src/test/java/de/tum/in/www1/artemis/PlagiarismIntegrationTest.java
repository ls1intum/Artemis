package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.notification.Notification;
import de.tum.in.www1.artemis.domain.notification.SingleUserNotification;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismComparison;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismStatus;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismSubmission;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextPlagiarismResult;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextSubmissionElement;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.web.rest.PlagiarismResource;
import de.tum.in.www1.artemis.web.rest.dto.PlagiarismCaseDTO;
import de.tum.in.www1.artemis.web.rest.dto.PlagiarismComparisonStatusDTO;
import de.tum.in.www1.artemis.web.rest.dto.PlagiarismNotificationDTO;

public class PlagiarismIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private PlagiarismComparisonRepository plagiarismComparisonRepository;

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @BeforeEach
    public void initTestCase() {
        database.addUsers(3, 1, 1, 1);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    private static final String INSTRUCTOR_STATEMENT_A = "instructor Statement A";
    private static final String INSTRUCTOR_STATEMENT_B = "instructor Statement B";

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void updatePlagiarismComparisonStatusToFinalWithoutStudentLogin_student() throws Exception {
        request.put("/api/plagiarism-comparisons/1/status?finalDecision=true", PlagiarismComparisonStatusDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    public void updatePlagiarismComparisonStatusToFinalWithoutStudentLogin_editor() throws Exception {
        request.put("/api/plagiarism-comparisons/1/status?finalDecision=true", PlagiarismComparisonStatusDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updatePlagiarismComparisonStatusToFinalForStudentA() throws Exception {
        Course course = database.addCourseWithOneFinishedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseId(course.getId()).get(0);
        TextPlagiarismResult textPlagiarismResult = database.createTextPlagiarismResultForExercise(textExercise);
        PlagiarismComparison<TextSubmissionElement> plagiarismComparison = new PlagiarismComparison<>();
        PlagiarismSubmission<TextSubmissionElement> submissionA = new PlagiarismSubmission<>();
        submissionA.setStudentLogin("student1");
        plagiarismComparison.setInstructorStatementA(INSTRUCTOR_STATEMENT_A);
        plagiarismComparison.setPlagiarismResult(textPlagiarismResult);
        plagiarismComparison.setStatusA(PlagiarismStatus.NONE);
        plagiarismComparison.setSubmissionA(submissionA);
        plagiarismComparisonRepository.save(plagiarismComparison);
        var plagiarismComparisonStatus = new PlagiarismComparisonStatusDTO();
        plagiarismComparisonStatus.setStatus(PlagiarismStatus.CONFIRMED);

        request.put("/api/plagiarism-comparisons/" + plagiarismComparison.getId() + "/status?finalDecision=true&studentLogin=student1", plagiarismComparisonStatus, HttpStatus.OK);
        var updatedPlagiarismComparison = plagiarismComparisonRepository.findByIdElseThrow(plagiarismComparison.getId());
        assertThat(updatedPlagiarismComparison.getStatusA()).as("should update status for studentA").isEqualTo(PlagiarismStatus.CONFIRMED);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updatePlagiarismComparisonStatusToFinalForStudentB() throws Exception {
        Course course = database.addCourseWithOneFinishedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseId(course.getId()).get(0);
        TextPlagiarismResult textPlagiarismResult = database.createTextPlagiarismResultForExercise(textExercise);
        PlagiarismComparison<TextSubmissionElement> plagiarismComparison = new PlagiarismComparison<>();
        PlagiarismSubmission<TextSubmissionElement> submissionA = new PlagiarismSubmission<>();
        submissionA.setStudentLogin("student1");
        PlagiarismSubmission<TextSubmissionElement> submissionB = new PlagiarismSubmission<>();
        submissionB.setStudentLogin("student2");
        plagiarismComparison.setInstructorStatementB(INSTRUCTOR_STATEMENT_B);
        plagiarismComparison.setPlagiarismResult(textPlagiarismResult);
        plagiarismComparison.setStatusB(PlagiarismStatus.NONE);
        plagiarismComparison.setSubmissionA(submissionA);
        plagiarismComparison.setSubmissionB(submissionB);
        plagiarismComparisonRepository.save(plagiarismComparison);
        var plagiarismComparisonStatus = new PlagiarismComparisonStatusDTO();
        plagiarismComparisonStatus.setStatus(PlagiarismStatus.CONFIRMED);

        request.put("/api/plagiarism-comparisons/" + plagiarismComparison.getId() + "/status?finalDecision=true&studentLogin=student2", plagiarismComparisonStatus, HttpStatus.OK);
        var updatedPlagiarismComparison = plagiarismComparisonRepository.findByIdElseThrow(plagiarismComparison.getId());
        assertThat(updatedPlagiarismComparison.getStatusB()).as("should update status for studentB").isEqualTo(PlagiarismStatus.CONFIRMED);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updatePlagiarismComparisonStatusToFinalForUnknownStudent() throws Exception {
        Course course = database.addCourseWithOneFinishedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseId(course.getId()).get(0);
        TextPlagiarismResult textPlagiarismResult = database.createTextPlagiarismResultForExercise(textExercise);
        PlagiarismComparison<TextSubmissionElement> plagiarismComparison = new PlagiarismComparison<>();
        PlagiarismSubmission<TextSubmissionElement> submissionA = new PlagiarismSubmission<>();
        submissionA.setStudentLogin("student1");
        PlagiarismSubmission<TextSubmissionElement> submissionB = new PlagiarismSubmission<>();
        submissionB.setStudentLogin("student2");
        plagiarismComparison.setInstructorStatementB(INSTRUCTOR_STATEMENT_B);
        plagiarismComparison.setPlagiarismResult(textPlagiarismResult);
        plagiarismComparison.setStatusB(PlagiarismStatus.NONE);
        plagiarismComparison.setSubmissionA(submissionA);
        plagiarismComparison.setSubmissionB(submissionB);
        plagiarismComparisonRepository.save(plagiarismComparison);

        request.put("/api/plagiarism-comparisons/" + plagiarismComparison.getId() + "/status?finalDecision=true&studentLogin=student23", PlagiarismComparisonStatusDTO.class,
            HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void getPlagiarismCasesForCourse_student() throws Exception {
        request.getList("/api/plagiarism-cases/1", HttpStatus.FORBIDDEN, PlagiarismCaseDTO.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TUTOR")
    public void getPlagiarismCasesForCourse_tutor() throws Exception {
        request.getList("/api/plagiarism-cases/1", HttpStatus.FORBIDDEN, PlagiarismCaseDTO.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getPlagiarismCasesForCourse_instructor() throws Exception {
        Course course = database.addCourseWithOneFinishedTextExercise();
        TextExercise textExercise = textExerciseRepository.findByCourseId(course.getId()).get(0);
        TextPlagiarismResult textPlagiarismResult = database.createTextPlagiarismResultForExercise(textExercise);
        PlagiarismComparison<TextSubmissionElement> plagiarismComparison1 = new PlagiarismComparison<>();
        plagiarismComparison1.setPlagiarismResult(textPlagiarismResult);
        plagiarismComparison1.setStatus(PlagiarismStatus.CONFIRMED);
        PlagiarismComparison<TextSubmissionElement> plagiarismComparison2 = new PlagiarismComparison<>();
        plagiarismComparison2.setPlagiarismResult(textPlagiarismResult);
        plagiarismComparison2.setStatus(PlagiarismStatus.CONFIRMED);
        plagiarismComparisonRepository.save(plagiarismComparison1);
        plagiarismComparisonRepository.save(plagiarismComparison2);

        var cases = request.getList("/api/plagiarism-cases/" + course.getId(), HttpStatus.OK, PlagiarismCaseDTO.class);
        assertThat(cases.size()).isEqualTo(1);
        assertThat(cases.get(0).getComparisons().size()).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void sendStatementForStudentA() throws Exception {
        PlagiarismComparison<TextSubmissionElement> plagiarismComparison = new PlagiarismComparison<>();
        var notification = new SingleUserNotification();
        notification.setRecipient(database.getUserByLogin("student1"));
        plagiarismComparison.setInstructorStatementA(INSTRUCTOR_STATEMENT_A);
        plagiarismComparisonRepository.save(plagiarismComparison);
        var statement = new PlagiarismResource.PlagiarismStatementDTO();
        statement.statement = "test statement";

        request.put("/api/plagiarism-cases/" + plagiarismComparison.getId() + "/statement", statement, HttpStatus.OK);
        var comparison = plagiarismComparisonRepository.findByIdElseThrow(plagiarismComparison.getId());
        assertThat(comparison.getStudentStatementA()).as("should update statement").isEqualTo("test statement");
    }

    @Test
    @WithMockUser(username = "student2", roles = "USER")
    public void sendStatementForStudentB() throws Exception {
        PlagiarismComparison<TextSubmissionElement> plagiarismComparison = new PlagiarismComparison<>();
        var notification = new SingleUserNotification();
        notification.setRecipient(database.getUserByLogin("student2"));
        plagiarismComparison.setInstructorStatementB(INSTRUCTOR_STATEMENT_B);
        plagiarismComparisonRepository.save(plagiarismComparison);
        var statement = new PlagiarismResource.PlagiarismStatementDTO();
        statement.statement = "test statement";

        request.put("/api/plagiarism-cases/" + plagiarismComparison.getId() + "/statement", statement, HttpStatus.OK);
        var comparison = plagiarismComparisonRepository.findByIdElseThrow(plagiarismComparison.getId());
        assertThat(comparison.getStudentStatementB()).as("should update statement").isEqualTo("test statement");
    }

    @Test
    @WithMockUser(username = "student3", roles = "USER")
    public void sendStatementForUnknownStudent() throws Exception {
        PlagiarismComparison<TextSubmissionElement> plagiarismComparison = new PlagiarismComparison<>();
        var notificationA = new SingleUserNotification();
        notificationA.setRecipient(database.getUserByLogin("student1"));
        plagiarismComparison.setInstructorStatementA(INSTRUCTOR_STATEMENT_A);
        var notificationB = new SingleUserNotification();
        notificationB.setRecipient(database.getUserByLogin("student2"));
        plagiarismComparison.setInstructorStatementB(INSTRUCTOR_STATEMENT_B);
        plagiarismComparisonRepository.save(plagiarismComparison);
        var statement = new PlagiarismResource.PlagiarismStatementDTO();
        statement.statement = "test statement";

        request.put("/api/plagiarism-cases/" + plagiarismComparison.getId() + "/statement", statement, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void sendPlagiarismNotificationForStudentA() throws Exception {
        PlagiarismComparison<TextSubmissionElement> plagiarismComparison = new PlagiarismComparison<>();
        PlagiarismSubmission<TextSubmissionElement> submissionA = new PlagiarismSubmission<>();
        submissionA.setStudentLogin("student1");
        plagiarismComparison.setSubmissionA(submissionA);
        plagiarismComparisonRepository.save(plagiarismComparison);
        var notification = new PlagiarismNotificationDTO("student1", plagiarismComparison.getId(), "test message");

        var returnedNotification = request.putWithResponseBody("/api/plagiarism-cases/notification", notification, Notification.class, HttpStatus.OK);
        assertThat(returnedNotification.getText()).as("Notification for student should be saved").isEqualTo(notification.getInstructorMessage());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void sendPlagiarismNotificationForStudentB() throws Exception {
        PlagiarismComparison<TextSubmissionElement> plagiarismComparison = new PlagiarismComparison<>();
        PlagiarismSubmission<TextSubmissionElement> submissionA = new PlagiarismSubmission<>();
        PlagiarismSubmission<TextSubmissionElement> submissionB = new PlagiarismSubmission<>();
        submissionA.setStudentLogin("student1");
        submissionB.setStudentLogin("student2");
        plagiarismComparison.setSubmissionA(submissionA);
        plagiarismComparison.setSubmissionB(submissionB);
        plagiarismComparisonRepository.save(plagiarismComparison);
        var notification = new PlagiarismNotificationDTO("student2", plagiarismComparison.getId(), "test message");

        var returnedNotification = request.putWithResponseBody("/api/plagiarism-cases/notification", notification, Notification.class, HttpStatus.OK);
        assertThat(returnedNotification.getText()).as("Notification for student should be saved").isEqualTo(notification.getInstructorMessage());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void sendPlagiarismNotificationForUnknownStudent() throws Exception {
        PlagiarismComparison<TextSubmissionElement> plagiarismComparison = new PlagiarismComparison<>();
        PlagiarismSubmission<TextSubmissionElement> submissionA = new PlagiarismSubmission<>();
        PlagiarismSubmission<TextSubmissionElement> submissionB = new PlagiarismSubmission<>();
        submissionA.setStudentLogin("student1");
        submissionB.setStudentLogin("student2");
        plagiarismComparison.setSubmissionA(submissionA);
        plagiarismComparison.setSubmissionB(submissionB);
        plagiarismComparisonRepository.save(plagiarismComparison);
        var notification = new PlagiarismNotificationDTO("student3", plagiarismComparison.getId(), "test message");

        request.put("/api/plagiarism-cases/notification", notification, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void sendPlagiarismNotification_notFound_instructor() throws Exception {
        request.put("/api/plagiarism-cases/notification", new PlagiarismNotificationDTO("student1", 12345L, "test message"), HttpStatus.NOT_FOUND);
    }
}
