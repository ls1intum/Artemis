package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.artemis.config.FileUploadExerciseProperties;
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

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
@ActiveProfiles("artemis")
public class FileUploadExerciseIntegrationTest {

    @Autowired
    DatabaseUtilService database;

    @Autowired
    RequestUtilService request;

    @Autowired
    CourseRepository courseRepo;

    @Autowired
    ExerciseRepository exerciseRepo;

    @Autowired
    FileUploadExerciseRepository fileUploadExerciseRepository;

    @Autowired
    FileUploadSubmissionRepository fileUploadSubmissionRepository;

    @Autowired
    FileUploadExerciseProperties fileUploadExerciseProperties;

    @BeforeEach
    public void initTestCase() {
        database.addUsers(1, 1, 1);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createFileUploadExercise() throws Exception {
        String filePattern = "pdf";
        FileUploadExercise fileUploadExercise = database.createFileUploadExercisesWithCourse().get(0);
        fileUploadExercise.setFilePattern(filePattern);

        assertThat(fileUploadExerciseProperties.getFilePatterns()).isNotNull();
        FileUploadExercise receivedFileUploadExercise = request.postWithResponseBody("/api/file-upload-exercises", fileUploadExercise, FileUploadExercise.class);

        assertThat(receivedFileUploadExercise).isNotNull();
        assertThat(receivedFileUploadExercise.getId()).isNotNull();
        assertThat(receivedFileUploadExercise.getFilePattern()).isEqualTo(filePattern);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void getFileUploadExercise() throws Exception {
        database.addCourseWithTwoFileUploadExercise();
        FileUploadExercise fileUploadExercise = (FileUploadExercise) exerciseRepo.findAll().get(0);

        FileUploadExercise receivedFileUploadExercise = request.get("/api/file-upload-exercises/" + fileUploadExercise.getId(), HttpStatus.OK, FileUploadExercise.class);

        assertThat(fileUploadExercise.getId()).isEqualTo(receivedFileUploadExercise.getId());
        assertThat(fileUploadExercise).isEqualTo(receivedFileUploadExercise);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void deleteFileUploadExercise() throws Exception {
        database.addCourseWithTwoFileUploadExercise();
        FileUploadExercise fileUploadExercise = (FileUploadExercise) exerciseRepo.findAll().get(0);
        FileUploadExercise fileUploadExercise2 = (FileUploadExercise) exerciseRepo.findAll().get(1);

        request.delete("/api/file-upload-exercises/" + fileUploadExercise.getId(), HttpStatus.OK);
        request.delete("/api/file-upload-exercises/" + fileUploadExercise2.getId(), HttpStatus.OK);

        assertThat(exerciseRepo.findAll().isEmpty()).isTrue();
    }
}
