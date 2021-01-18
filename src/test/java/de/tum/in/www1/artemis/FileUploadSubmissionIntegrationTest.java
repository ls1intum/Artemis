package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.FileUploadExercise;
import de.tum.in.www1.artemis.domain.FileUploadSubmission;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.exception.FilePathParsingException;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.FileService;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.util.ModelFactory;

public class FileUploadSubmissionIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    CourseRepository courseRepo;

    @Autowired
    ExerciseRepository exerciseRepo;

    @Autowired
    UserRepository userRepo;

    @Autowired
    ParticipationService participationService;

    @Autowired
    FileService fileService;

    @Autowired
    ResultRepository resultRepo;

    @Autowired
    FileUploadSubmissionRepository fileUploadSubmissionRepository;

    @Autowired
    ParticipationRepository participationRepository;

    private FileUploadExercise releasedFileUploadExercise;

    private FileUploadExercise finishedFileUploadExercise;

    private FileUploadSubmission submittedFileUploadSubmission;

    private FileUploadSubmission notSubmittedFileUploadSubmission;

    private FileUploadSubmission lateFileUploadSubmission;

    private final MockMultipartFile validFile = new MockMultipartFile("file", "file.png", "application/json", "some data".getBytes());

    private StudentParticipation participation;

    @BeforeEach
    public void initTestCase() throws Exception {
        database.addUsers(3, 1, 1);
        Course course = database.addCourseWithThreeFileUploadExercise();
        releasedFileUploadExercise = database.findFileUploadExerciseWithTitle(course.getExercises(), "released");
        finishedFileUploadExercise = database.findFileUploadExerciseWithTitle(course.getExercises(), "finished");
        submittedFileUploadSubmission = ModelFactory.generateFileUploadSubmission(true);
        notSubmittedFileUploadSubmission = ModelFactory.generateFileUploadSubmission(false);
        lateFileUploadSubmission = ModelFactory.generateLateFileUploadSubmission();
        database.createAndSaveParticipationForExercise(releasedFileUploadExercise, "student3");
        participation = database.createAndSaveParticipationForExercise(finishedFileUploadExercise, "student3");
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(value = "student3")
    public void submitFileUploadSubmission() throws Exception {
        submitFile();
    }

    private void submitFile() throws Exception {
        FileUploadSubmission submission = ModelFactory.generateFileUploadSubmission(false);
        FileUploadSubmission returnedSubmission = performInitialSubmission(releasedFileUploadExercise.getId(), submission);
        String actualFilePath = Paths.get(FileUploadSubmission.buildFilePath(releasedFileUploadExercise.getId(), returnedSubmission.getId()), "file.png").toString();
        String publicFilePath = fileService.publicPathForActualPath(actualFilePath, returnedSubmission.getId());
        assertThat(returnedSubmission).as("submission correctly posted").isNotNull();
        assertThat(returnedSubmission.getFilePath()).isEqualTo(publicFilePath);
        var fileBytes = Files.readAllBytes(Paths.get(actualFilePath));
        assertThat(fileBytes.length > 0).as("Stored file has content").isTrue();
        checkDetailsHidden(returnedSubmission, true);
    }

    @Test
    @WithMockUser(value = "student3")
    public void submitFileUploadSubmissionTwiceSameFile() throws Exception {
        submitFile();
        submitFile();

        // TODO: upload a real file from the file system twice with the same and with different names and test both works correctly
    }

    @Test
    @WithMockUser(value = "student1")
    public void submitFileUploadSubmission_wrongExercise() throws Exception {
        FileUploadSubmission submission = ModelFactory.generateFileUploadSubmission(false);
        var file = new MockMultipartFile("file", "file.png", "application/json", "some data".getBytes());
        request.postWithMultipartFile("/api/exercises/" + releasedFileUploadExercise.getId() + "/file-upload-submissions", submission, "submission", file,
                FileUploadSubmission.class, HttpStatus.FAILED_DEPENDENCY);
    }

    @Test
    @WithMockUser(value = "student1")
    public void submitFileUploadSubmission_withoutParticipation() throws Exception {
        FileUploadSubmission submission = ModelFactory.generateFileUploadSubmission(false);
        var file = new MockMultipartFile("file", "file.png", "application/json", "some data".getBytes());
        request.postWithMultipartFile("/api/exercises/" + releasedFileUploadExercise.getId() + "/file-upload-submissions", submission, "submission", file,
                FileUploadSubmission.class, HttpStatus.FAILED_DEPENDENCY);
    }

    @Test
    @WithMockUser(value = "student3")
    public void submitFileUploadSubmission_emptyFileContent() throws Exception {
        FileUploadSubmission submission = ModelFactory.generateFileUploadSubmission(false);
        var file = new MockMultipartFile("file", "file.png", "application/json", (byte[]) null);
        request.postWithMultipartFile("/api/exercises/" + releasedFileUploadExercise.getId() + "/file-upload-submissions", submission, "submission", file,
                FileUploadSubmission.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(value = "student1")
    public void setSubmittedFileUploadSubmission_incorrectFileType() throws Exception {
        FileUploadSubmission submission = ModelFactory.generateFileUploadSubmission(false);
        var file = new MockMultipartFile("file", "file.txt", "application/json", "some data".getBytes());
        request.postWithMultipartFile("/api/exercises/" + releasedFileUploadExercise.getId() + "/file-upload-submissions", submission, "submission", file,
                FileUploadSubmission.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getAllSubmissionsOfExercise() throws Exception {
        FileUploadSubmission submission1 = database.addFileUploadSubmission(releasedFileUploadExercise, notSubmittedFileUploadSubmission, "student1");
        FileUploadSubmission submission2 = database.addFileUploadSubmission(releasedFileUploadExercise, submittedFileUploadSubmission, "student2");

        List<FileUploadSubmission> submissions = request.getList("/api/exercises/" + releasedFileUploadExercise.getId() + "/file-upload-submissions", HttpStatus.OK,
                FileUploadSubmission.class);

        assertThat(submissions).as("contains both submissions").containsExactlyInAnyOrder(submission1, submission2);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void cannotSeeStudentDetailsInSubmissionListAsTutor() throws Exception {
        FileUploadSubmission submission1 = database.saveFileUploadSubmissionWithResultAndAssessor(releasedFileUploadExercise, submittedFileUploadSubmission, "student1", "tutor1");

        List<FileUploadSubmission> submissions = request.getList("/api/exercises/" + releasedFileUploadExercise.getId() + "/file-upload-submissions?assessedByTutor=true",
                HttpStatus.OK, FileUploadSubmission.class);

        assertThat(submissions.size()).as("one file upload submission was found").isEqualTo(1);
        assertThat(submissions.get(0).getId()).as("correct file upload submission was found").isEqualTo(submission1.getId());
        final StudentParticipation participation1 = (StudentParticipation) submissions.get(0).getParticipation();
        assertThat(participation1.getStudent()).as("contains no student details").isEmpty();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void canSeeStudentDetailsInSubmissionListAsInstructor() throws Exception {
        FileUploadSubmission submission1 = database.addFileUploadSubmission(releasedFileUploadExercise, submittedFileUploadSubmission, "student1");

        List<FileUploadSubmission> submissions = request.getList("/api/exercises/" + releasedFileUploadExercise.getId() + "/file-upload-submissions?submittedOnly=true",
                HttpStatus.OK, FileUploadSubmission.class);

        assertThat(submissions.size()).as("one file upload submission was found").isEqualTo(1);
        assertThat(submissions.get(0).getId()).as("correct file upload submission was found").isEqualTo(submission1.getId());
        final StudentParticipation participation1 = (StudentParticipation) submissions.get(0).getParticipation();
        assertThat(participation1.getStudent()).as("contains student details").isNotEmpty();
    }

    @Test
    @WithMockUser(value = "student1")
    public void getAllSubmissionsOfExerciseAsStudent() throws Exception {
        database.addFileUploadSubmission(releasedFileUploadExercise, submittedFileUploadSubmission, "student1");

        request.getList("/api/exercises/" + releasedFileUploadExercise.getId() + "/file-upload-submissions", HttpStatus.FORBIDDEN, ModelingSubmission.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getAllSubmittedSubmissionsOfExercise() throws Exception {
        FileUploadSubmission submission1 = database.addFileUploadSubmission(releasedFileUploadExercise, submittedFileUploadSubmission, "student1");
        database.addFileUploadSubmission(releasedFileUploadExercise, notSubmittedFileUploadSubmission, "student2");

        List<FileUploadSubmission> submissions = request.getList("/api/exercises/" + releasedFileUploadExercise.getId() + "/file-upload-submissions?submittedOnly=true",
                HttpStatus.OK, FileUploadSubmission.class);

        assertThat(submissions).as("contains only submitted submission").containsExactlyInAnyOrder(submission1);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getSubmissionWithoutAssessment() throws Exception {
        FileUploadSubmission submission = database.addFileUploadSubmission(releasedFileUploadExercise, submittedFileUploadSubmission, "student1");
        database.addFileUploadSubmission(releasedFileUploadExercise, lateFileUploadSubmission, "student2"); // tests prioritizing in-time submissions over late submissions

        database.updateExerciseDueDate(releasedFileUploadExercise.getId(), ZonedDateTime.now().minusHours(1));

        assertThat(submittedFileUploadSubmission.getSubmissionDate()).as("first submission is in-time").isBefore(releasedFileUploadExercise.getDueDate());
        assertThat(lateFileUploadSubmission.getSubmissionDate()).as("second submission is late").isAfter(releasedFileUploadExercise.getDueDate());

        FileUploadSubmission storedSubmission = request.get("/api/exercises/" + releasedFileUploadExercise.getId() + "/file-upload-submission-without-assessment", HttpStatus.OK,
                FileUploadSubmission.class);

        assertThat(storedSubmission).as("in-time submission was found").isEqualToIgnoringGivenFields(submission, "result", "submissionDate", "fileService");
        assertThat(storedSubmission.getLatestResult()).as("result is not set").isNull();
        checkDetailsHidden(storedSubmission, false);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getLateSubmissionWithoutAssessment() throws Exception {

        database.saveFileUploadSubmissionWithResultAndAssessor(releasedFileUploadExercise, submittedFileUploadSubmission, "student1", "tutor1");
        FileUploadSubmission lateSubmission = database.addFileUploadSubmission(releasedFileUploadExercise, lateFileUploadSubmission, "student1");

        database.updateExerciseDueDate(releasedFileUploadExercise.getId(), ZonedDateTime.now().minusHours(1));

        assertThat(submittedFileUploadSubmission.getSubmissionDate()).as("first submission is in-time").isBefore(releasedFileUploadExercise.getDueDate());
        assertThat(lateFileUploadSubmission.getSubmissionDate()).as("second submission is late").isAfter(releasedFileUploadExercise.getDueDate());

        FileUploadSubmission storedSubmission = request.get("/api/exercises/" + releasedFileUploadExercise.getId() + "/file-upload-submission-without-assessment", HttpStatus.OK,
                FileUploadSubmission.class);

        assertThat(storedSubmission).as("submission was found").isEqualToIgnoringGivenFields(lateSubmission, "result", "submissionDate", "fileService");
        assertThat(storedSubmission.getLatestResult()).as("result is not set").isNull();
        checkDetailsHidden(storedSubmission, false);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void testGetLateSubmissionWithoutAssessmentLock() throws Exception {

        database.saveFileUploadSubmissionWithResultAndAssessor(releasedFileUploadExercise, submittedFileUploadSubmission, "student1", "tutor1");
        FileUploadSubmission lateSubmission = database.addFileUploadSubmission(releasedFileUploadExercise, lateFileUploadSubmission, "student1");

        database.updateExerciseDueDate(releasedFileUploadExercise.getId(), ZonedDateTime.now().minusHours(1));

        assertThat(submittedFileUploadSubmission.getSubmissionDate()).as("first submission is in-time").isBefore(releasedFileUploadExercise.getDueDate());
        assertThat(lateFileUploadSubmission.getSubmissionDate()).as("second submission is late").isAfter(releasedFileUploadExercise.getDueDate());

        FileUploadSubmission storedSubmission = request.get("/api/exercises/" + releasedFileUploadExercise.getId() + "/file-upload-submission-without-assessment?lock=true",
                HttpStatus.OK, FileUploadSubmission.class);

        assertThat(storedSubmission).as("submission was found").isEqualToIgnoringGivenFields(lateSubmission, "results", "submissionDate", "fileService");
        assertThat(storedSubmission.getLatestResult()).as("result is set").isNotNull();
        checkDetailsHidden(storedSubmission, false);
    }

    @Test
    @WithMockUser(value = "student1")
    public void getFileUploadSubmissionWithoutAssessment_asStudent_forbidden() throws Exception {
        database.addFileUploadSubmission(releasedFileUploadExercise, submittedFileUploadSubmission, "student1");
        database.updateExerciseDueDate(releasedFileUploadExercise.getId(), ZonedDateTime.now().minusHours(1));

        request.get("/api/exercises/" + releasedFileUploadExercise.getId() + "/file-upload-submission-without-assessment", HttpStatus.FORBIDDEN, FileUploadSubmission.class);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getFileUploadSubmissionWithoutAssessment_inFuture() throws Exception {
        database.updateExerciseDueDate(releasedFileUploadExercise.getId(), ZonedDateTime.now().plusHours(1));

        request.get("/api/exercises/" + releasedFileUploadExercise.getId() + "/file-upload-submission-without-assessment", HttpStatus.FORBIDDEN, FileUploadSubmission.class);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getFileUploadSubmissionWithoutAssessment_wrongExerciseType() throws Exception {
        Course course = database.addCourseWithOneModelingExercise();
        ModelingExercise modelingExercise = database.findModelingExerciseWithTitle(course.getExercises(), "ClassDiagram");
        request.get("/api/exercises/" + modelingExercise.getId() + "/file-upload-submission-without-assessment", HttpStatus.BAD_REQUEST, FileUploadSubmission.class);
    }

    @Test
    @WithMockUser(value = "student1")
    public void getDataForFileUpload() throws Exception {
        FileUploadSubmission fileUploadSubmission = ModelFactory.generateFileUploadSubmission(true);
        List<Feedback> feedbacks = ModelFactory.generateFeedback();
        fileUploadSubmission = database.saveFileUploadSubmissionWithResultAndAssessorFeedback(releasedFileUploadExercise, fileUploadSubmission, "student1", "tutor1", feedbacks);
        database.updateExerciseDueDate(releasedFileUploadExercise.getId(), ZonedDateTime.now().minusHours(1));

        FileUploadSubmission submission = request.get("/api/participations/" + fileUploadSubmission.getParticipation().getId() + "/file-upload-editor", HttpStatus.OK,
                FileUploadSubmission.class);
        assertThat(submission).isNotNull();
        assertThat(submission.getLatestResult()).isNotNull();
        assertThat(submission.isSubmitted()).isTrue();
        assertThat(submission.getLatestResult().getFeedbacks().size()).as("No feedback should be returned for editor").isEqualTo(0);
    }

    @Test
    @WithMockUser(value = "student1")
    public void getDataForFileUpload_withoutFinishedAssessment() throws Exception {
        FileUploadSubmission fileUploadSubmission = ModelFactory.generateFileUploadSubmission(true);
        List<Feedback> feedbacks = ModelFactory.generateFeedback();
        fileUploadSubmission = database.saveFileUploadSubmissionWithResultAndAssessorFeedback(releasedFileUploadExercise, fileUploadSubmission, "student1", "tutor1", feedbacks);
        database.updateResultCompletionDate(fileUploadSubmission.getLatestResult().getId(), null);
        database.updateExerciseDueDate(releasedFileUploadExercise.getId(), ZonedDateTime.now().minusHours(1));

        FileUploadSubmission submission = request.get("/api/participations/" + fileUploadSubmission.getParticipation().getId() + "/file-upload-editor", HttpStatus.OK,
                FileUploadSubmission.class);
        assertThat(submission.getLatestResult()).isNull();
    }

    @Test
    @WithMockUser(value = "student1")
    public void getDataForFileUpload_wrongStudent() throws Exception {
        FileUploadSubmission fileUploadSubmission = ModelFactory.generateFileUploadSubmission(true);
        List<Feedback> feedbacks = ModelFactory.generateFeedback();
        fileUploadSubmission = database.saveFileUploadSubmissionWithResultAndAssessorFeedback(releasedFileUploadExercise, fileUploadSubmission, "student2", "tutor1", feedbacks);
        database.updateExerciseDueDate(releasedFileUploadExercise.getId(), ZonedDateTime.now().minusHours(1));

        FileUploadSubmission submission = request.get("/api/participations/" + fileUploadSubmission.getParticipation().getId() + "/file-upload-editor", HttpStatus.FORBIDDEN,
                FileUploadSubmission.class);
        assertThat(submission).isNull();
    }

    @Test
    @WithMockUser(value = "student1")
    public void getDataForFileUpload_wrongExcerciseType() throws Exception {
        Course course = database.addCourseWithOneModelingExercise();
        ModelingExercise modelingExercise = database.findModelingExerciseWithTitle(course.getExercises(), "ClassDiagram");
        Participation modelingExerciseParticipation = database.createAndSaveParticipationForExercise(modelingExercise, "student1");
        FileUploadSubmission submission = request.get("/api/participations/" + modelingExerciseParticipation.getId() + "/file-upload-editor", HttpStatus.BAD_REQUEST,
                FileUploadSubmission.class);
        assertThat(submission).isNull();
    }

    @Test
    @WithMockUser(value = "student3", roles = "USER")
    public void submitExercise_afterDueDate_forbidden() throws Exception {
        participation.setInitializationDate(ZonedDateTime.now().minusDays(2));
        participationRepository.save(participation);
        request.postWithMultipartFile("/api/exercises/" + finishedFileUploadExercise.getId() + "/file-upload-submissions", submittedFileUploadSubmission, "submission", validFile,
                FileUploadSubmission.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(value = "student3", roles = "USER")
    public void submitExercise_beforeDueDate_allowed() throws Exception {
        request.postWithMultipartFile("/api/exercises/" + releasedFileUploadExercise.getId() + "/file-upload-submissions", submittedFileUploadSubmission, "submission", validFile,
                FileUploadSubmission.class, HttpStatus.OK);
    }

    @Test
    @WithMockUser(value = "student3", roles = "USER")
    public void submitExercise_afterDueDateWithParticipationStartAfterDueDate_allowed() throws Exception {
        request.postWithMultipartFile("/api/exercises/" + finishedFileUploadExercise.getId() + "/file-upload-submissions", submittedFileUploadSubmission, "submission", validFile,
                FileUploadSubmission.class, HttpStatus.OK);
    }

    @Test
    @WithMockUser(value = "student3", roles = "USER")
    public void submitExercise_beforeDueDateSecondSubmission_allowed() throws Exception {
        var file = new MockMultipartFile("file", "ffile.png", "application/json", "some data".getBytes());
        submittedFileUploadSubmission = request.postWithMultipartFile("/api/exercises/" + releasedFileUploadExercise.getId() + "/file-upload-submissions",
                submittedFileUploadSubmission, "submission", file, FileUploadSubmission.class, HttpStatus.OK);

        final var submissionInDb = fileUploadSubmissionRepository.findById(submittedFileUploadSubmission.getId());
        assertThat(submissionInDb.isPresent()).isTrue();
        assertThat(submissionInDb.get().getFilePath().contains("ffile.png")).isTrue();
    }

    private FileUploadSubmission performInitialSubmission(Long exerciseId, FileUploadSubmission submission) throws Exception {
        var file = new MockMultipartFile("file", "file.png", "application/json", "some data".getBytes());
        return request.postWithMultipartFile("/api/exercises/" + exerciseId + "/file-upload-submissions", submission, "submission", file, FileUploadSubmission.class,
                HttpStatus.OK);
    }

    private void checkDetailsHidden(FileUploadSubmission submission, boolean isStudent) {
        assertThat(submission.getParticipation().getResults()).isNullOrEmpty();
        if (isStudent) {
            assertThat(submission.getLatestResult()).isNull();
        }
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getSubmissionByID_asTA() throws Exception {
        FileUploadSubmission fileUploadSubmission = ModelFactory.generateFileUploadSubmission(true);
        fileUploadSubmission = database.addFileUploadSubmission(releasedFileUploadExercise, fileUploadSubmission, "student1");

        long submissionID = fileUploadSubmission.getId();
        FileUploadSubmission receivedSubmission = request.get("/api/file-upload-submissions/" + submissionID, HttpStatus.OK, FileUploadSubmission.class);

        assertThat(receivedSubmission.getId()).isEqualTo(submissionID);
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void getSubmissionByID_asStudent() throws Exception {
        FileUploadSubmission fileUploadSubmission = ModelFactory.generateFileUploadSubmission(true);
        fileUploadSubmission = database.addFileUploadSubmission(releasedFileUploadExercise, fileUploadSubmission, "student1");

        long submissionID = fileUploadSubmission.getId();
        FileUploadSubmission receivedSubmission = request.get("/api/file-upload-submissions/" + submissionID, HttpStatus.FORBIDDEN, FileUploadSubmission.class);

        assertThat(receivedSubmission).isNull();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testDeleteSubmission() {
        submittedFileUploadSubmission.setFilePath("/api/files/file-upload-exercises/769/submissions/406062/Pinguin.pdf");
        fileUploadSubmissionRepository.save(submittedFileUploadSubmission);
        fileUploadSubmissionRepository.delete(submittedFileUploadSubmission);
        assertThat(fileUploadSubmissionRepository.findAll()).doesNotContain(submittedFileUploadSubmission);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testOnDeleteSubmission() {
        submittedFileUploadSubmission.setFilePath("/api/files/file-upload-exercises/769/submissions/406062/Pinguin.pdf");
        fileUploadSubmissionRepository.save(submittedFileUploadSubmission);
        Assertions.assertDoesNotThrow(() -> submittedFileUploadSubmission.onDelete());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testOnDeleteSubmissionWithException() {
        submittedFileUploadSubmission.setFilePath("/api/files/file-upload-exercises");
        fileUploadSubmissionRepository.save(submittedFileUploadSubmission);
        Assertions.assertThrows(FilePathParsingException.class, () -> submittedFileUploadSubmission.onDelete());
    }
}
