package de.tum.cit.aet.artemis.athena.service.connectors;

import static de.tum.cit.aet.artemis.core.connector.AthenaRequestMockProvider.ATHENA_MODULE_PROGRAMMING_TEST;
import static de.tum.cit.aet.artemis.core.connector.AthenaRequestMockProvider.ATHENA_MODULE_TEXT_TEST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Autowired;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.domain.GradingInstruction;
import de.tum.cit.aet.artemis.assessment.repository.FeedbackRepository;
import de.tum.cit.aet.artemis.assessment.repository.GradingCriterionRepository;
import de.tum.cit.aet.artemis.assessment.repository.TextBlockRepository;
import de.tum.cit.aet.artemis.assessment.util.GradingCriterionUtil;
import de.tum.cit.aet.artemis.athena.AbstractAthenaTest;
import de.tum.cit.aet.artemis.athena.service.AthenaFeedbackSendingService;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingSubmissionTestRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.text.domain.TextBlock;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.repository.TextExerciseRepository;
import de.tum.cit.aet.artemis.text.test_repository.TextSubmissionTestRepository;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class AthenaFeedbackSendingServiceTest extends AbstractAthenaTest {

    private static final String TEST_PREFIX = "athenafeedbacksending";

    @Autowired
    private TextBlockRepository textBlockRepository;

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @Autowired
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Autowired
    private GradingCriterionRepository gradingCriterionRepository;

    @Autowired
    private TextSubmissionTestRepository textSubmissionRepository;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private ProgrammingSubmissionTestRepository programmingSubmissionRepository;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private AthenaFeedbackSendingService athenaFeedbackSendingService;

    private TextExercise textExercise;

    private TextSubmission textSubmission;

    private Feedback textFeedback;

    private TextBlock textBlock;

    private ProgrammingExercise programmingExercise;

    private ProgrammingSubmission programmingSubmission;

    private Feedback programmingFeedback;

    private ListAppender<ILoggingEvent> asyncExceptionLogAppender;

    private Logger asyncExceptionLog;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 2, 0, 0, 0);

        textExercise = textExerciseUtilService.createSampleTextExercise(null);
        textExercise.setFeedbackSuggestionModule(ATHENA_MODULE_TEXT_TEST);
        textExercise = textExerciseRepository.save(textExercise);

        var textParticipation = participationUtilService.createAndSaveParticipationForExercise(textExercise, TEST_PREFIX + "student1");

        textSubmission = new TextSubmission().text("Test - This is what the feedback references - Submission");
        textSubmission.setParticipation(textParticipation);
        textSubmission = textSubmissionRepository.save(textSubmission);

        textBlock = new TextBlock().startIndex(7).endIndex(46).text("This is what the feedback references").submission(textSubmission);
        textBlock.computeId();
        textBlock = textBlockRepository.save(textBlock);

        var textResult = participationUtilService.addResultToSubmission(textParticipation, textSubmission);

        textFeedback = new Feedback().type(FeedbackType.MANUAL).credits(5.0).reference(textBlock.getId());
        textFeedback.setText("Title");
        textFeedback.setDetailText("Description");
        textFeedback.setResult(textResult);
        textFeedback = feedbackRepository.save(textFeedback);

        programmingExercise = programmingExerciseUtilService.createSampleProgrammingExercise();
        programmingExercise.setFeedbackSuggestionModule(ATHENA_MODULE_PROGRAMMING_TEST);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);

        var programmingParticipation = participationUtilService.createAndSaveParticipationForExercise(programmingExercise, TEST_PREFIX + "student2");

        programmingSubmission = new ProgrammingSubmission();
        programmingSubmission.setParticipation(programmingParticipation);
        programmingSubmission = programmingSubmissionRepository.save(programmingSubmission);

        var programmingResult = participationUtilService.addResultToSubmission(programmingParticipation, programmingSubmission);

        programmingFeedback = new Feedback().type(FeedbackType.MANUAL).credits(5.0).reference("test");
        programmingFeedback.setText("Title");
        programmingFeedback.setDetailText("Description");
        programmingFeedback.setReference("file:src/Test.java_line:12");
        programmingFeedback.setResult(programmingResult);
        programmingFeedback = feedbackRepository.save(programmingFeedback);

        asyncExceptionLogAppender = new ListAppender<>();
        asyncExceptionLogAppender.start();
        asyncExceptionLog = (Logger) LoggerFactory.getLogger(SimpleAsyncUncaughtExceptionHandler.class);
        asyncExceptionLog.addAppender(asyncExceptionLogAppender);
    }

    @AfterEach
    void tearDown() {
        asyncExceptionLog.detachAppender(asyncExceptionLogAppender);
    }

    @Test
    void testFeedbackSendingText() {
        athenaRequestMockProvider.mockSendFeedbackAndExpect("text", jsonPath("$.exercise.id").value(textExercise.getId()),
                jsonPath("$.submission.id").value(textSubmission.getId()), jsonPath("$.submission.exerciseId").value(textExercise.getId()),
                jsonPath("$.feedbacks[0].id").value(textFeedback.getId()), jsonPath("$.feedbacks[0].exerciseId").value(textExercise.getId()),
                jsonPath("$.feedbacks[0].title").value(textFeedback.getText()), jsonPath("$.feedbacks[0].description").value(textFeedback.getDetailText()),
                jsonPath("$.feedbacks[0].credits").value(textFeedback.getCredits()), jsonPath("$.feedbacks[0].indexStart").value(textBlock.getStartIndex()),
                jsonPath("$.feedbacks[0].indexEnd").value(textBlock.getEndIndex()));

        athenaFeedbackSendingService.sendFeedback(textExercise, textSubmission, List.of(textFeedback));
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> athenaRequestMockProvider.verify());
    }

    private GradingCriterion createExampleGradingCriterion() {
        var gradingInstruction = new GradingInstruction();
        gradingInstruction.setCredits(1.0);
        gradingInstruction.setGradingScale("good");
        gradingInstruction.setInstructionDescription("Give this feedback if xyz");
        gradingInstruction.setFeedback("Well done!");
        gradingInstruction.setUsageCount(1);

        var gradingCriterion = new GradingCriterion();
        gradingCriterion.setTitle("Test");
        gradingCriterion.setExercise(textExercise);

        gradingCriterion.setStructuredGradingInstructions(Set.of(gradingInstruction));
        return gradingCriterionRepository.save(gradingCriterion);
    }

    @Test
    void testFeedbackSendingTextWithGradingInstruction() {
        GradingCriterion gradingCriterion = createExampleGradingCriterion();
        textExercise.setGradingCriteria(Set.of(gradingCriterion));
        textExercise = textExerciseRepository.save(textExercise);

        final GradingInstruction instruction = GradingCriterionUtil.findAnyInstructionWhere(textExercise.getGradingCriteria(),
                gradingInstruction -> "Give this feedback if xyz".equals(gradingInstruction.getInstructionDescription())).orElseThrow();
        textFeedback.setGradingInstruction(instruction);

        athenaRequestMockProvider.mockSendFeedbackAndExpect("text", jsonPath("$.exercise.id").value(textExercise.getId()),
                jsonPath("$.exercise.gradingCriteria[0].id").value(gradingCriterion.getId()), jsonPath("$.exercise.gradingCriteria[0].title").value("Test"),
                jsonPath("$.exercise.gradingCriteria[0].structuredGradingInstructions[0].id").value(instruction.getId()),
                jsonPath("$.exercise.gradingCriteria[0].structuredGradingInstructions[0].credits").value(1.0),
                jsonPath("$.exercise.gradingCriteria[0].structuredGradingInstructions[0].gradingScale").value("good"),
                jsonPath("$.exercise.gradingCriteria[0].structuredGradingInstructions[0].instructionDescription").value("Give this feedback if xyz"),
                jsonPath("$.exercise.gradingCriteria[0].structuredGradingInstructions[0].feedback").value("Well done!"),
                jsonPath("$.exercise.gradingCriteria[0].structuredGradingInstructions[0].usageCount").value(1), jsonPath("$.submission.id").value(textSubmission.getId()),
                jsonPath("$.submission.exerciseId").value(textExercise.getId()), jsonPath("$.feedbacks[0].id").value(textFeedback.getId()),
                jsonPath("$.feedbacks[0].exerciseId").value(textExercise.getId()), jsonPath("$.feedbacks[0].title").value(textFeedback.getText()),
                jsonPath("$.feedbacks[0].description").value(textFeedback.getDetailText()), jsonPath("$.feedbacks[0].credits").value(textFeedback.getCredits()),
                jsonPath("$.feedbacks[0].credits").value(textFeedback.getCredits()), jsonPath("$.feedbacks[0].indexStart").value(textBlock.getStartIndex()),
                jsonPath("$.feedbacks[0].indexEnd").value(textBlock.getEndIndex()), jsonPath("$.feedbacks[0].structuredGradingInstructionId").value(instruction.getId()));

        athenaFeedbackSendingService.sendFeedback(textExercise, textSubmission, List.of(textFeedback));
        await().untilAsserted(() -> athenaRequestMockProvider.verify());
    }

    @Test
    void testFeedbackSendingProgramming() {
        athenaRequestMockProvider.mockSendFeedbackAndExpect("programming", jsonPath("$.exercise.id").value(programmingExercise.getId()),
                jsonPath("$.submission.id").value(programmingSubmission.getId()), jsonPath("$.submission.exerciseId").value(programmingExercise.getId()),
                jsonPath("$.feedbacks[0].id").value(programmingFeedback.getId()), jsonPath("$.feedbacks[0].exerciseId").value(programmingExercise.getId()),
                jsonPath("$.feedbacks[0].title").value(programmingFeedback.getText()), jsonPath("$.feedbacks[0].description").value(programmingFeedback.getDetailText()),
                jsonPath("$.feedbacks[0].credits").value(programmingFeedback.getCredits()), jsonPath("$.feedbacks[0].lineStart").value(12),
                jsonPath("$.feedbacks[0].lineEnd").value(12));

        athenaFeedbackSendingService.sendFeedback(programmingExercise, programmingSubmission, List.of(programmingFeedback));
        await().untilAsserted(() -> athenaRequestMockProvider.verify());
    }

    @Test
    void testEmptyFeedbackNotSending() {
        athenaFeedbackSendingService.sendFeedback(textExercise, textSubmission, List.of());
        athenaFeedbackSendingService.sendFeedback(programmingExercise, programmingSubmission, List.of());
        await().during(Duration.ofSeconds(2)).untilAsserted(() -> athenaRequestMockProvider.verify()); // Ensure that there was no request
    }

    @Test
    void testSendFeedbackWithFeedbackSuggestionsDisabled() {
        textExercise.setFeedbackSuggestionModule(null);
        textExercise = textExerciseRepository.save(textExercise);
        athenaFeedbackSendingService.sendFeedback(textExercise, textSubmission, List.of(textFeedback));
        await().untilAsserted(
                () -> assertThat(asyncExceptionLogAppender.list).extracting(AthenaFeedbackSendingServiceTest::getExceptionName).contains(IllegalArgumentException.class.getName()));

        asyncExceptionLogAppender.list.clear();

        programmingExercise.setFeedbackSuggestionModule(null);
        programmingExerciseRepository.save(programmingExercise);
        athenaFeedbackSendingService.sendFeedback(programmingExercise, programmingSubmission, List.of(programmingFeedback));
        await().untilAsserted(
                () -> assertThat(asyncExceptionLogAppender.list).extracting(AthenaFeedbackSendingServiceTest::getExceptionName).contains(IllegalArgumentException.class.getName()));
    }

    @Nullable
    private static String getExceptionName(ILoggingEvent event) {
        IThrowableProxy throwableProxy = event.getThrowableProxy();
        if (throwableProxy == null) {
            return null;
        }
        return throwableProxy.getClassName();
    }
}
