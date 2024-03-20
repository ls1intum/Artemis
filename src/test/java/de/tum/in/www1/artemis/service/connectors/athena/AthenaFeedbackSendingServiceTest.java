package de.tum.in.www1.artemis.service.connectors.athena;

import static de.tum.in.www1.artemis.connector.AthenaRequestMockProvider.ATHENA_MODULE_PROGRAMMING_TEST;
import static de.tum.in.www1.artemis.connector.AthenaRequestMockProvider.ATHENA_MODULE_TEXT_TEST;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractAthenaTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.exercise.GradingCriterionUtil;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseUtilService;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseUtilService;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.TextBlockRepository;
import de.tum.in.www1.artemis.repository.TextExerciseRepository;

class AthenaFeedbackSendingServiceTest extends AbstractAthenaTest {

    @Autowired
    private AthenaModuleService athenaModuleService;

    @Mock
    private TextBlockRepository textBlockRepository;

    @Mock
    private TextExerciseRepository textExerciseRepository;

    @Mock
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    private AthenaFeedbackSendingService athenaFeedbackSendingService;

    private TextExercise textExercise;

    private TextSubmission textSubmission;

    private Feedback textFeedback;

    private TextBlock textBlock;

    private ProgrammingExercise programmingExercise;

    private ProgrammingSubmission programmingSubmission;

    private Feedback programmingFeedback;

    @BeforeEach
    void setUp() {
        athenaFeedbackSendingService = new AthenaFeedbackSendingService(athenaRequestMockProvider.getRestTemplate(), athenaModuleService,
                new AthenaDTOConverterService(textBlockRepository, textExerciseRepository, programmingExerciseRepository));

        athenaRequestMockProvider.enableMockingOfRequests();

        textExercise = textExerciseUtilService.createSampleTextExercise(null);
        textExercise.setFeedbackSuggestionModule(ATHENA_MODULE_TEXT_TEST);
        when(textExerciseRepository.findWithGradingCriteriaByIdElseThrow(textExercise.getId())).thenReturn(textExercise);

        textSubmission = new TextSubmission(2L).text("Test - This is what the feedback references - Submission");

        textBlock = new TextBlock().startIndex(7).endIndex(46).text("This is what the feedback references").submission(textSubmission);
        textBlock.computeId();
        when(textBlockRepository.findById(textBlock.getId())).thenReturn(Optional.of(textBlock));

        textFeedback = new Feedback().type(FeedbackType.MANUAL).credits(5.0).reference(textBlock.getId());
        textFeedback.setId(3L);
        var result = new Result();
        textFeedback.setResult(result);
        var participation = new StudentParticipation();
        participation.setExercise(textExercise);
        result.setParticipation(participation);

        programmingExercise = programmingExerciseUtilService.createSampleProgrammingExercise();
        programmingExercise.setFeedbackSuggestionModule(ATHENA_MODULE_PROGRAMMING_TEST);
        when(programmingExerciseRepository.findByIdWithGradingCriteriaElseThrow(programmingExercise.getId())).thenReturn(programmingExercise);

        programmingSubmission = new ProgrammingSubmission();
        programmingSubmission.setParticipation(new StudentParticipation());
        programmingSubmission.getParticipation().setExercise(programmingExercise);
        programmingSubmission.setId(2L);

        programmingFeedback = new Feedback().type(FeedbackType.MANUAL).credits(5.0).reference("test");
        programmingFeedback.setId(3L);
        programmingFeedback.setReference("file:src/Test.java_line:12");
        var programmingResult = new Result();
        programmingFeedback.setResult(programmingResult);
        programmingResult.setParticipation(programmingSubmission.getParticipation());
    }

    @Test
    void testFeedbackSendingText() {
        athenaRequestMockProvider.mockSendFeedbackAndExpect("text", jsonPath("$.exercise.id").value(textExercise.getId()),
                jsonPath("$.submission.id").value(textSubmission.getId()), jsonPath("$.submission.exerciseId").value(textExercise.getId()),
                jsonPath("$.feedbacks[0].id").value(textFeedback.getId()), jsonPath("$.feedbacks[0].exerciseId").value(textExercise.getId()),
                jsonPath("$.feedbacks[0].title").value(textFeedback.getText()), jsonPath("$.feedbacks[0].description").value(textFeedback.getDetailText()),
                jsonPath("$.feedbacks[0].credits").value(textFeedback.getCredits()), jsonPath("$.feedbacks[0].credits").value(textFeedback.getCredits()),
                jsonPath("$.feedbacks[0].indexStart").value(textBlock.getStartIndex()), jsonPath("$.feedbacks[0].indexEnd").value(textBlock.getEndIndex()));

        athenaFeedbackSendingService.sendFeedback(textExercise, textSubmission, List.of(textFeedback));
        athenaRequestMockProvider.verify();
    }

    private GradingCriterion createExampleGradingCriterion() {
        var gradingInstruction = new GradingInstruction();
        gradingInstruction.setId(101L);
        gradingInstruction.setCredits(1.0);
        gradingInstruction.setGradingScale("good");
        gradingInstruction.setInstructionDescription("Give this feedback if xyz");
        gradingInstruction.setFeedback("Well done!");
        gradingInstruction.setUsageCount(1);
        var gradingCriterion = new GradingCriterion();
        gradingCriterion.setId(1L);
        gradingCriterion.setTitle("Test");
        gradingCriterion.setExercise(textExercise);
        gradingCriterion.setStructuredGradingInstructions(Set.of(gradingInstruction));
        return gradingCriterion;
    }

