package de.tum.in.www1.artemis.service.connectors.athena;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.in.www1.artemis.AbstractAthenaTest;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseUtilService;
import de.tum.in.www1.artemis.participation.ParticipationFactory;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.repository.TextSubmissionRepository;
import de.tum.in.www1.artemis.user.UserUtilService;

class AthenaSubmissionSendingServiceTest extends AbstractAthenaTest {

    private static final String TEST_PREFIX = "athenasubmissionsendingservice";

    private static final int MAX_NUMBER_OF_TOTAL_PARTICIPATIONS = 190;

    private static final Language DEFAULT_SUBMISSION_LANGUAGE = Language.ENGLISH;

    private static final String DEFAULT_SUBMISSION_TEXT = "This is a test submission.";

    @Autowired
    private TextSubmissionRepository textSubmissionRepository;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private StudentParticipationRepository studentParticipationRepository;

    @Autowired
    private UserUtilService userUtilService;

    private AthenaSubmissionSendingService athenaSubmissionSendingService;

    private TextExercise textExercise;

    @BeforeEach
    void setUp() {
        athenaRequestMockProvider.enableMockingOfRequests();
        // we need to have one student per participation, otherwise the database constraints cannot be fulfilled
        userUtilService.addUsers(TEST_PREFIX, MAX_NUMBER_OF_TOTAL_PARTICIPATIONS, 0, 0, 0);

        athenaSubmissionSendingService = new AthenaSubmissionSendingService(athenaRequestMockProvider.getRestTemplate(), textSubmissionRepository);
        ReflectionTestUtils.setField(athenaSubmissionSendingService, "athenaUrl", athenaUrl);

        textExercise = textExerciseUtilService.createSampleTextExercise(null);
    }

    @AfterEach
    void tearDown() {
        textSubmissionRepository.deleteAll(textSubmissionRepository.findByParticipation_ExerciseIdAndSubmittedIsTrue(textExercise.getId()));
        studentParticipationRepository.deleteAll(studentParticipationRepository.findByExerciseId(textExercise.getId()));
    }

    private void createTextSubmissionsForSubmissionSending(int totalSubmissions) {
        for (long i = 0; i < totalSubmissions; i++) {
            var submission = new TextSubmission();
            submission.setLanguage(DEFAULT_SUBMISSION_LANGUAGE);
            submission.setText(DEFAULT_SUBMISSION_TEXT);
            submission.setSubmitted(true);
            var studentParticipation = ParticipationFactory.generateStudentParticipation(InitializationState.FINISHED, textExercise,
                    userUtilService.getUserByLogin(TEST_PREFIX + "student" + (i + 1)));
            studentParticipation.setExercise(textExercise);
            studentParticipationRepository.save(studentParticipation);
            submission.setParticipation(studentParticipation);
            textSubmissionRepository.save(submission);
        }
    }

    @Test
    void testSendSubmissionsSuccess() {
        createTextSubmissionsForSubmissionSending(1);
        athenaRequestMockProvider.mockSendSubmissionsAndExpect(jsonPath("$.exercise.id").value(textExercise.getId()), jsonPath("$.exercise.title").value(textExercise.getTitle()),
                jsonPath("$.exercise.maxPoints").value(textExercise.getMaxPoints()), jsonPath("$.exercise.bonusPoints").value(textExercise.getBonusPoints()),
                jsonPath("$.exercise.gradingInstructions").value(textExercise.getGradingInstructions()),
                jsonPath("$.exercise.problemStatement").value(textExercise.getProblemStatement()), jsonPath("$.submissions[0].exerciseId").value(textExercise.getId()),
                jsonPath("$.submissions[0].text").value(DEFAULT_SUBMISSION_TEXT), jsonPath("$.submissions[0].language").value(DEFAULT_SUBMISSION_LANGUAGE.toString()));

        athenaSubmissionSendingService.sendSubmissions(textExercise);
    }

    @Test
    void testSendNoSubmissions() {
        athenaRequestMockProvider.ensureNoRequest();
        athenaSubmissionSendingService.sendSubmissions(textExercise);
    }

    @Test
    void testSendMultipleSubmissionBatches() {
        createTextSubmissionsForSubmissionSending(MAX_NUMBER_OF_TOTAL_PARTICIPATIONS); // 190 = almost twice the batch size (100)
        // expect two batches of submissions
        athenaRequestMockProvider.mockSendSubmissionsAndExpect(jsonPath("$.exercise.id").value(textExercise.getId()),
                // We cannot check IDs or similar here because the submissions are not ordered
                jsonPath("$.submissions[0].text").value(DEFAULT_SUBMISSION_TEXT));
        athenaRequestMockProvider.mockSendSubmissionsAndExpect(jsonPath("$.exercise.id").value(textExercise.getId()),
                jsonPath("$.submissions[0].text").value(DEFAULT_SUBMISSION_TEXT));

        athenaSubmissionSendingService.sendSubmissions(textExercise);
    }

    @Test
    void testSendSubmissionsWithFeedbackSuggestionsDisabled() {
        textExercise.setAssessmentType(AssessmentType.MANUAL); // disable feedback suggestions
        assertThatThrownBy(() -> athenaSubmissionSendingService.sendSubmissions(textExercise)).isInstanceOf(IllegalArgumentException.class);
    }
}
