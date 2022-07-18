package de.tum.in.www1.artemis;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseType;
import de.tum.in.www1.artemis.domain.plagiarism.*;
import de.tum.in.www1.artemis.domain.plagiarism.modeling.ModelingSubmissionElement;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextSubmissionElement;
import de.tum.in.www1.artemis.repository.TextExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismComparisonRepository;
import de.tum.in.www1.artemis.util.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import java.io.IOException;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

public class PlagiarismCheckForExcercisesIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlagiarismComparisonRepository plagiarismComparisonRepository;

    private static Course textExerciseCourse;
    private static Course modelingExerciseCourse;

    private static TextExercise textExercise;

    private static ArrayList<PlagiarismCase> plagiarismCases;

    private static int studentAmount;

    @BeforeEach
    public void initTestCase() throws IOException {
        String submissionText = "Lorem ipsum dolor sit amet, consectetur adipiscing elit";
        String submissionModel = FileUtils.loadFileFromResources("test-data/model-submission/model.54727.json");
        studentAmount = 5;

        textExerciseCourse = database.addCourseWithOneFinishedTextExerciseAndSimilarSubmissions(submissionText, studentAmount);
        modelingExerciseCourse = database.addCourseWithOneFinishedModelingExerciseAndSimilarSubmissions(submissionModel, studentAmount, textExerciseCourse);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    public void testCheckPlagiarismResultForTextExercise() throws Exception {
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("similarityThreshold", "50");
        params.add("minimumScore", "0");
        params.add("minimumSize", "0");

        var plagiarismResultResponse = request.get("/api/text-exercises/" + textExerciseCourse.getId() + "/check-plagiarism",
            HttpStatus.OK, PlagiarismResult.class, params);
        int comparisonAmount = possibleComparisons(studentAmount);

        assertThat(plagiarismResultResponse.getComparisons().size()).as("should have the comparison amount").isEqualTo(comparisonAmount);

        for (var comparison : plagiarismResultResponse.getComparisons()) {
            var submissionA = ((PlagiarismComparison<TextSubmissionElement>) comparison).getSubmissionA();
            var submissionB = ((PlagiarismComparison<TextSubmissionElement>) comparison).getSubmissionB();

            assertThat(submissionA).as("should have a submission A").isNotNull();
            assertThat(submissionB).as("should have a submission B").isNotNull();

            assertThat(submissionA.getPlagiarismComparison()).as("should have a biderectional connection").isEqualTo(comparison);
            assertThat(submissionB.getPlagiarismComparison()).as("should have a biderectional connection").isEqualTo(comparison);
        }

    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testCheckPlagiarismResultForModelingExercise() throws Exception {
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("similarityThreshold", "50");
        params.add("minimumScore", "0");
        params.add("minimumSize", "0");

        var modelingExercise = modelingExerciseCourse.getExercises().stream().filter(ex -> ex.getExerciseType() == ExerciseType.MODELING).findFirst().get();
        var plagiarismResultResponse = request.get("/api/modeling-exercises/" + modelingExercise.getId() + "/check-plagiarism",
            HttpStatus.OK, PlagiarismResult.class, params);
        int comparisonAmount = possibleComparisons(studentAmount);

        assertThat(plagiarismResultResponse.getComparisons().size()).as("should have the comparison amount").isEqualTo(comparisonAmount);

        for (var comparison : plagiarismResultResponse.getComparisons()) {
            var submissionA = ((PlagiarismComparison<ModelingSubmissionElement>) comparison).getSubmissionA();
            var submissionB = ((PlagiarismComparison<ModelingSubmissionElement>) comparison).getSubmissionB();

            assertThat(submissionA).as("should have a submission A").isNotNull();
            assertThat(submissionB).as("should have a submission B").isNotNull();

            assertThat(submissionA.getPlagiarismComparison()).as("should have a biderectional connection").isEqualTo(comparison);
            assertThat(submissionB.getPlagiarismComparison()).as("should have a biderectional connection").isEqualTo(comparison);
        }

    }

    /***
     * Calculate recursively the amount of the comparisons based on the student amount with the same submission
     * @param studentAmount The amount of students with the same submission
     * @return The amount of the comparisons
     */
    private int possibleComparisons(int studentAmount) {
        if (studentAmount > 0) {
            return studentAmount - 1 + possibleComparisons(studentAmount - 1);
        } else
            return studentAmount;
    }

}