    @Test
    void testFeedbackSendingTextWithGradingInstruction() {
        textExercise.setGradingCriteria(Set.of(createExampleGradingCriterion()));
        textExerciseRepository.save(textExercise);

        final GradingInstruction instruction = GradingCriterionUtil.findAnyInstructionWhere(textExercise.getGradingCriteria(),
                gradingInstruction -> "Give this feedback if xyz".equals(gradingInstruction.getInstructionDescription())).orElseThrow();
        textFeedback.setGradingInstruction(instruction);

        athenaRequestMockProvider.mockSendFeedbackAndExpect("text", jsonPath("$.exercise.id").value(textExercise.getId()), jsonPath("$.exercise.gradingCriteria[0].id").value(1),
                jsonPath("$.exercise.gradingCriteria[0].title").value("Test"), jsonPath("$.exercise.gradingCriteria[0].structuredGradingInstructions[0].id").value(101),
                jsonPath("$.exercise.gradingCriteria[0].structuredGradingInstructions[0].credits").value(1.0),
                jsonPath("$.exercise.gradingCriteria[0].structuredGradingInstructions[0].gradingScale").value("good"),
                jsonPath("$.exercise.gradingCriteria[0].structuredGradingInstructions[0].instructionDescription").value("Give this feedback if xyz"),
                jsonPath("$.exercise.gradingCriteria[0].structuredGradingInstructions[0].feedback").value("Well done!"),
                jsonPath("$.exercise.gradingCriteria[0].structuredGradingInstructions[0].usageCount").value(1), jsonPath("$.submission.id").value(textSubmission.getId()),
                jsonPath("$.submission.exerciseId").value(textExercise.getId()), jsonPath("$.feedbacks[0].id").value(textFeedback.getId()),
                jsonPath("$.feedbacks[0].exerciseId").value(textExercise.getId()), jsonPath("$.feedbacks[0].title").value(textFeedback.getText()),
                jsonPath("$.feedbacks[0].description").value(textFeedback.getDetailText()), jsonPath("$.feedbacks[0].credits").value(textFeedback.getCredits()),
                jsonPath("$.feedbacks[0].credits").value(textFeedback.getCredits()), jsonPath("$.feedbacks[0].indexStart").value(textBlock.getStartIndex()),
                jsonPath("$.feedbacks[0].indexEnd").value(textBlock.getEndIndex()), jsonPath("$.feedbacks[0].structuredGradingInstructionId").value(101));

        athenaFeedbackSendingService.sendFeedback(textExercise, textSubmission, List.of(textFeedback));
        athenaRequestMockProvider.verify();
    }

    @Test
    void testFeedbackSendingProgramming() {
        athenaRequestMockProvider.mockSendFeedbackAndExpect("programming", jsonPath("$.exercise.id").value(programmingExercise.getId()),
                jsonPath("$.submission.id").value(programmingSubmission.getId()), jsonPath("$.submission.exerciseId").value(programmingExercise.getId()),
                jsonPath("$.feedbacks[0].id").value(programmingFeedback.getId()), jsonPath("$.feedbacks[0].exerciseId").value(programmingExercise.getId()),
                jsonPath("$.feedbacks[0].title").value(programmingFeedback.getText()), jsonPath("$.feedbacks[0].description").value(programmingFeedback.getDetailText()),
                jsonPath("$.feedbacks[0].credits").value(programmingFeedback.getCredits()), jsonPath("$.feedbacks[0].credits").value(programmingFeedback.getCredits()),
                jsonPath("$.feedbacks[0].lineStart").value(12), jsonPath("$.feedbacks[0].lineEnd").value(12));

        athenaFeedbackSendingService.sendFeedback(programmingExercise, programmingSubmission, List.of(programmingFeedback));
        athenaRequestMockProvider.verify();
    }

    @Test
    void testFeedbackSendingUnsupportedExerciseType() {
        athenaRequestMockProvider.mockSendFeedbackAndExpect("modeling");
        assertThatThrownBy(() -> athenaFeedbackSendingService.sendFeedback(new ModelingExercise(), new ModelingSubmission(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testEmptyFeedbackNotSending() {
        athenaFeedbackSendingService.sendFeedback(textExercise, textSubmission, List.of());
        athenaFeedbackSendingService.sendFeedback(programmingExercise, programmingSubmission, List.of());
        athenaRequestMockProvider.verify(); // Ensure that there was no request
    }

    @Test
    void testSendFeedbackWithFeedbackSuggestionsDisabled() {
        textExercise.setFeedbackSuggestionModule(null);
        assertThatThrownBy(() -> athenaFeedbackSendingService.sendFeedback(textExercise, textSubmission, List.of(textFeedback))).isInstanceOf(IllegalArgumentException.class);
        programmingExercise.setFeedbackSuggestionModule(null);
        assertThatThrownBy(() -> athenaFeedbackSendingService.sendFeedback(programmingExercise, programmingSubmission, List.of(programmingFeedback)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
