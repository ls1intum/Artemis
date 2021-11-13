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
import de.tum.in.www1.artemis.web.rest.dto.PlagiarismNotificationDTO;

public class PlagiarismIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private PlagiarismComparisonRepository plagiarismComparisonRepository;

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @Autowired
    private ModelingExerciseRepository modelingExerciseRepository;

    @BeforeEach
    public void initTestCase() {
        database.addUsers(2, 1, 1, 1);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void getAnonymousPlagiarismComparison() {
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
        PlagiarismComparison<TextSubmissionElement> plagiarismComparison1 = new PlagiarismComparison<TextSubmissionElement>();
        plagiarismComparison1.setPlagiarismResult(textPlagiarismResult);
        plagiarismComparison1.setStatus(PlagiarismStatus.CONFIRMED);
        PlagiarismComparison<TextSubmissionElement> plagiarismComparison2 = new PlagiarismComparison<TextSubmissionElement>();
        plagiarismComparison2.setPlagiarismResult(textPlagiarismResult);
        plagiarismComparison2.setStatus(PlagiarismStatus.CONFIRMED);
        plagiarismComparisonRepository.save(plagiarismComparison1);
        plagiarismComparisonRepository.save(plagiarismComparison2);

        var cases = request.getList("/api/plagiarism-cases/1", HttpStatus.OK, PlagiarismCaseDTO.class);
        assertThat(cases.size()).isEqualTo(1);
        assertThat(cases.get(0).getComparisons().size()).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void sendStatement() throws Exception {
        PlagiarismComparison<TextSubmissionElement> plagiarismComparison = new PlagiarismComparison<TextSubmissionElement>();
        var notification = new SingleUserNotification();
        notification.setRecipient(database.getUserByLogin("student1"));
        plagiarismComparison.setNotificationA(notification);
        plagiarismComparisonRepository.save(plagiarismComparison);
        var statement = new PlagiarismResource.PlagiarismStatementDTO();
        statement.statement = "test statement";

        request.put("/api/plagiarism-cases/" + plagiarismComparison.getId() + "/statement", statement, HttpStatus.OK);
        var comparison = plagiarismComparisonRepository.findByIdElseThrow(plagiarismComparison.getId());
        assertThat(comparison.getStatementA()).as("should update statement").isEqualTo("test statement");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void sendPlagiarismNotification() throws Exception {
        PlagiarismComparison<TextSubmissionElement> plagiarismComparison = new PlagiarismComparison<TextSubmissionElement>();
        PlagiarismSubmission<TextSubmissionElement> submissionA = new PlagiarismSubmission<TextSubmissionElement>();
        submissionA.setStudentLogin("student1");
        plagiarismComparison.setSubmissionA(submissionA);
        plagiarismComparisonRepository.save(plagiarismComparison);
        var notification = new PlagiarismNotificationDTO("student1", plagiarismComparison.getId(), "test message");

        var returnedNotification = request.putWithResponseBody("/api/plagiarism-cases/notification", notification, Notification.class, HttpStatus.OK);
        assertThat(returnedNotification.getText()).as("Notification for student should be saved").isEqualTo(notification.getInstructorMessage());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void sendPlagiarismNotification_notFound_instructor() throws Exception {
        request.put("/api/plagiarism-cases/notification", new PlagiarismNotificationDTO("student1", 12345L, "test message"), HttpStatus.NOT_FOUND);
    }
}
