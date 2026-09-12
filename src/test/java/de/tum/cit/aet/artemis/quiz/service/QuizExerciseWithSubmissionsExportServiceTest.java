package de.tum.cit.aet.artemis.quiz.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.service.ArchivalReportEntry;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Tests the quiz exercise details file a course or exam archive contains.
 */
class QuizExerciseWithSubmissionsExportServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "quizexportservice";

    @Autowired
    private QuizExerciseWithSubmissionsExportService quizExerciseWithSubmissionsExportService;

    @Autowired
    private QuizExerciseUtilService quizExerciseUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @TempDir
    private Path exportDir;

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void exportExerciseWithSubmissions_writesTheQuestionsAndTheirSolutionsWithoutTheEntityGraph() throws Exception {
        Course course = courseUtilService.createCourse();
        QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuizWithAllQuestionTypes(course, ZonedDateTime.now().minusHours(5), ZonedDateTime.now().minusHours(2),
                ZonedDateTime.now().minusHours(1), QuizMode.SYNCHRONIZED);
        List<String> exportErrors = new ArrayList<>();

        quizExerciseWithSubmissionsExportService.exportExerciseWithSubmissions(quizExercise, exportDir, exportErrors, new ArrayList<ArchivalReportEntry>());

        // the fixture has no image files on disk, so only the details file is asserted here
        assertThat(exportErrors).as("the details are written without a JSON error").noneMatch(error -> error.contains("JSON"));
        String details = Files.readString(exportDir.resolve("Exercise-Details-" + quizExercise.getSanitizedExerciseTitle() + ".json"));
        assertThat(details).as("the questions of every type are exported with their solutions").contains("\"type\":\"multiple-choice\"").contains("\"type\":\"drag-and-drop\"")
                .contains("\"type\":\"short-answer\"").contains("\"isCorrect\":true");
        assertThat(details).as("no student data and no entity back references are written").doesNotContain("studentParticipations").doesNotContain("\"exercises\"");
    }
}
