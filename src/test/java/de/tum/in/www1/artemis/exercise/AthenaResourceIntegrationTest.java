package de.tum.in.www1.artemis.exercise;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractAthenaTest;
import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseUtilService;
import de.tum.in.www1.artemis.participation.ParticipationFactory;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.repository.TextSubmissionRepository;
import de.tum.in.www1.artemis.user.UserUtilService;

class AthenaResourceIntegrationTest extends AbstractAthenaTest {

    private static final String TEST_PREFIX = "athenaintegration";

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private TextSubmissionRepository textSubmissionRepository;

    @Autowired
    private StudentParticipationRepository studentParticipationRepository;

    @Autowired
    private UserUtilService userUtilService;

    private TextExercise textExercise;

    private TextSubmission textSubmission;

    @BeforeEach
    protected void initTestCase() {
        super.initTestCase();

        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        var course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        textExercise = exerciseUtilService.findTextExerciseWithTitle(course.getExercises(), "Text");
        textSubmission = ParticipationFactory.generateTextSubmission("This is a test sentence. This is a second test sentence. This is a third test sentence.", Language.ENGLISH,
                true);
        var studentParticipation = ParticipationFactory.generateStudentParticipation(InitializationState.FINISHED, textExercise,
                userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        studentParticipationRepository.save(studentParticipation);
        textSubmission.setParticipation(studentParticipation);
        textSubmissionRepository.save(textSubmission);

        athenaRequestMockProvider.mockGetFeedbackSuggestionsAndExpect();
    }

    @AfterEach
    void tearDown() throws Exception {
        athenaRequestMockProvider.reset();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetFeedbackSuggestionsSuccess() throws Exception {
        List<Feedback> response = request.getList("/api/athena/exercises/" + textExercise.getId() + "/submissions/" + textSubmission.getId() + "/feedback-suggestions",
                HttpStatus.OK, Feedback.class);
        assertThat(response).as("response is not empty").isNotEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetFeedbackSuggestionsNotFound() throws Exception {
        request.get("/api/athena/exercises/9999/submissions/9999/feedback-suggestions", HttpStatus.NOT_FOUND, List.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "STUDENT")
    void testGetFeedbackSuggestionsAccessForbidden() throws Exception {
        request.get("/api/athena/exercises/" + textExercise.getId() + "/submissions/" + textSubmission.getId() + "/feedback-suggestions", HttpStatus.FORBIDDEN, List.class);
    }
}
