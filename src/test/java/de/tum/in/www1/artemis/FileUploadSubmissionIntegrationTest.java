package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.exception.FilePathParsingException;
import de.tum.in.www1.artemis.repository.FileUploadSubmissionRepository;
import de.tum.in.www1.artemis.repository.ParticipationRepository;
import de.tum.in.www1.artemis.util.ModelFactory;

public class FileUploadSubmissionIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private FileUploadSubmissionRepository fileUploadSubmissionRepository;

    @Autowired
    private ParticipationRepository participationRepository;

    private FileUploadExercise releasedFileUploadExercise;

    private FileUploadExercise finishedFileUploadExercise;

    private FileUploadExercise assessedFileUploadExercise;

    private FileUploadExercise noDueDateFileUploadExercise;

    private FileUploadSubmission submittedFileUploadSubmission;

    private FileUploadSubmission notSubmittedFileUploadSubmission;

    private FileUploadSubmission lateFileUploadSubmission;

    private final MockMultipartFile validFile = new MockMultipartFile("file", "file.png", "application/json", "some data".getBytes());

    private StudentParticipation participation;

    @BeforeEach
    public void initTestCase() throws Exception {
        database.addUsers(3, 1, 0, 1);
        Course course = database.addCourseWithFourFileUploadExercise();
        releasedFileUploadExercise = database.findFileUploadExerciseWithTitle(course.getExercises(), "released");
        finishedFileUploadExercise = database.findFileUploadExerciseWithTitle(course.getExercises(), "finished");
        assessedFileUploadExercise = database.findFileUploadExerciseWithTitle(course.getExercises(), "assessed");
        noDueDateFileUploadExercise = database.findFileUploadExerciseWithTitle(course.getExercises(), "noDueDate");
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
        submitFile("file.png", false);
    }

    @Test
    @WithMockUser(value = "student3")
    public void submitFileUploadSubmissionWithShortName() throws Exception {
        submitFile(".png", false);
    }

    @Test
    @WithMockUser(value = "student3")
    public void submitFileUploadSubmissionWithDoubleBackslash() throws Exception {
        submitFile("file\\file.png", false);
    }

    @Test
    @WithMockUser(value = "student3")
    public void submitFileUploadSubmissionDifferentFile() throws Exception {
        submitFile("file2.png", true);
    }

    @Test
    @WithMockUser(value = "student3")
    public void submitFileUploadSubmissionTwiceSameFile() throws Exception {
        submitFile("file.png", false);
        submitFile("file.png", false);

        // TODO: upload a real file from the file system twice with the same and with different names and test both works correctly
    }

    private void submitFile(String filename, boolean differentFilePath) throws Exception {
        FileUploadSubmission submission = ModelFactory.generateFileUploadSubmission(false);

        if (differentFilePath) {
            submission.setFilePath("/api/files/file-upload-exercises/1/submissions/1/file1.png");
        }
        FileUploadSubmission returnedSubmission = performInitialSubmission(releasedFileUploadExercise.getId(), submission, filename);

        String actualFilePath;

        if (differentFilePath) {
            actualFilePath = Paths.get(FileUploadSubmission.buildFilePath(releasedFileUploadExercise.getId(), returnedSubmission.getId()), filename).toString();
        }
        else {
            if (filename.length() < 5) {
                actualFilePath = Paths.get(FileUploadSubmission.buildFilePath(releasedFileUploadExercise.getId(), returnedSubmission.getId()), "file" + filename).toString();
            }
            else if (filename.contains("\\")) {
                actualFilePath = Paths.get(FileUploadSubmission.buildFilePath(releasedFileUploadExercise.getId(), returnedSubmission.getId()), "file.png").toString();
            }
            else {
                actualFilePath = Paths.get(FileUploadSubmission.buildFilePath(releasedFileUploadExercise.getId(), returnedSubmission.getId()), filename).toString();
            }
        }

        String publicFilePath = fileService.publicPathForActualPath(actualFilePath, returnedSubmission.getId());
        assertThat(returnedSubmission).as("submission correctly posted").isNotNull();
        assertThat(returnedSubmission.getFilePath()).isEqualTo(publicFilePath);
        var fileBytes = Files.readAllBytes(Paths.get(actualFilePath));
        assertThat(fileBytes.length > 0).as("Stored file has content").isTrue();
        checkDetailsHidden(returnedSubmission, true);
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
    public void getSubmissionWithoutAssessmentWithoutSubmission() throws Exception {
        database.updateExerciseDueDate(releasedFileUploadExercise.getId(), ZonedDateTime.now().minusHours(1));
        assertThat(releasedFileUploadExercise.getNumberOfSubmissions()).as("no submissions").isNull();

        FileUploadSubmission storedSubmission = request.get("/api/exercises/" + releasedFileUploadExercise.getId() + "/file-upload-submission-without-assessment",
                HttpStatus.NOT_FOUND, FileUploadSubmission.class);

        assertThat(storedSubmission).as("no submission eligible for new assessment").isNull();
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
    @WithMockUser(value = "tutor1", roles = "TA")
    public void testGetSubmissionWithoutAssessmentWithoutSubmissionLock() throws Exception {
        database.updateExerciseDueDate(releasedFileUploadExercise.getId(), ZonedDateTime.now().minusHours(1));

        assertThat(releasedFileUploadExercise.getNumberOfSubmissions()).as("no submissions").isNull();

        FileUploadSubmission storedSubmission = request.get("/api/exercises/" + releasedFileUploadExercise.getId() + "/file-upload-submission-without-assessment?lock=true",
                HttpStatus.NOT_FOUND, FileUploadSubmission.class);

        assertThat(storedSubmission).as("no submission present and therefore none locked").isNull();
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
        assertThat(submission.getLatestResult()).isNotNull();
        assertThat(submission.getLatestResult().getFeedbacks()).isEmpty();
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
    public void getDataForFileUpload_wrongExerciseType() throws Exception {
        Course course = database.addCourseWithOneModelingExercise();
        ModelingExercise modelingExercise = database.findModelingExerciseWithTitle(course.getExercises(), "ClassDiagram");
        Participation modelingExerciseParticipation = database.createAndSaveParticipationForExercise(modelingExercise, "student1");
        FileUploadSubmission submission = request.get("/api/participations/" + modelingExerciseParticipation.getId() + "/file-upload-editor", HttpStatus.BAD_REQUEST,
                FileUploadSubmission.class);
        assertThat(submission).isNull();
    }

    @Test
    @WithMockUser(value = "student1")
    public void getDataForFileUpload_afterAssessmentDueDate_showsFeedback() throws Exception {
        FileUploadSubmission fileUploadSubmission = ModelFactory.generateFileUploadSubmission(true);
        List<Feedback> feedbacks = ModelFactory.generateFeedback();
        fileUploadSubmission = database.saveFileUploadSubmissionWithResultAndAssessorFeedback(assessedFileUploadExercise, fileUploadSubmission, "student1", "tutor1", feedbacks);

        FileUploadSubmission submission = request.get("/api/participations/" + fileUploadSubmission.getParticipation().getId() + "/file-upload-editor", HttpStatus.OK,
                FileUploadSubmission.class);
        assertThat(submission).isNotNull();
        assertThat(submission.getLatestResult()).isNotNull();
        assertThat(submission.isSubmitted()).isTrue();
        assertThat(submission.getLatestResult().getFeedbacks()).isEqualTo(feedbacks);
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
    public void submitExerciseWithSubmissionID() throws Exception {
        FileUploadSubmission submission = performInitialSubmission(releasedFileUploadExercise.getId(), submittedFileUploadSubmission, validFile.getOriginalFilename());
        request.postWithMultipartFile("/api/exercises/" + releasedFileUploadExercise.getId() + "/file-upload-submissions", submission, "submission", validFile,
                FileUploadSubmission.class, HttpStatus.OK);
    }

    @Test
    @WithMockUser(value = "student3", roles = "USER")
    public void submitExercise_beforeDueDate_allowed() throws Exception {
        request.postWithMultipartFile("/api/exercises/" + releasedFileUploadExercise.getId() + "/file-upload-submissions", submittedFileUploadSubmission, "submission", validFile,
                FileUploadSubmission.class, HttpStatus.OK);
    }

    @Test
    @WithMockUser(value = "student3", roles = "USER")
    public void submitExercise_afterDueDate() throws Exception {
        StudentParticipation studentParticipation = database.createAndSaveParticipationForExerciseInTheFuture(releasedFileUploadExercise, "student3");
        submittedFileUploadSubmission.setParticipation(studentParticipation);

        request.postWithMultipartFile("/api/exercises/" + releasedFileUploadExercise.getId() + "/file-upload-submissions", submittedFileUploadSubmission, "submission", validFile,
                FileUploadSubmission.class, HttpStatus.OK);
    }

    @Test
    @WithMockUser(value = "student3", roles = "USER")
    public void submitExercise_withoutDueDate() throws Exception {
        StudentParticipation studentParticipation = database.createAndSaveParticipationForExerciseInTheFuture(noDueDateFileUploadExercise, "student3");
        submittedFileUploadSubmission.setParticipation(studentParticipation);

        request.postWithMultipartFile("/api/exercises/" + noDueDateFileUploadExercise.getId() + "/file-upload-submissions", submittedFileUploadSubmission, "submission", validFile,
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
    public void saveExercise_beforeDueDate() throws Exception {
        FileUploadSubmission storedSubmission = request.postWithMultipartFile("/api/exercises/" + releasedFileUploadExercise.getId() + "/file-upload-submissions",
                notSubmittedFileUploadSubmission, "submission", validFile, FileUploadSubmission.class, HttpStatus.OK);
        assertThat(storedSubmission.isSubmitted()).isTrue();

    }

    @Test
    @WithMockUser(value = "student3", roles = "USER")
    public void saveExercise_afterDueDateWithParticipationStartAfterDueDate_allowed() throws Exception {
        FileUploadSubmission storedSubmission = request.postWithMultipartFile("/api/exercises/" + finishedFileUploadExercise.getId() + "/file-upload-submissions",
                notSubmittedFileUploadSubmission, "submission", validFile, FileUploadSubmission.class, HttpStatus.OK);
        assertThat(storedSubmission.isSubmitted()).isFalse();
    }

    @Test
    @WithMockUser(value = "student3", roles = "USER")
    public void submitExercise_beforeDueDateSecondSubmission_allowed() throws Exception {
        var file = new MockMultipartFile("file", "ffile.png", "application/json", "some data".getBytes());
        submittedFileUploadSubmission = request.postWithMultipartFile("/api/exercises/" + releasedFileUploadExercise.getId() + "/file-upload-submissions",
                submittedFileUploadSubmission, "submission", file, FileUploadSubmission.class, HttpStatus.OK);

        final var submissionInDb = fileUploadSubmissionRepository.findById(submittedFileUploadSubmission.getId());
        assertThat(submissionInDb).isPresent();
        assertThat(submissionInDb.get().getFilePath()).contains("ffile.png");
    }

    private FileUploadSubmission performInitialSubmission(Long exerciseId, FileUploadSubmission submission, String originalFilename) throws Exception {
        var file = new MockMultipartFile("file", originalFilename, "application/json", "some data".getBytes());
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
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getSubmissionByID_asTA_withResult() throws Exception {
        FileUploadSubmission fileUploadSubmission = ModelFactory.generateFileUploadSubmission(true);
        fileUploadSubmission = database.addFileUploadSubmission(releasedFileUploadExercise, fileUploadSubmission, "student1");
        Participation studentParticipation = database.createAndSaveParticipationForExercise(releasedFileUploadExercise, "student1");
        Result result = database.addResultToParticipation(studentParticipation, fileUploadSubmission);

        long submissionID = fileUploadSubmission.getId();
        FileUploadSubmission receivedSubmission = request.get("/api/file-upload-submissions/" + submissionID, HttpStatus.OK, FileUploadSubmission.class);

        assertThat(receivedSubmission.getId()).isEqualTo(submissionID);
        assertThat(receivedSubmission.getLatestResult()).as("submission has latest result").isEqualTo(result);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getSubmissionByID_asTA_withResultAndAssessor() throws Exception {
        FileUploadSubmission fileUploadSubmission = ModelFactory.generateFileUploadSubmission(true);
        fileUploadSubmission = database.saveFileUploadSubmissionWithResultAndAssessor(releasedFileUploadExercise, fileUploadSubmission, "student1", "tutor1");
        User assessor = database.getUserByLogin("tutor1");

        long submissionID = fileUploadSubmission.getId();
        FileUploadSubmission receivedSubmission = request.get("/api/file-upload-submissions/" + submissionID, HttpStatus.OK, FileUploadSubmission.class);

        assertThat(receivedSubmission.getId()).isEqualTo(submissionID);
        assertThat(receivedSubmission.getLatestResult().getAssessor()).as("latest result has assessor").isEqualTo(assessor);
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
