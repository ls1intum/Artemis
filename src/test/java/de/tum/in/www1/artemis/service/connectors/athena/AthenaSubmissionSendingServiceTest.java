package de.tum.in.www1.artemis.service.connectors.athena;

import static de.tum.in.www1.artemis.connector.AthenaRequestMockProvider.ATHENA_MODULE_PROGRAMMING_TEST;
import static de.tum.in.www1.artemis.connector.AthenaRequestMockProvider.ATHENA_MODULE_TEXT_TEST;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractAthenaTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseUtilService;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseUtilService;
import de.tum.in.www1.artemis.participation.ParticipationFactory;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.repository.SubmissionRepository;
import de.tum.in.www1.artemis.user.UserUtilService;

class AthenaSubmissionSendingServiceTest extends AbstractAthenaTest {

    private static final String TEST_PREFIX = "athenasubmissionsendingservice";

    private static final int MAX_NUMBER_OF_TOTAL_PARTICIPATIONS = 190;

    private static final Language DEFAULT_SUBMISSION_LANGUAGE = Language.ENGLISH;

    private static final String DEFAULT_SUBMISSION_TEXT = "This is a test submission.";

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private AthenaModuleService athenaModuleService;

    @Autowired
    private AthenaDTOConverterService athenaDTOConverterService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private StudentParticipationRepository studentParticipationRepository;

    @Autowired
    private UserUtilService userUtilService;

    private AthenaSubmissionSendingService athenaSubmissionSendingService;

    private TextExercise textExercise;

    private ProgrammingExercise programmingExercise;

    @BeforeEach
    void setUp() {
        athenaRequestMockProvider.enableMockingOfRequests();
        // we need to have one student per participation, otherwise the database constraints cannot be fulfilled
        userUtilService.addUsers(TEST_PREFIX, MAX_NUMBER_OF_TOTAL_PARTICIPATIONS, 0, 0, 0);

        athenaSubmissionSendingService = new AthenaSubmissionSendingService(athenaRequestMockProvider.getRestTemplate(), submissionRepository, athenaModuleService,
                athenaDTOConverterService);

        textExercise = textExerciseUtilService.createSampleTextExercise(null);
        textExercise.setFeedbackSuggestionModule(ATHENA_MODULE_TEXT_TEST);

        programmingExercise = programmingExerciseUtilService.createSampleProgrammingExercise();
        programmingExercise.setFeedbackSuggestionModule(ATHENA_MODULE_PROGRAMMING_TEST);
    }

    @AfterEach
    void tearDown() {
        submissionRepository.deleteAll(submissionRepository.findByParticipation_ExerciseIdAndSubmittedIsTrue(textExercise.getId()));
        studentParticipationRepository.deleteAll(studentParticipationRepository.findByExerciseId(textExercise.getId()));
        submissionRepository.deleteAll(submissionRepository.findByParticipation_ExerciseIdAndSubmittedIsTrue(programmingExercise.getId()));
        studentParticipationRepository.deleteAll(studentParticipationRepository.findByExerciseId(programmingExercise.getId()));
    }

