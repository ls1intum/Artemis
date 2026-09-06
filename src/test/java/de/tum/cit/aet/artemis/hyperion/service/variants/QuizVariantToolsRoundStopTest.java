package de.tum.cit.aet.artemis.hyperion.service.variants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.hyperion.dto.VariantGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.service.websocket.HyperionWebsocketService;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository;
import de.tum.cit.aet.artemis.quiz.service.QuizExerciseService;

/**
 * The tool-call budget's "call finish now" directive is advisory: Spring AI's internal tool loop only ends when
 * the model stops calling tools or a called tool returns directly, so a model that keeps calling ordinary tools
 * would keep the round — and its model exchanges — going indefinitely. These tests pin the hard stop that ends
 * such a round: past the budget's grace calls (and on the first call after a cancellation) every tool reports
 * {@code returnDirect}, which terminates the loop whatever the model does next. They read the metadata BEFORE
 * each call, the way Spring AI's tool-calling manager does — it decides {@code returnDirect} for a call before
 * invoking it, so a condition that only became true inside that call would end the round one turn too late.
 */
class QuizVariantToolsRoundStopTest {

    private static final long QUIZ_ID = 7L;

    /** {@code QuizVariantTools.TOOL_CALL_BUDGET} plus {@code VariantRoundBudget.GRACE_CALLS}. */
    private static final int LAST_CONTINUING_CALL = 25 + 10;

    private ExerciseVariantJobService jobService;

    private VariantJob job;

    private QuizVariantTools tools;

    @BeforeEach
    void setUp() {
        jobService = new ExerciseVariantJobService(new LocalDataProviderService(), mock(HyperionWebsocketService.class));
        jobService.init();
        Exercise sourceExercise = mock(Exercise.class);
        when(sourceExercise.getId()).thenReturn(QUIZ_ID);
        when(sourceExercise.getTitle()).thenReturn("Source quiz");
        when(sourceExercise.getExerciseType()).thenReturn(ExerciseType.QUIZ);
        User user = mock(User.class);
        when(user.getLogin()).thenReturn("instructor1");
        job = jobService.startJob(user, sourceExercise, mock(VariantGenerationRequestDTO.class));

        QuizExerciseRepository quizExerciseRepository = mock(QuizExerciseRepository.class);
        QuizExercise quiz = new QuizExercise();
        quiz.setId(QUIZ_ID);
        quiz.setQuizQuestions(new ArrayList<>(List.of()));
        when(quizExerciseRepository.findByIdWithQuestionsElseThrow(QUIZ_ID)).thenReturn(quiz);
        tools = new QuizVariantTools(QUIZ_ID, job.getJobId(), jobService, quizExerciseRepository, mock(QuizExerciseService.class), new ObjectMapper());
    }

    @Test
    void endsTheRoundOnceTheToolCallBudgetAndItsGraceCallsAreUsedUp() {
        ToolCallback validateQuiz = toolCallback("validateQuiz");

        for (int call = 1; call <= LAST_CONTINUING_CALL; call++) {
            assertThat(validateQuiz.getToolMetadata().returnDirect()).as("the round must keep running through call %d", call).isFalse();
            validateQuiz.call("{}");
        }

        assertThat(validateQuiz.getToolMetadata().returnDirect()).isTrue();
        assertThat(validateQuiz.call("{}")).contains("TOOL BUDGET EXHAUSTED");
    }

    @Test
    void endsTheRoundOnTheFirstToolCallAfterACancellation() {
        ToolCallback validateQuiz = toolCallback("validateQuiz");
        jobService.requestCancel(job.getJobId(), "instructor1");

        assertThat(validateQuiz.getToolMetadata().returnDirect()).isTrue();
        assertThat(validateQuiz.call("{}")).contains("CANCELLED");
    }

    @Test
    void keepsFinishReturningDirectlyBeforeAnyStopCondition() {
        assertThat(toolCallback("finish").getToolMetadata().returnDirect()).isTrue();
        assertThat(toolCallback("validateQuiz").getToolMetadata().returnDirect()).isFalse();
    }

    private ToolCallback toolCallback(String name) {
        return tools.toolCallbacks().stream().filter(callback -> callback.getToolDefinition().name().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("No tool named " + name));
    }
}
