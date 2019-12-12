package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.FileUploadExercise;
import de.tum.in.www1.artemis.domain.FileUploadSubmission;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.FileService;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.RequestUtilService;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@ActiveProfiles("artemis")
public class FileUploadSubmissionIntegrationTest {

    @Autowired
    CourseRepository courseRepo;

    @Autowired
    ExerciseRepository exerciseRepo;

    @Autowired
    UserRepository userRepo;

    @Autowired
    RequestUtilService request;

    @Autowired
    DatabaseUtilService database;

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

    private FileUploadExercise fileUploadExercise;

    private FileUploadExercise afterDueDateFileUploadExercise;

    private FileUploadSubmission submittedFileUploadSubmission;

    private FileUploadSubmission notSubmittedFileUploadSubmission;

    private MockMultipartFile validFile = new MockMultipartFile("file", "file.png", "application/json", "some data".getBytes());

    private StudentParticipation participation;

    @BeforeEach
    public void initTestCase() throws Exception {
        database.addUsers(3, 1, 1);
        database.addCourseWithTwoFileUploadExercise();
        fileUploadExercise = (FileUploadExercise) exerciseRepo.findAll().get(0);
        afterDueDateFileUploadExercise = (FileUploadExercise) exerciseRepo.findAll().get(1);
        submittedFileUploadSubmission = ModelFactory.generateFileUploadSubmission(true);
        notSubmittedFileUploadSubmission = ModelFactory.generateFileUploadSubmission(false);
        database.addParticipationForExercise(fileUploadExercise, "student3");
        participation = database.addParticipationForExercise(afterDueDateFileUploadExercise, "student3");
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(value = "student3")
    public void submitFileUploadSubmission() throws Exception {
        FileUploadSubmission submission = ModelFactory.generateFileUploadSubmission(false);
        FileUploadSubmission returnedSubmission = performInitialSubmission(fileUploadExercise.getId(), submission);
        String actualFilePath = FileUploadSubmission.buildFilePath(fileUploadExercise.getId(), returnedSubmission.getId()).concat("file.png");
        String publicFilePath = fileService.publicPathForActualPath(actualFilePath, returnedSubmission.getId());
        assertThat(returnedSubmission).as("submission correctly posted").isNotNull();
        assertThat(returnedSubmission.getFilePath()).isEqualTo(publicFilePath);
        var fileBytes = Files.readAllBytes(Path.of(actualFilePath));
        assertThat(fileBytes.length > 0).as("Stored file has content").isTrue();
        checkDetailsHidden(returnedSubmission, true);
    }

    @Test
    @WithMockUser(value = "student3")
    public void submitFileUploadSubmission_emptyFileContent() throws Exception {
        FileUploadSubmission submission = ModelFactory.generateFileUploadSubmission(false);
        var file = new MockMultipartFile("file", "file.png", "application/json", (byte[]) null);
        request.postWithMultipartFile("/api/exercises/" + fileUploadExercise.getId() + "/file-upload-submissions", submission, "submission", file, FileUploadSubmission.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(value = "student1")
    public void setSubmittedFileUploadSubmission_incorrectFileType() throws Exception {
        FileUploadSubmission submission = ModelFactory.generateFileUploadSubmission(false);
        var file = new MockMultipartFile("file", "file.txt", "application/json", "some data".getBytes());
        request.postWithMultipartFile("/api/exercises/" + fileUploadExercise.getId() + "/file-upload-submissions", submission, "submission", file, FileUploadSubmission.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getAllSubmissionsOfExercise() throws Exception {
        FileUploadSubmission submission1 = database.addFileUploadSubmission(fileUploadExercise, notSubmittedFileUploadSubmission, "student1");
        FileUploadSubmission submission2 = database.addFileUploadSubmission(fileUploadExercise, submittedFileUploadSubmission, "student2");

        List<FileUploadSubmission> submissions = request.getList("/api/exercises/" + fileUploadExercise.getId() + "/file-upload-submissions", HttpStatus.OK,
                FileUploadSubmission.class);

        assertThat(submissions).as("contains both submissions").containsExactlyInAnyOrder(submission1, submission2);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void cannotSeeStudentDetailsInSubmissionListAsTutor() throws Exception {
        FileUploadSubmission submission1 = database.addFileUploadSubmissionWithResultAndAssessor(fileUploadExercise, submittedFileUploadSubmission, "student1", "tutor1");

        List<FileUploadSubmission> submissions = request.getList("/api/exercises/" + fileUploadExercise.getId() + "/file-upload-submissions?assessedByTutor=true", HttpStatus.OK,
                FileUploadSubmission.class);

        assertThat(submissions.size()).as("one file upload submission was found").isEqualTo(1);
        assertThat(submissions.get(0).getId()).as("correct file upload submission was found").isEqualTo(submission1.getId());
        final StudentParticipation participation1 = (StudentParticipation) submissions.get(0).getParticipation();
        assertThat(participation1.getStudent()).as("contains no student details").isNull();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void canSeeStudentDetailsInSubmissionListAsInstructor() throws Exception {
        FileUploadSubmission submission1 = database.addFileUploadSubmission(fileUploadExercise, submittedFileUploadSubmission, "student1");

        List<FileUploadSubmission> submissions = request.getList("/api/exercises/" + fileUploadExercise.getId() + "/file-upload-submissions?submittedOnly=true", HttpStatus.OK,
                FileUploadSubmission.class);

        assertThat(submissions.size()).as("one file upload submission was found").isEqualTo(1);
        assertThat(submissions.get(0).getId()).as("correct file upload submission was found").isEqualTo(submission1.getId());
        final StudentParticipation participation1 = (StudentParticipation) submissions.get(0).getParticipation();
        assertThat(participation1.getStudent()).as("contains student details").isNotNull();
    }

    @Test
    @WithMockUser(value = "student1")
    public void getAllSubmissionsOfExerciseAsStudent() throws Exception {
        database.addFileUploadSubmission(fileUploadExercise, submittedFileUploadSubmission, "student1");

        request.getList("/api/exercises/" + fileUploadExercise.getId() + "/file-upload-submissions", HttpStatus.FORBIDDEN, ModelingSubmission.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getAllSubmittedSubmissionsOfExercise() throws Exception {
        FileUploadSubmission submission1 = database.addFileUploadSubmission(fileUploadExercise, submittedFileUploadSubmission, "student1");
        database.addFileUploadSubmission(fileUploadExercise, notSubmittedFileUploadSubmission, "student2");

        List<FileUploadSubmission> submissions = request.getList("/api/exercises/" + fileUploadExercise.getId() + "/file-upload-submissions?submittedOnly=true", HttpStatus.OK,
                FileUploadSubmission.class);

        assertThat(submissions).as("contains only submitted submission").containsExactlyInAnyOrder(submission1);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getSubmissionWithoutAssessment() throws Exception {
        FileUploadSubmission submission = database.addFileUploadSubmission(fileUploadExercise, submittedFileUploadSubmission, "student1");
        database.updateExerciseDueDate(fileUploadExercise.getId(), ZonedDateTime.now().minusHours(1));

        FileUploadSubmission storedSubmission = request.get("/api/exercises/" + fileUploadExercise.getId() + "/file-upload-submission-without-assessment", HttpStatus.OK,
                FileUploadSubmission.class);

        assertThat(storedSubmission).as("submission was found").isEqualToIgnoringGivenFields(submission, "result", "submissionDate", "fileService");
        assertThat(storedSubmission.getResult()).as("result is not set").isNull();
        checkDetailsHidden(storedSubmission, false);
    }

    @Test
    @WithMockUser(value = "student1")
    public void getFileUploadSubmissionWithoutAssessment_asStudent_forbidden() throws Exception {
        database.addFileUploadSubmission(fileUploadExercise, submittedFileUploadSubmission, "student1");
        database.updateExerciseDueDate(fileUploadExercise.getId(), ZonedDateTime.now().minusHours(1));

        request.get("/api/exercises/" + fileUploadExercise.getId() + "/file-upload-submission-without-assessment", HttpStatus.FORBIDDEN, FileUploadSubmission.class);
    }

    @Test
    @WithMockUser(value = "student1")
    public void getDataForFileUpload() throws Exception {
        FileUploadSubmission fileUploadSubmission = ModelFactory.generateFileUploadSubmission(true);
        List<Feedback> feedbacks = ModelFactory.generateFeedback();
        fileUploadSubmission = database.addFileUploadSubmissionWithResultAndAssessorFeedback(fileUploadExercise, fileUploadSubmission, "student1", "tutor1", feedbacks);
        database.updateExerciseDueDate(fileUploadExercise.getId(), ZonedDateTime.now().minusHours(1));

        FileUploadSubmission submission = request.get("/api/participations/" + fileUploadSubmission.getParticipation().getId() + "/file-upload-editor", HttpStatus.OK,
                FileUploadSubmission.class);
        assertThat(submission).isNotNull();
        assertThat(submission.getResult()).isNotNull();
        assertThat(submission.isSubmitted()).isTrue();
        assertThat(submission.getResult().getFeedbacks().size()).as("No feedback should be returned for editor").isEqualTo(0);
    }

    @Test
    @WithMockUser(value = "student3", roles = "USER")
    public void submitExercise_afterDueDate_forbidden() throws Exception {
        participation.setInitializationDate(ZonedDateTime.now().minusDays(2));
        participationRepository.save(participation);
        request.postWithMultipartFile("/api/exercises/" + afterDueDateFileUploadExercise.getId() + "/file-upload-submissions", submittedFileUploadSubmission, "submission",
                validFile, FileUploadSubmission.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(value = "student3", roles = "USER")
    public void submitExercise_beforeDueDate_allowed() throws Exception {
        request.postWithMultipartFile("/api/exercises/" + fileUploadExercise.getId() + "/file-upload-submissions", submittedFileUploadSubmission, "submission", validFile,
                FileUploadSubmission.class, HttpStatus.OK);
    }

    @Test
    @WithMockUser(value = "student3", roles = "USER")
    public void submitExercise_afterDueDateWithParticipationStartAfterDueDate_allowed() throws Exception {
        request.postWithMultipartFile("/api/exercises/" + afterDueDateFileUploadExercise.getId() + "/file-upload-submissions", submittedFileUploadSubmission, "submission",
                validFile, FileUploadSubmission.class, HttpStatus.OK);
    }

    @Test
    @WithMockUser(value = "student3", roles = "USER")
    public void submitExercise_beforeDueDateSecondSubmission_allowed() throws Exception {
        var file = new MockMultipartFile("file", "ffile.png", "application/json", "some data".getBytes());
        submittedFileUploadSubmission = request.postWithMultipartFile("/api/exercises/" + fileUploadExercise.getId() + "/file-upload-submissions", submittedFileUploadSubmission,
                "submission", file, FileUploadSubmission.class, HttpStatus.OK);

        final var submissionInDb = fileUploadSubmissionRepository.findById(submittedFileUploadSubmission.getId());
        assertThat(submissionInDb.isPresent());
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
            assertThat(submission.getResult()).isNull();
        }
    }
}
