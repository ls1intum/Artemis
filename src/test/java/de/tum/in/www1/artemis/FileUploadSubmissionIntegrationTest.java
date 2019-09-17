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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import de.tum.in.www1.artemis.domain.FileUploadExercise;
import de.tum.in.www1.artemis.domain.FileUploadSubmission;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.repository.*;
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
    ResultRepository resultRepo;

    @Autowired
    FileUploadSubmissionRepository fileUploadSubmissionRepository;

    private FileUploadExercise fileUploadExercise;

    private FileUploadSubmission submittedFileUploadSubmission;

    private FileUploadSubmission notSubmittedFileUploadSubmission;

    @BeforeEach
    public void initTestCase() throws Exception {
        database.addUsers(3, 1, 0);
        database.addCourseWithOneFileUploadExercise();
        fileUploadExercise = (FileUploadExercise) exerciseRepo.findAll().get(0);
        submittedFileUploadSubmission = ModelFactory.generateFileUploadSubmission("p.pdf", true);
        notSubmittedFileUploadSubmission = ModelFactory.generateFileUploadSubmission("p.png", false);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(value = "student1")
    public void submitFileUploadSubmission() throws Exception {
        database.addParticipationForExercise(fileUploadExercise, "student1");
        FileUploadSubmission submission = ModelFactory.generateFileUploadSubmission("j.pdf", false);
        FileUploadSubmission returnedSubmission = performInitialSubmission(fileUploadExercise.getId(), submission);
        assertThat(returnedSubmission).as("submission correctly posted").isNotNull();
        assertThat(returnedSubmission.getFilePath()).isEqualTo(submission.getFilePath());
        checkDetailsHidden(returnedSubmission, true);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void getAllSubmissionsOfExercise() throws Exception {
        FileUploadSubmission submission1 = database.addFileUploadSubmission(fileUploadExercise, notSubmittedFileUploadSubmission, "student1");
        FileUploadSubmission submission2 = database.addFileUploadSubmission(fileUploadExercise, submittedFileUploadSubmission, "student2");

        List<FileUploadSubmission> submissions = request.getList("/api/exercises/" + fileUploadExercise.getId() + "/file-upload-submissions", HttpStatus.OK,
                FileUploadSubmission.class);

        assertThat(submissions).as("contains both submissions").containsExactlyInAnyOrder(submission1, submission2);
    }

    @Test
    @WithMockUser(value = "student1")
    public void getAllSubmissionsOfExerciseAsStudent() throws Exception {
        database.addFileUploadSubmission(fileUploadExercise, submittedFileUploadSubmission, "student1");

        request.getList("/api/exercises/" + fileUploadExercise.getId() + "/file-upload-submissions", HttpStatus.FORBIDDEN, ModelingSubmission.class);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
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

        request.get("/api/exercises/" + fileUploadExercise.getId() + "/file-upload-submission-without-assessment", HttpStatus.FORBIDDEN, ModelingSubmission.class);
    }

    private FileUploadSubmission performInitialSubmission(Long exerciseId, FileUploadSubmission submission) throws Exception {
        return request.postWithResponseBody("/api/exercises/" + exerciseId + "/file-upload-submissions", submission, FileUploadSubmission.class, HttpStatus.OK);
    }

    private void checkDetailsHidden(FileUploadSubmission submission, boolean isStudent) {
        assertThat(submission.getParticipation().getResults()).isNullOrEmpty();
        if (isStudent) {
            assertThat(submission.getResult()).isNull();
        }
    }
}
