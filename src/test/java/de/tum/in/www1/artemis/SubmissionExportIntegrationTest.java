package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.service.FileUploadSubmissionExportService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.web.rest.dto.SubmissionExportOptionsDTO;

public class SubmissionExportIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private ModelingExercise modelingExercise;

    private TextExercise textExercise;

    private FileUploadExercise fileUploadExercise;

    private SubmissionExportOptionsDTO baseExportOptions;

    private ModelingSubmission modelingSubmission1;

    private ModelingSubmission modelingSubmission2;

    private ModelingSubmission modelingSubmission3;

    private TextSubmission textSubmission1;

    private TextSubmission textSubmission2;

    private TextSubmission textSubmission3;

    private FileUploadSubmission fileUploadSubmission1;

    private FileUploadSubmission fileUploadSubmission2;

    private FileUploadSubmission fileUploadSubmission3;

    private final long NOT_EXISTING_EXERCISE_ID = 5489218954L;

    @BeforeEach
    public void initTestCase() {
        List<User> users = database.addUsers(3, 1, 0, 1);
        users.remove(database.getUserByLogin("admin"));
        Course course1 = database.addCourseWithModelingAndTextAndFileUploadExercise();
        course1.getExercises().forEach(exercise -> {
            database.createAndSaveParticipationForExercise(exercise, "student1");
            database.createAndSaveParticipationForExercise(exercise, "student2");
            database.createAndSaveParticipationForExercise(exercise, "student3");

            if (exercise instanceof ModelingExercise) {
                modelingExercise = (ModelingExercise) exercise;
                try {
                    modelingSubmission1 = database.addModelingSubmissionFromResources(modelingExercise, "test-data/model-submission/model.54727.json", "student1");
                    modelingSubmission2 = database.addModelingSubmissionFromResources(modelingExercise, "test-data/model-submission/model.54742.json", "student2");
                    modelingSubmission3 = database.addModelingSubmissionFromResources(modelingExercise, "test-data/model-submission/model.54745.json", "student3");
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
            else if (exercise instanceof TextExercise) {
                textExercise = (TextExercise) exercise;

                textSubmission1 = database.saveTextSubmission(textExercise, ModelFactory.generateTextSubmission("example text", Language.ENGLISH, true), "student1");
                textSubmission2 = database.saveTextSubmission(textExercise, ModelFactory.generateTextSubmission("some other text", Language.ENGLISH, true), "student2");
                textSubmission3 = database.saveTextSubmission(textExercise, ModelFactory.generateTextSubmission("a third text", Language.ENGLISH, true), "student3");
            }
            else if (exercise instanceof FileUploadExercise) {
                fileUploadExercise = (FileUploadExercise) exercise;

                fileUploadSubmission1 = database.addFileUploadSubmission(fileUploadExercise, ModelFactory.generateFileUploadSubmissionWithFile(true, "test1.pdf"), "student1");
                fileUploadSubmission2 = database.addFileUploadSubmission(fileUploadExercise, ModelFactory.generateFileUploadSubmissionWithFile(true, "test2.pdf"), "student2");
                fileUploadSubmission3 = database.addFileUploadSubmission(fileUploadExercise, ModelFactory.generateFileUploadSubmissionWithFile(true, "test3.pdf"), "student3");

                try {
                    saveEmptySubmissionFile(fileUploadExercise, fileUploadSubmission1);
                    saveEmptySubmissionFile(fileUploadExercise, fileUploadSubmission2);
                    saveEmptySubmissionFile(fileUploadExercise, fileUploadSubmission3);
                }
                catch (IOException e) {
                    fail("Could not create submission files", e);
                }

            }
        });

        baseExportOptions = new SubmissionExportOptionsDTO();
        baseExportOptions.setExportAllParticipants(true);
        baseExportOptions.setFilterLateSubmissions(false);
    }

    private void saveEmptySubmissionFile(Exercise exercise, FileUploadSubmission submission) throws IOException {

        String[] parts = submission.getFilePath().split("/");
        String fileName = parts[parts.length - 1];
        File file = Paths.get(FileUploadSubmission.buildFilePath(exercise.getId(), submission.getId()), fileName).toFile();

        File parent = file.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) {
            throw new IOException("Couldn't create dir: " + parent);
        }

        if (!file.exists()) {
            file.createNewFile();
        }
    }

    @AfterEach
    public void resetDatabase() throws Exception {
        // change back to instructor user
        database.changeUser("instructor1");
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testAll_asStudent() throws Exception {
        this.testAllPreAuthorize();
    }

    private void testAllPreAuthorize() throws Exception {
        request.post("/api/modeling-exercises/" + modelingExercise.getId() + "/export-submissions", baseExportOptions, HttpStatus.FORBIDDEN);
        request.post("/api/text-exercises/" + textExercise.getId() + "/export-submissions", baseExportOptions, HttpStatus.FORBIDDEN);
        request.post("/api/file-upload-exercises/" + fileUploadExercise.getId() + "/export-submissions", baseExportOptions, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testNoSubmissionsForStudent_asInstructor() throws Exception {
        baseExportOptions.setExportAllParticipants(false);
        baseExportOptions.setParticipantIdentifierList("nonexistentstudent");
        request.post("/api/text-exercises/" + textExercise.getId() + "/export-submissions", baseExportOptions, HttpStatus.BAD_REQUEST);
        request.post("/api/modeling-exercises/" + modelingExercise.getId() + "/export-submissions", baseExportOptions, HttpStatus.BAD_REQUEST);
        request.post("/api/file-upload-exercises/" + fileUploadExercise.getId() + "/export-submissions", baseExportOptions, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testNoSubmissionsForStudent_asInstructorNotInGroup() throws Exception {
        baseExportOptions.setExportAllParticipants(false);
        baseExportOptions.setParticipantIdentifierList("nonexistentstudent");
        Course course = textExercise.getCourseViaExerciseGroupOrCourseMember();
        course.setInstructorGroupName("abc");
        database.saveCourse(course);
        request.post("/api/text-exercises/" + textExercise.getId() + "/export-submissions", baseExportOptions, HttpStatus.FORBIDDEN);
        request.post("/api/modeling-exercises/" + modelingExercise.getId() + "/export-submissions", baseExportOptions, HttpStatus.FORBIDDEN);
        request.post("/api/file-upload-exercises/" + fileUploadExercise.getId() + "/export-submissions", baseExportOptions, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testNoSubmissionsForStudent_asTutor() throws Exception {
        baseExportOptions.setExportAllParticipants(true);
        baseExportOptions.setParticipantIdentifierList("nonexistentstudent");
        request.post("/api/text-exercises/" + textExercise.getId() + "/export-submissions", baseExportOptions, HttpStatus.FORBIDDEN);
        request.post("/api/modeling-exercises/" + modelingExercise.getId() + "/export-submissions", baseExportOptions, HttpStatus.FORBIDDEN);
        request.post("/api/file-upload-exercises/" + fileUploadExercise.getId() + "/export-submissions", baseExportOptions, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testWrongExerciseId_asInstructor() throws Exception {
        baseExportOptions.setExportAllParticipants(false);
        baseExportOptions.setParticipantIdentifierList("nonexistentstudent");
        request.post("/api/text-exercises/" + NOT_EXISTING_EXERCISE_ID + "/export-submissions", baseExportOptions, HttpStatus.NOT_FOUND);
        request.post("/api/modeling-exercises/" + NOT_EXISTING_EXERCISE_ID + "/export-submissions", baseExportOptions, HttpStatus.NOT_FOUND);
        request.post("/api/file-upload-exercises/" + NOT_EXISTING_EXERCISE_ID + "/export-submissions", baseExportOptions, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testNoSubmissionsForDate_asInstructor() throws Exception {
        baseExportOptions.setFilterLateSubmissions(true);
        baseExportOptions.setFilterLateSubmissionsDate(ZonedDateTime.now().minusDays(2));
        request.post("/api/text-exercises/" + textExercise.getId() + "/export-submissions", baseExportOptions, HttpStatus.BAD_REQUEST);
        request.post("/api/modeling-exercises/" + modelingExercise.getId() + "/export-submissions", baseExportOptions, HttpStatus.BAD_REQUEST);
        request.post("/api/file-upload-exercises/" + fileUploadExercise.getId() + "/export-submissions", baseExportOptions, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testExportAll() throws Exception {
        File textZip = request.postWithResponseBodyFile("/api/text-exercises/" + textExercise.getId() + "/export-submissions", baseExportOptions, HttpStatus.OK);
        assertZipContains(textZip, textSubmission1, textSubmission2, textSubmission3);

        File modelingZip = request.postWithResponseBodyFile("/api/modeling-exercises/" + modelingExercise.getId() + "/export-submissions", baseExportOptions, HttpStatus.OK);
        assertZipContains(modelingZip, modelingSubmission1, modelingSubmission2, modelingSubmission3);

        File fileUploadUip = request.postWithResponseBodyFile("/api/file-upload-exercises/" + fileUploadExercise.getId() + "/export-submissions", baseExportOptions, HttpStatus.OK);
        assertZipContains(fileUploadUip, fileUploadSubmission1, fileUploadSubmission2, fileUploadSubmission3);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testExportAll_IOException() throws Exception {
        MockedStatic<Files> mockedFiles = mockStatic(Files.class);
        mockedFiles.when(() -> Files.newOutputStream(any(), any())).thenThrow(IOException.class);
        request.postWithResponseBodyFile("/api/file-upload-exercises/" + fileUploadExercise.getId() + "/export-submissions", baseExportOptions, HttpStatus.BAD_REQUEST);
        // the following line resets the mock and prevents it from disturbing any other tests
        mockedFiles.close();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testExportTextExerciseSubmission_IOException() throws Exception {
        MockedStatic<Files> mockedFiles = mockStatic(Files.class);
        mockedFiles.when(() -> Files.newOutputStream(any(), any())).thenThrow(IOException.class);
        request.postWithResponseBodyFile("/api/text-exercises/" + textExercise.getId() + "/export-submissions", baseExportOptions, HttpStatus.BAD_REQUEST);
        mockedFiles.close();
    }
    
    private void assertZipContains(File file, Submission... submissions) {
        try {
            ZipFile zip = new ZipFile(file);
            for (Submission s : submissions) {
                assertThat(zip.getEntry(getSubmissionFileName(s))).isNotNull();
            }
        }
        catch (IOException e) {
            fail("Could not read zip file.");
        }
    }

    private String getSubmissionFileName(Submission submission) {
        if (submission instanceof TextSubmission) {
            return textExercise.getTitle() + "-" + ((StudentParticipation) submission.getParticipation()).getParticipantIdentifier() + "-" + submission.getId() + ".txt";
        }
        else if (submission instanceof ModelingSubmission) {
            return modelingExercise.getTitle() + "-" + ((StudentParticipation) submission.getParticipation()).getParticipantIdentifier() + "-" + submission.getId() + ".json";
        }
        else if (submission instanceof FileUploadSubmission) {
            return fileUploadExercise.getTitle() + "-" + ((StudentParticipation) submission.getParticipation()).getParticipantIdentifier() + "-" + submission.getId() + ".pdf";
        }
        else {
            fail("Unknown submission type");
            return "";
        }
    }

}
