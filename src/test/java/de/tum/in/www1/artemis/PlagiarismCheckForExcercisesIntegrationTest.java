package de.tum.in.www1.artemis;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.plagiarism.*;
import de.tum.in.www1.artemis.repository.TextExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismComparisonRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

public class PlagiarismCheckForExcercisesIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlagiarismComparisonRepository plagiarismComparisonRepository;

    private static Course course;

    private static TextExercise textExercise;

    private static ArrayList<PlagiarismCase> plagiarismCases;

    @BeforeEach
    public void initTestCase() {
        String submissionText = "Lorem ipsum dolor sit amet, consectetur adipiscing elit";
        course = database.addCourseWithOneFinishedTextExerciseAndSimilarSubmissions(submissionText);
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    public void testCheckPlagiarismResultForTextExercise() throws Exception {
        var plagiarismResultResponse = request.get("/api/text-exercises/" + course.getId() + "/check-plagiarism", HttpStatus.OK, PlagiarismResult.class);
    }

}
