package de.tum.in.www1.artemis.service.connectors.athena;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

    @Mock
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
        textSubmissionRepository.deleteAll();
        studentParticipationRepository.deleteAll();
    }

    private void mockTextSubmissionRepository(long exerciseId, int totalSubmissions) {
        when(textSubmissionRepository.findByParticipation_ExerciseIdAndSubmittedIsTrue(eq(exerciseId), any(Pageable.class))).thenAnswer(invocation -> {
            Pageable pageable = invocation.getArgument(1);
            int page = pageable.getPageNumber();
            int size = pageable.getPageSize();
            int start = page * size;
            int end = Math.min(start + size, totalSubmissions);
            List<TextSubmission> batch = new ArrayList<>();
            for (long i = start; i < end; i++) {
                var submission = new TextSubmission(i);
                submission.setLanguage(DEFAULT_SUBMISSION_LANGUAGE);
                submission.setText(DEFAULT_SUBMISSION_TEXT);
                var studentParticipation = ParticipationFactory.generateStudentParticipation(InitializationState.FINISHED, textExercise,
                        userUtilService.getUserByLogin(TEST_PREFIX + "student" + (i + 1)));
                studentParticipationRepository.save(studentParticipation);
                submission.setParticipation(studentParticipation);
                batch.add(submission);
            }
            return new PageImpl<>(batch, PageRequest.of(page, size), totalSubmissions);
        });
    }

    @Test
    void testSendSubmissionsSuccess() {
        mockTextSubmissionRepository(textExercise.getId(), 1);
        athenaRequestMockProvider.mockSendSubmissionsAndExpect(jsonPath("$.exercise.id").value(textExercise.getId()), jsonPath("$.exercise.title").value(textExercise.getTitle()),
                jsonPath("$.exercise.maxPoints").value(textExercise.getMaxPoints()), jsonPath("$.exercise.bonusPoints").value(textExercise.getBonusPoints()),
                jsonPath("$.exercise.gradingInstructions").value(textExercise.getGradingInstructions()),
                jsonPath("$.exercise.problemStatement").value(textExercise.getProblemStatement()), jsonPath("$.submissions[0].id").value(0),
                jsonPath("$.submissions[0].exerciseId").value(textExercise.getId()), jsonPath("$.submissions[0].text").value(DEFAULT_SUBMISSION_TEXT),
                jsonPath("$.submissions[0].language").value(DEFAULT_SUBMISSION_LANGUAGE.toString()));

        athenaSubmissionSendingService.sendSubmissions(textExercise);
    }

    @Test
    void testSendNoSubmissions() {
        athenaRequestMockProvider.ensureNoRequest();
        mockTextSubmissionRepository(textExercise.getId(), 0);
        athenaSubmissionSendingService.sendSubmissions(textExercise);
    }

    @Test
    void testSendMultipleSubmissionBatches() {
        mockTextSubmissionRepository(textExercise.getId(), MAX_NUMBER_OF_TOTAL_PARTICIPATIONS); // 190 = almost twice the batch size (100)
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
