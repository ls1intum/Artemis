package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
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
        checkDetailsHidden(returnedSubmission, true);
        var submissionInDb = fileUploadSubmissionRepository.findById(returnedSubmission.getId()).get();
        assertThat(submissionInDb).isEqualToIgnoringGivenFields(returnedSubmission, "result", "participation", "fileService", "submissionDate");
    }

    @Test
    @WithMockUser(value = "student1")
    public void submitFileUploadSubmission_withSubmissionId() throws Exception {
        database.addFileUploadSubmission(fileUploadExercise, notSubmittedFileUploadSubmission, "student1");
        request.postWithResponseBody("/api/exercises/" + fileUploadExercise.getId() + "/file-upload-submissions", notSubmittedFileUploadSubmission, FileUploadSubmission.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(value = "student1")
    public void submitFileUploadSubmission_exerciseIdIncorrect() throws Exception {
        database.addFileUploadSubmission(fileUploadExercise, notSubmittedFileUploadSubmission, "student1");
        request.postWithResponseBody("/api/exercises/" + (fileUploadExercise.getId() + 1) + "/file-upload-submissions", notSubmittedFileUploadSubmission,
                FileUploadSubmission.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(value = "student1")
    public void submitFileUploadSubmission_courseIdIncorrect() throws Exception {
        var courseId = fileUploadExercise.getCourse().getId();
        fileUploadExercise.getCourse().setId(courseId + 1);
        database.addFileUploadSubmission(fileUploadExercise, notSubmittedFileUploadSubmission, "student1");
        request.postWithResponseBody("/api/exercises/" + fileUploadExercise.getId() + "/file-upload-submissions", notSubmittedFileUploadSubmission, FileUploadSubmission.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(value = "student2")
    public void submitFileUploadSubmission_notStudentInCourse() throws Exception {
        database.addFileUploadSubmission(fileUploadExercise, notSubmittedFileUploadSubmission, "student1");
        request.postWithResponseBody("/api/exercises/" + fileUploadExercise.getId() + "/file-upload-submissions", notSubmittedFileUploadSubmission, FileUploadSubmission.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(value = "student1")
    public void submitFileUploadSubmission_incorrectFileType() throws Exception {
        FileUploadSubmission submission = ModelFactory.generateFileUploadSubmission(false);
        var file = new MockMultipartFile("file", "file.txt", "application/json", "some data".getBytes());
        request.postWithMultipartFile("/api/exercises/" + fileUploadExercise.getId() + "/file-upload-submissions", submission, "submission", file, FileUploadSubmission.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(value = "student3", roles = "USER")
    public void submitFileUploadSubmission_afterDueDate_forbidden() throws Exception {
        participation.setInitializationDate(ZonedDateTime.now().minusDays(2));
        participationRepository.save(participation);
        request.postWithMultipartFile("/api/exercises/" + afterDueDateFileUploadExercise.getId() + "/file-upload-submissions", submittedFileUploadSubmission, "submission",
                validFile, FileUploadSubmission.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(value = "student3", roles = "USER")
    public void submitFileUploadSubmission_beforeDueDate_allowed() throws Exception {
        request.postWithMultipartFile("/api/exercises/" + fileUploadExercise.getId() + "/file-upload-submissions", submittedFileUploadSubmission, "submission", validFile,
                FileUploadSubmission.class, HttpStatus.OK);
    }

    @Test
    @WithMockUser(value = "student3", roles = "USER")
    public void submitFileUploadSubmission_afterDueDateWithParticipationStartAfterDueDate_allowed() throws Exception {
        request.postWithMultipartFile("/api/exercises/" + afterDueDateFileUploadExercise.getId() + "/file-upload-submissions", submittedFileUploadSubmission, "submission",
                validFile, FileUploadSubmission.class, HttpStatus.OK);
    }

    @Test
    @WithMockUser(value = "student3", roles = "USER")
    public void submitFileUploadSubmission_beforeDueDateSecondSubmission_allowed() throws Exception {
        var file = new MockMultipartFile("file", "ffile.png", "application/json", "some data".getBytes());
        submittedFileUploadSubmission = request.postWithMultipartFile("/api/exercises/" + fileUploadExercise.getId() + "/file-upload-submissions", submittedFileUploadSubmission,
                "submission", file, FileUploadSubmission.class, HttpStatus.OK);

        final var submissionInDb = fileUploadSubmissionRepository.findById(submittedFileUploadSubmission.getId());
        assertThat(submissionInDb.isPresent());
        assertThat(submissionInDb.get().getFilePath().contains("ffile.png")).isTrue();
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getAllSubmissionsOfExercise_studentHidden() throws Exception {
        FileUploadSubmission submission1 = database.addFileUploadSubmission(fileUploadExercise, notSubmittedFileUploadSubmission, "student1");
        FileUploadSubmission submission2 = database.addFileUploadSubmission(fileUploadExercise, submittedFileUploadSubmission, "student2");

        List<FileUploadSubmission> submissions = request.getList("/api/exercises/" + fileUploadExercise.getId() + "/file-upload-submissions", HttpStatus.OK,
                FileUploadSubmission.class);

        assertThat(submissions).as("contains both submissions").containsExactlyInAnyOrder(submission1, submission2);
        assertThat(submissions.stream().allMatch(submission -> ((StudentParticipation) submission.getParticipation()).getStudent() == null)).isTrue();
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getAllSubmissionsOfExerciseAssessedByTutor() throws Exception {
        FileUploadSubmission submission = ModelFactory.generateFileUploadSubmission(true);
        List<Feedback> feedbacks = ModelFactory.generateFeedback();
        submission = database.addFileUploadSubmissionWithResultAndAssessorFeedback(fileUploadExercise, submission, "student1", "tutor1", feedbacks);
        database.updateExerciseDueDate(fileUploadExercise.getId(), ZonedDateTime.now().minusHours(1));

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("assessedByTutor", "true");

        List<FileUploadSubmission> submissions = request.getList("/api/exercises/" + fileUploadExercise.getId() + "/file-upload-submissions", HttpStatus.OK,
                FileUploadSubmission.class, params);

        assertThat(submissions).as("contains both submissions").containsExactlyInAnyOrder(submission);
        assertThat(submissions.stream().allMatch(sub -> ((StudentParticipation) sub.getParticipation()).getStudent() == null)).isTrue();
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
    public void getAllSubmissionsOfExercise_asStudent() throws Exception {
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
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getSubmissionWithoutAssessment_lock() throws Exception {
        FileUploadSubmission submission = database.addFileUploadSubmission(fileUploadExercise, submittedFileUploadSubmission, "student1");
        database.updateExerciseDueDate(fileUploadExercise.getId(), ZonedDateTime.now().minusHours(1));
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("lock", "true");

        FileUploadSubmission storedSubmission = request.get("/api/exercises/" + fileUploadExercise.getId() + "/file-upload-submission-without-assessment", HttpStatus.OK,
                FileUploadSubmission.class, params);

        assertThat(storedSubmission).as("submission was found").isEqualToIgnoringGivenFields(submission, "result", "submissionDate", "fileService");
        assertThat(storedSubmission.getResult()).as("result is set").isNotNull();
        assertThat(storedSubmission.getResult().getAssessor().getLogin()).as("assessor is set").isEqualTo("tutor1");
        assertThat(storedSubmission.getResult().getAssessmentType()).as("assessment type set to manual").isEqualTo(AssessmentType.MANUAL);
        checkDetailsHidden(storedSubmission, false);
    }

    @Test
    @WithMockUser(value = "student1")
    public void getSubmissionWithoutAssessment_asStudent() throws Exception {
        request.get("/api/exercises/" + fileUploadExercise.getId() + "/file-upload-submission-without-assessment", HttpStatus.FORBIDDEN, FileUploadSubmission.class);
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
        assertThat(submission.getParticipation().getResults()).as("No results are send to the student").isEmpty();
        assertThat(submission.getParticipation().getSubmissions()).as("No submissions are send to the student").isEmpty();
    }

    @Test
    @WithMockUser(value = "student1")
    public void getDataForFileUpload_wrongExerciseType() throws Exception {
        var textCourse = database.addCourseWithOneTextExercise();
        var textExercise = (TextExercise) textCourse.getExercises().iterator().next();
        TextSubmission textSubmission = ModelFactory.generateTextSubmission("text", Language.ENGLISH, true);
        textSubmission = database.addTextSubmissionWithResultAndAssessor(textExercise, textSubmission, "student1", "tutor1");

        request.get("/api/participations/" + textSubmission.getParticipation().getId() + "/file-upload-editor", HttpStatus.BAD_REQUEST, TextSubmission.class);
    }

    @Test
    @WithMockUser(value = "student2")
    public void getDataForFileUpload_wrongStudent() throws Exception {
        FileUploadSubmission fileUploadSubmission = ModelFactory.generateFileUploadSubmission(true);
        List<Feedback> feedbacks = ModelFactory.generateFeedback();
        fileUploadSubmission = database.addFileUploadSubmissionWithResultAndAssessorFeedback(fileUploadExercise, fileUploadSubmission, "student1", "tutor1", feedbacks);
        database.updateExerciseDueDate(fileUploadExercise.getId(), ZonedDateTime.now().minusHours(1));

        request.get("/api/participations/" + fileUploadSubmission.getParticipation().getId() + "/file-upload-editor", HttpStatus.FORBIDDEN, FileUploadSubmission.class);
    }

    private FileUploadSubmission performInitialSubmission(Long exerciseId, FileUploadSubmission submission) throws Exception {
        var file = new MockMultipartFile("file", "file.png", "application/json", "some data".getBytes());
        return request.postWithMultipartFile("/api/exercises/" + exerciseId + "/file-upload-submissions", submission, "submission", file, FileUploadSubmission.class,
                HttpStatus.OK);
    }

    private void checkDetailsHidden(FileUploadSubmission submission, boolean isStudent) {
        assertThat(submission.getParticipation().getSubmissions()).as("submissions are hidden in participation").isNullOrEmpty();
        assertThat(submission.getParticipation().getResults()).as("results are hidden in participation").isNullOrEmpty();
        if (isStudent) {
            assertThat(submission.getResult()).as("result is hidden").isNull();
        }
        else {
            assertThat(((StudentParticipation) submission.getParticipation()).getStudent()).as("student of participation is hidden").isNull();
        }
    }
}
