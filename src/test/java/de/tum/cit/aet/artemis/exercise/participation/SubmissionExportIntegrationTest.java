package de.tum.cit.aet.artemis.exercise.participation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;

import java.io.File;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.dto.SubmissionExportOptionsDTO;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadSubmission;
import de.tum.cit.aet.artemis.fileupload.util.FileUploadExerciseUtilService;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.modeling.util.ModelingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class SubmissionExportIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "submissionexportintegration";

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private ModelingExerciseUtilService modelingExerciseUtilService;

    @Autowired
    private FileUploadExerciseUtilService fileUploadExerciseUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

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

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 3, 1, 0, 1);
        Course course1 = courseUtilService.addCourseWithModelingAndTextAndFileUploadExercise();
        course1.getExercises().forEach(exercise -> {
            participationUtilService.createAndSaveParticipationForExercise(exercise, TEST_PREFIX + "student1");
            participationUtilService.createAndSaveParticipationForExercise(exercise, TEST_PREFIX + "student2");
            participationUtilService.createAndSaveParticipationForExercise(exercise, TEST_PREFIX + "student3");

            switch (exercise) {
                case ModelingExercise modelingExercise1 -> {
                    modelingExercise = modelingExercise1;
                    try {
                        modelingSubmission1 = modelingExerciseUtilService.addModelingSubmissionFromResources(modelingExercise, "test-data/model-submission/model.54727.json",
                                TEST_PREFIX + "student1");
                        modelingSubmission2 = modelingExerciseUtilService.addModelingSubmissionFromResources(modelingExercise, "test-data/model-submission/model.54742.json",
                                TEST_PREFIX + "student2");
                        modelingSubmission3 = modelingExerciseUtilService.addModelingSubmissionFromResources(modelingExercise, "test-data/model-submission/model.54745.json",
                                TEST_PREFIX + "student3");
                    }
                    catch (IOException e) {
                        fail(e.getMessage(), e);
                    }
                }
                case TextExercise textExercise1 -> {
                    textExercise = textExercise1;

                    textSubmission1 = textExerciseUtilService.saveTextSubmission(textExercise, ParticipationFactory.generateTextSubmission("example text", Language.ENGLISH, true),
                            TEST_PREFIX + "student1");
                    textSubmission2 = textExerciseUtilService.saveTextSubmission(textExercise,
                            ParticipationFactory.generateTextSubmission("some other text", Language.ENGLISH, true), TEST_PREFIX + "student2");
                    textSubmission3 = textExerciseUtilService.saveTextSubmission(textExercise, ParticipationFactory.generateTextSubmission("a third text", Language.ENGLISH, true),
                            TEST_PREFIX + "student3");
                }
                case FileUploadExercise uploadExercise -> {
                    fileUploadExercise = uploadExercise;

                    fileUploadSubmission1 = fileUploadExerciseUtilService.addFileUploadSubmission(fileUploadExercise,
                            ParticipationFactory.generateFileUploadSubmissionWithFile(true, "test1.pdf"), TEST_PREFIX + "student1");
                    fileUploadSubmission2 = fileUploadExerciseUtilService.addFileUploadSubmission(fileUploadExercise,
                            ParticipationFactory.generateFileUploadSubmissionWithFile(true, "test2.pdf"), TEST_PREFIX + "student2");
                    fileUploadSubmission3 = fileUploadExerciseUtilService.addFileUploadSubmission(fileUploadExercise,
                            ParticipationFactory.generateFileUploadSubmissionWithFile(true, "test3.pdf"), TEST_PREFIX + "student3");

                    try {
                        saveEmptySubmissionFile(fileUploadExercise, fileUploadSubmission1);
                        saveEmptySubmissionFile(fileUploadExercise, fileUploadSubmission2);
                        saveEmptySubmissionFile(fileUploadExercise, fileUploadSubmission3);
                    }
                    catch (IOException e) {
                        fail("Could not create submission files", e);
                    }
                }
                default -> {
                }
            }
        });

        baseExportOptions = new SubmissionExportOptionsDTO();
        baseExportOptions.setExportAllParticipants(true);
        baseExportOptions.setFilterLateSubmissions(false);
    }

    private void saveEmptySubmissionFile(Exercise exercise, FileUploadSubmission submission) throws IOException {

        String[] parts = submission.getFilePath().split(Pattern.quote(File.separator));
        String fileName = parts[parts.length - 1];
        File file = FileUploadSubmission.buildFilePath(exercise.getId(), submission.getId()).resolve(fileName).toFile();

        File parent = file.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) {
            throw new IOException("Couldn't create dir: " + parent);
        }

        if (!file.exists()) {
            file.createNewFile();
        }
    }

    @AfterEach
    void tearDown() {
        // change back to instructor user
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testAll_asStudent() throws Exception {
        this.testAllPreAuthorize();
    }

    private void testAllPreAuthorize() throws Exception {
        request.post("/api/modeling-exercises/" + modelingExercise.getId() + "/export-submissions", baseExportOptions, HttpStatus.FORBIDDEN);
        request.post("/api/text-exercises/" + textExercise.getId() + "/export-submissions", baseExportOptions, HttpStatus.FORBIDDEN);
        request.post("/api/file-upload-exercises/" + fileUploadExercise.getId() + "/export-submissions", baseExportOptions, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testNoSubmissionsForStudent_asInstructor() throws Exception {
        baseExportOptions.setExportAllParticipants(false);
        baseExportOptions.setParticipantIdentifierList("nonexistentstudent");
        request.post("/api/text-exercises/" + textExercise.getId() + "/export-submissions", baseExportOptions, HttpStatus.BAD_REQUEST);
        request.post("/api/modeling-exercises/" + modelingExercise.getId() + "/export-submissions", baseExportOptions, HttpStatus.BAD_REQUEST);
        request.post("/api/file-upload-exercises/" + fileUploadExercise.getId() + "/export-submissions", baseExportOptions, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testNoSubmissionsForStudent_asInstructorNotInGroup() throws Exception {
        baseExportOptions.setExportAllParticipants(false);
        baseExportOptions.setParticipantIdentifierList("nonexistentstudent");
        Course course = textExercise.getCourseViaExerciseGroupOrCourseMember();
        course.setInstructorGroupName("abc");
        courseUtilService.saveCourse(course);
        request.post("/api/text-exercises/" + textExercise.getId() + "/export-submissions", baseExportOptions, HttpStatus.FORBIDDEN);
        request.post("/api/modeling-exercises/" + modelingExercise.getId() + "/export-submissions", baseExportOptions, HttpStatus.FORBIDDEN);
        request.post("/api/file-upload-exercises/" + fileUploadExercise.getId() + "/export-submissions", baseExportOptions, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testNoSubmissionsForStudent_asTutor() throws Exception {
        baseExportOptions.setExportAllParticipants(true);
        baseExportOptions.setParticipantIdentifierList("nonexistentstudent");
        request.post("/api/text-exercises/" + textExercise.getId() + "/export-submissions", baseExportOptions, HttpStatus.FORBIDDEN);
        request.post("/api/modeling-exercises/" + modelingExercise.getId() + "/export-submissions", baseExportOptions, HttpStatus.FORBIDDEN);
        request.post("/api/file-upload-exercises/" + fileUploadExercise.getId() + "/export-submissions", baseExportOptions, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testWrongExerciseId_asInstructor() throws Exception {
        baseExportOptions.setExportAllParticipants(false);
        baseExportOptions.setParticipantIdentifierList("nonexistentstudent");
        long NOT_EXISTING_EXERCISE_ID = 5489218954L;
        request.post("/api/text-exercises/" + NOT_EXISTING_EXERCISE_ID + "/export-submissions", baseExportOptions, HttpStatus.NOT_FOUND);
        request.post("/api/modeling-exercises/" + NOT_EXISTING_EXERCISE_ID + "/export-submissions", baseExportOptions, HttpStatus.NOT_FOUND);
        request.post("/api/file-upload-exercises/" + NOT_EXISTING_EXERCISE_ID + "/export-submissions", baseExportOptions, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testNoSubmissionsForDate_asInstructor() throws Exception {
        baseExportOptions.setFilterLateSubmissions(true);
        baseExportOptions.setFilterLateSubmissionsDate(ZonedDateTime.now().minusDays(2));
        request.post("/api/text-exercises/" + textExercise.getId() + "/export-submissions", baseExportOptions, HttpStatus.BAD_REQUEST);
        request.post("/api/modeling-exercises/" + modelingExercise.getId() + "/export-submissions", baseExportOptions, HttpStatus.BAD_REQUEST);
        request.post("/api/file-upload-exercises/" + fileUploadExercise.getId() + "/export-submissions", baseExportOptions, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExportAll() throws Exception {
        File textZip = request.postWithResponseBodyFile("/api/text-exercises/" + textExercise.getId() + "/export-submissions", baseExportOptions, HttpStatus.OK);
        assertZipContains(textZip, textSubmission1, textSubmission2, textSubmission3);

        File modelingZip = request.postWithResponseBodyFile("/api/modeling-exercises/" + modelingExercise.getId() + "/export-submissions", baseExportOptions, HttpStatus.OK);
        assertZipContains(modelingZip, modelingSubmission1, modelingSubmission2, modelingSubmission3);

        File fileUploadUip = request.postWithResponseBodyFile("/api/file-upload-exercises/" + fileUploadExercise.getId() + "/export-submissions", baseExportOptions, HttpStatus.OK);
        assertZipContains(fileUploadUip, fileUploadSubmission1, fileUploadSubmission2, fileUploadSubmission3);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExportAll_IOException() throws Exception {
        doThrow(IOException.class).when(zipFileService).createZipFile(any(), any());
        request.postWithResponseBodyFile("/api/file-upload-exercises/" + fileUploadExercise.getId() + "/export-submissions", baseExportOptions, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExportTextExerciseSubmission_IOException() throws Exception {
        doThrow(IOException.class).when(zipFileService).createZipFile(any(), any());
        request.postWithResponseBodyFile("/api/text-exercises/" + textExercise.getId() + "/export-submissions", baseExportOptions, HttpStatus.BAD_REQUEST);
    }

    private void assertZipContains(File file, Submission... submissions) {
        try (ZipFile zip = new ZipFile(file)) {
            for (Submission submission : submissions) {
                assertThat(zip.getEntry(getSubmissionFileName(submission))).isNotNull();
            }
        }
        catch (IOException e) {
            fail("Could not read zip file.");
        }
    }

    private String getSubmissionFileName(Submission submission) {
        switch (submission) {
            case TextSubmission ignored -> {
                return textExercise.getTitle() + "-" + ((StudentParticipation) submission.getParticipation()).getParticipantIdentifier() + "-" + submission.getId() + ".txt";
            }
            case ModelingSubmission ignored -> {
                return modelingExercise.getTitle() + "-" + ((StudentParticipation) submission.getParticipation()).getParticipantIdentifier() + "-" + submission.getId() + ".json";
            }
            case FileUploadSubmission ignored -> {
                return fileUploadExercise.getTitle() + "-" + ((StudentParticipation) submission.getParticipation()).getParticipantIdentifier() + "-" + submission.getId() + ".pdf";
            }
            case null, default -> {
                fail("Unknown submission type");
                return "";
            }
        }
    }

}
