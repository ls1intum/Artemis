package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseUtilService;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.user.UserUtilService;

class LongFeedbackResourceIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "longfeedbackintegration";

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    private Result resultStudent1;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 0, 0);

        final Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        final ProgrammingExercise exercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);

        resultStudent1 = participationUtilService.addProgrammingParticipationWithResultForExercise(exercise, TEST_PREFIX + "student1");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void getLongFeedbackAsStudent() throws Exception {
        final Feedback feedback = addLongFeedbackToResult(resultStudent1);

        final LongFeedbackText longFeedbackText = request.get(getUrl(resultStudent1.getId(), feedback.getId()), HttpStatus.OK, LongFeedbackText.class);
        assertThat(longFeedbackText.getId()).isEqualTo(feedback.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1")
    void getLongFeedbackAsTutor() throws Exception {
        final Feedback feedback = addLongFeedbackToResult(resultStudent1);

        final LongFeedbackText longFeedbackText = request.get(getUrl(resultStudent1.getId(), feedback.getId()), HttpStatus.OK, LongFeedbackText.class);
        assertThat(longFeedbackText.getId()).isEqualTo(feedback.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void badRequestOnWrongResultId() throws Exception {
        final Feedback feedback = addLongFeedbackToResult(resultStudent1);

        final LongFeedbackText longFeedbackText = request.get(getUrl(resultStudent1.getId() + 1, feedback.getId()), HttpStatus.BAD_REQUEST, LongFeedbackText.class);
        assertThat(longFeedbackText).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void notFoundIfNotExists() throws Exception {
        final Feedback feedback = addLongFeedbackToResult(resultStudent1);

        final LongFeedbackText longFeedbackText = request.get(getUrl(resultStudent1.getId(), feedback.getId() + 1), HttpStatus.NOT_FOUND, LongFeedbackText.class);
        assertThat(longFeedbackText).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void notFoundIfOnlyShortFeedback() throws Exception {
        final Feedback feedback = new Feedback();
        feedback.setDetailText("short text");
        participationUtilService.addFeedbackToResult(feedback, resultStudent1);

        final LongFeedbackText longFeedbackText = request.get(getUrl(resultStudent1.getId(), feedback.getId()), HttpStatus.NOT_FOUND, LongFeedbackText.class);
        assertThat(longFeedbackText).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2")
    void accessForbiddenIfNotOwnParticipation() throws Exception {
        final Feedback feedback = addLongFeedbackToResult(resultStudent1);

        final LongFeedbackText longFeedbackText = request.get(getUrl(resultStudent1.getId(), feedback.getId()), HttpStatus.FORBIDDEN, LongFeedbackText.class);
        assertThat(longFeedbackText).isNull();
    }

    private String getUrl(final long resultId, final long feedbackId) {
        return String.format("/api/results/%d/feedbacks/%d/long-feedback", resultId, feedbackId);
    }

    private Feedback addLongFeedbackToResult(final Result result) {
        final Feedback feedback = new Feedback();
        feedback.setDetailText("a".repeat(Constants.FEEDBACK_DETAIL_TEXT_DATABASE_MAX_LENGTH + 10));

        participationUtilService.addFeedbackToResult(feedback, result);

        return feedback;
    }
}
