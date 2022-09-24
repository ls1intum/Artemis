package de.tum.in.www1.artemis.plagiarism;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismComparison;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismResult;
import de.tum.in.www1.artemis.util.FileUtils;

class PlagiarismCheckIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private Course course;

    @BeforeEach
    void initTestCase() throws IOException {
        String submissionText = "Lorem ipsum dolor sit amet, consectetur adipiscing elit";
        String submissionModel = FileUtils.loadFileFromResources("test-data/model-submission/model.54727.json");
        int studentAmount = 5;

        course = database.addCourseWithOneFinishedTextExerciseAndSimilarSubmissions(submissionText, studentAmount);
        database.addOneFinishedModelingExerciseAndSimilarSubmissionsToTheCourse(submissionModel, studentAmount, course);
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    void testCheckPlagiarismResultForTextExercise() throws Exception {
        var textExercise = database.getFirstExerciseWithType(course, TextExercise.class);
        String path = "/api/text-exercises/" + textExercise.getId() + "/check-plagiarism";
        createAndTestPlagiarismResult(path);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testCheckPlagiarismResultForModelingExercise() throws Exception {
        var modelingExercise = database.getFirstExerciseWithType(course, ModelingExercise.class);
        String path = "/api/modeling-exercises/" + modelingExercise.getId() + "/check-plagiarism";
        createAndTestPlagiarismResult(path);
    }

    /***
     * Create the plagiarism result response based on the provided path
     * @param path The provided path to the rest endpoint
     */
    private void createAndTestPlagiarismResult(String path) throws Exception {
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("similarityThreshold", "50");
        params.add("minimumScore", "0");
        params.add("minimumSize", "0");

        PlagiarismResult<?> plagiarismResult = request.get(path, HttpStatus.OK, PlagiarismResult.class, params);

        for (PlagiarismComparison<?> comparison : plagiarismResult.getComparisons()) {
            var submissionA = comparison.getSubmissionA();
            var submissionB = comparison.getSubmissionB();

            assertThat(submissionA).as("should have a submission A").isNotNull();
            assertThat(submissionB).as("should have a submission B").isNotNull();

            assertThat(submissionA.getPlagiarismComparison()).as("should have a bidirectional connection").isEqualTo(comparison);
            assertThat(submissionB.getPlagiarismComparison()).as("should have a bidirectional connection").isEqualTo(comparison);
        }
    }

}
