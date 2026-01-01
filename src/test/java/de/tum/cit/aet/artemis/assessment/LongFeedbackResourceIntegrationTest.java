package de.tum.cit.aet.artemis.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class LongFeedbackResourceIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "longfeedbackintegration";

    private static final String LONG_FEEDBACK = "a".repeat(Constants.FEEDBACK_DETAIL_TEXT_DATABASE_MAX_LENGTH + 10);

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    private Result resultStudent1;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 0, 0);

        final Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        final ProgrammingExercise exercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);

        resultStudent1 = participationUtilService.addProgrammingParticipationWithResultForExercise(exercise, TEST_PREFIX + "student1");
        ProgrammingExerciseStudentParticipation programmingExerciseStudentParticipation = programmingExerciseParticipationService
                .findStudentParticipationByExerciseAndStudentId(exercise, TEST_PREFIX + "student1");
        programmingExerciseUtilService.addProgrammingSubmissionToResultAndParticipation(resultStudent1, programmingExerciseStudentParticipation, "test");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void getLongFeedbackAsStudent() throws Exception {
        final Feedback feedback = addLongFeedbackToResult(resultStudent1);

        final String longFeedbackText = request.get(getUrl(feedback.getId()), HttpStatus.OK, String.class);
        assertThat(longFeedbackText).isEqualTo(LONG_FEEDBACK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1")
    void getLongFeedbackAsTutor() throws Exception {
        final Feedback feedback = addLongFeedbackToResult(resultStudent1);

        final String longFeedbackText = request.get(getUrl(feedback.getId()), HttpStatus.OK, String.class);
        assertThat(longFeedbackText).isEqualTo(LONG_FEEDBACK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void notFoundIfNotExists() throws Exception {
        final Feedback feedback = addLongFeedbackToResult(resultStudent1);

        final String longFeedbackText = request.get(getUrl(feedback.getId() + 1), HttpStatus.NOT_FOUND, String.class);
        assertThat(longFeedbackText).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void notFoundIfOnlyShortFeedback() throws Exception {
        final Feedback feedback = new Feedback();
        feedback.setDetailText("short text");
        Result savedResult = participationUtilService.addFeedbackToResult(feedback, resultStudent1);
        Feedback savedFeedback = savedResult.getFeedbacks().stream().reduce((first, second) -> second).orElseThrow();

        final String longFeedbackText = request.get(getUrl(savedFeedback.getId()), HttpStatus.NOT_FOUND, String.class);
        assertThat(longFeedbackText).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2")
    void accessForbiddenIfNotOwnParticipation() throws Exception {
        final Feedback feedback = addLongFeedbackToResult(resultStudent1);

        final String longFeedbackText = request.get(getUrl(feedback.getId()), HttpStatus.FORBIDDEN, String.class);
        assertThat(longFeedbackText).isNull();
    }

    private String getUrl(final long feedbackId) {
        return String.format("/api/assessment/feedbacks/%d/long-feedback", feedbackId);
    }

    private Feedback addLongFeedbackToResult(final Result result) {
        final Feedback feedback = new Feedback();
        feedback.setDetailText(LONG_FEEDBACK);

        Result savedResult = participationUtilService.addFeedbackToResult(feedback, result);

        // Return the saved feedback from the result to ensure it has an ID
        return savedResult.getFeedbacks().stream().reduce((first, second) -> second).orElseThrow();
    }
}