    private void createTextSubmissionsForSubmissionSending(int totalSubmissions) {
        for (long i = 0; i < totalSubmissions; i++) {
            var studentParticipation = ParticipationFactory.generateStudentParticipation(InitializationState.FINISHED, textExercise,
                    userUtilService.getUserByLogin(TEST_PREFIX + "student" + (i + 1)));
            studentParticipation.setExercise(textExercise);
            studentParticipationRepository.save(studentParticipation);
            var submission = new TextSubmission();
            submission.setLanguage(DEFAULT_SUBMISSION_LANGUAGE);
            submission.setText(DEFAULT_SUBMISSION_TEXT);
            submission.setSubmitted(true);
            // Set a submission date so that the submission is found
            submission.setSubmissionDate(studentParticipation.getInitializationDate());
            submission.setParticipation(studentParticipation);
            submissionRepository.save(submission);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testSendTextSubmissionsSuccess() {
        createTextSubmissionsForSubmissionSending(1);
        athenaRequestMockProvider.mockSendSubmissionsAndExpect("text", jsonPath("$.exercise.id").value(textExercise.getId()),
                jsonPath("$.exercise.title").value(textExercise.getTitle()), jsonPath("$.exercise.maxPoints").value(textExercise.getMaxPoints()),
                jsonPath("$.exercise.bonusPoints").value(textExercise.getBonusPoints()), jsonPath("$.exercise.gradingInstructions").value(textExercise.getGradingInstructions()),
                jsonPath("$.exercise.problemStatement").value(textExercise.getProblemStatement()), jsonPath("$.submissions[0].exerciseId").value(textExercise.getId()),
                jsonPath("$.submissions[0].text").value(DEFAULT_SUBMISSION_TEXT), jsonPath("$.submissions[0].language").value(DEFAULT_SUBMISSION_LANGUAGE.toString()));

        athenaSubmissionSendingService.sendSubmissions(textExercise);
        athenaRequestMockProvider.verify();
    }

    private void createProgrammingSubmissionForSubmissionSending() {
        var studentParticipation = ParticipationFactory.generateStudentParticipation(InitializationState.FINISHED, programmingExercise,
                userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        studentParticipation.setExercise(programmingExercise);
        studentParticipationRepository.save(studentParticipation);
        var submission = ParticipationFactory.generateProgrammingSubmission(true);
        submission.setParticipation(studentParticipation);
        submissionRepository.save(submission);
        athenaRequestMockProvider.verify();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testSendSubmissionsSuccessProgramming() {
        createProgrammingSubmissionForSubmissionSending();
        athenaRequestMockProvider.mockSendSubmissionsAndExpect("programming", jsonPath("$.exercise.id").value(programmingExercise.getId()),
                jsonPath("$.exercise.title").value(programmingExercise.getTitle()), jsonPath("$.exercise.maxPoints").value(programmingExercise.getMaxPoints()),
                jsonPath("$.exercise.bonusPoints").value(programmingExercise.getBonusPoints()),
                jsonPath("$.exercise.gradingInstructions").value(programmingExercise.getGradingInstructions()),
                jsonPath("$.exercise.problemStatement").value(programmingExercise.getProblemStatement()),
                jsonPath("$.submissions[0].exerciseId").value(programmingExercise.getId()), jsonPath("$.submissions[0].repositoryUri").isString());

        athenaSubmissionSendingService.sendSubmissions(programmingExercise);
        athenaRequestMockProvider.verify();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testSendNoSubmissions() {
        athenaSubmissionSendingService.sendSubmissions(textExercise);
        athenaSubmissionSendingService.sendSubmissions(programmingExercise);
        athenaRequestMockProvider.verify(); // Ensure that there was no request
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testSendMultipleSubmissionBatches() {
        createTextSubmissionsForSubmissionSending(MAX_NUMBER_OF_TOTAL_PARTICIPATIONS); // 190 = almost twice the batch size (100)
        // expect two batches of submissions
        athenaRequestMockProvider.mockSendSubmissionsAndExpect("text", jsonPath("$.exercise.id").value(textExercise.getId()),
                // We cannot check IDs or similar here because the submissions are not ordered
                jsonPath("$.submissions[0].text").value(DEFAULT_SUBMISSION_TEXT));
        athenaRequestMockProvider.mockSendSubmissionsAndExpect("text", jsonPath("$.exercise.id").value(textExercise.getId()),
                jsonPath("$.submissions[0].text").value(DEFAULT_SUBMISSION_TEXT));

        athenaSubmissionSendingService.sendSubmissions(textExercise);
        athenaRequestMockProvider.verify();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testSendSubmissionsWithFeedbackSuggestionsDisabledText() {
        textExercise.setFeedbackSuggestionModule(null);
        assertThatThrownBy(() -> athenaSubmissionSendingService.sendSubmissions(textExercise)).isInstanceOf(IllegalArgumentException.class);
    }
}
