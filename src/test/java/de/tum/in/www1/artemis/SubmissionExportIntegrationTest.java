package de.tum.in.www1.artemis;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.*;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.repository.ExamSessionRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.StudentExamRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.RequestUtilService;
import de.tum.in.www1.artemis.web.rest.dto.SubmissionExportOptionsDTO;
import org.dom4j.Text;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class SubmissionExportIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    DatabaseUtilService database;

    @Autowired
    RequestUtilService request;

    @Autowired
    ExerciseRepository exerciseRepository;

    private List<User> users;

    private Course course1;

    private ModelingExercise modelingExercise;

    private TextExercise textExercise;

    private SubmissionExportOptionsDTO baseExportOptions;
    private ModelingSubmission modelingSubmission1;
    private ModelingSubmission modelingSubmission2;
    private ModelingSubmission modelingSubmission3;
    private TextSubmission textSubmission1;
    private TextSubmission textSubmission2;
    private TextSubmission textSubmission3;

    @BeforeEach
    public void initTestCase() {
        users = database.addUsers(3, 1, 1);
        users.remove(database.getUserByLogin("admin"));
        course1 = database.addCourseWithModelingAndTextExercise();
        course1.getExercises().forEach(exercise -> {
            database.addParticipationForExercise(exercise, "student1");
            database.addParticipationForExercise(exercise, "student2");
            database.addParticipationForExercise(exercise, "student3");

            if (exercise instanceof ModelingExercise) {
                modelingExercise = (ModelingExercise) exercise;
                try {
                    modelingSubmission1 = database.addModelingSubmissionFromResources(modelingExercise, "test-data/model-submission/model.54727.json", "student1");
                    modelingSubmission2 = database.addModelingSubmissionFromResources(modelingExercise, "test-data/model-submission/model.54742.json", "student2");
                    modelingSubmission3 = database.addModelingSubmissionFromResources(modelingExercise, "test-data/model-submission/model.54745.json", "student3");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else if (exercise instanceof TextExercise) {
                textExercise = (TextExercise) exercise;

                textSubmission1 = database.addTextSubmission(textExercise, ModelFactory.generateTextSubmission("example text", Language.ENGLISH, true), "student1");
                textSubmission2 = database.addTextSubmission(textExercise, ModelFactory.generateTextSubmission("some other text", Language.ENGLISH, true), "student2");
                textSubmission3 = database.addTextSubmission(textExercise, ModelFactory.generateTextSubmission("a third text", Language.ENGLISH, true), "student3");
            }
        });

        baseExportOptions = new SubmissionExportOptionsDTO();
        baseExportOptions.setExportAllParticipants(true);
        baseExportOptions.setFilterLateSubmissions(false);
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
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testNoSubmissionsForStudent_asInstructor() throws Exception {
        baseExportOptions.setExportAllParticipants(false);
        baseExportOptions.setParticipantIdentifierList("nonexistentstudent");
        request.post("/api/text-exercises/" + textExercise.getId() + "/export-submissions", baseExportOptions, HttpStatus.BAD_REQUEST);
        request.post("/api/modeling-exercises/" + modelingExercise.getId() + "/export-submissions", baseExportOptions, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testNoSubmissionsForDate_asInstructor() throws Exception {
        baseExportOptions.setFilterLateSubmissions(true);
        baseExportOptions.setFilterLateSubmissionsDate(ZonedDateTime.now().minusDays(2));
        request.post("/api/text-exercises/" + textExercise.getId() + "/export-submissions", baseExportOptions, HttpStatus.BAD_REQUEST);
        request.post("/api/modeling-exercises/" + modelingExercise.getId() + "/export-submissions", baseExportOptions, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testExportAll() throws Exception {
        File textZip = request.postWithResponseBodyFile("/api/text-exercises/" + textExercise.getId() + "/export-submissions", baseExportOptions, HttpStatus.OK);
        assertZipContains(textZip, textSubmission1, textSubmission2, textSubmission3);

        File modelingZip = request.postWithResponseBodyFile("/api/modeling-exercises/" + modelingExercise.getId() + "/export-submissions", baseExportOptions, HttpStatus.OK);
        assertZipContains(modelingZip, modelingSubmission1, modelingSubmission2, modelingSubmission3);
    }

    private void assertZipContains(File file, Submission ...submissions) {
        try {
            ZipFile zip = new ZipFile(file);
            for (Submission s : submissions) {
                assertThat(zip.getEntry(getSubmissionFileName(s))).isNotNull();
            }
        } catch (IOException e) {
            fail("Could not read zip file.");
        }
    }

    private String getSubmissionFileName(Submission submission) {
        if (submission instanceof TextSubmission) {
            return textExercise.getTitle() + "-" + ((StudentParticipation)submission.getParticipation()).getParticipantIdentifier() + "-" + submission.getId() + ".txt";
        } else if (submission instanceof ModelingSubmission) {
            return modelingExercise.getTitle() + "-" + ((StudentParticipation)submission.getParticipation()).getParticipantIdentifier() + "-" + submission.getId() + ".json";
        } else {
            fail("Unknown submission type");
            return "";
        }
    }

}
