package de.tum.cit.aet.artemis.hyperion.service.variants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService;
import de.tum.cit.aet.artemis.hyperion.service.websocket.HyperionWebsocketService;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.DragItem;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository;
import de.tum.cit.aet.artemis.quiz.service.QuizExerciseService;

/**
 * The drag-and-drop image guard is the toolset's only defence for files that belong to the SOURCE exercise: a
 * variant clone shares their paths, so a replacement that repoints, drops, or invents one silently breaks the
 * source's images. These tests pin the guard against replacement JSON the model can plausibly produce.
 */
class QuizVariantToolsImageGuardTest {

    private static final long QUIZ_ID = 7L;

    private static final String PICTURE_PATH = "/api/files/drag-and-drop/drag-items/1/cargo.png";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private QuizExerciseService quizExerciseService;

    private QuizVariantTools tools;

    private DragAndDropQuestion question;

    @BeforeEach
    void setUp() {
        ExerciseVariantJobService jobService = new ExerciseVariantJobService(new LocalDataProviderService(), mock(HyperionWebsocketService.class));
        jobService.init();
        QuizExerciseRepository quizExerciseRepository = mock(QuizExerciseRepository.class);
        quizExerciseService = mock(QuizExerciseService.class);

        DragItem picturedItem = new DragItem();
        picturedItem.setId(1L);
        picturedItem.setPictureFilePath(PICTURE_PATH);
        DragItem textItem = new DragItem();
        textItem.setId(2L);
        textItem.setText("Cargo bay");

        question = new DragAndDropQuestion();
        question.setId(11L);
        question.setTitle("Sort the cargo");
        question.setPoints(1.0);
        question.setBackgroundFilePath("/api/files/drag-and-drop/backgrounds/11/bay.png");
        question.setDragItems(new ArrayList<>(List.of(picturedItem, textItem)));

        QuizExercise quiz = new QuizExercise();
        quiz.setId(QUIZ_ID);
        quiz.setQuizQuestions(new ArrayList<>(List.of(question)));
        when(quizExerciseRepository.findByIdWithQuestionsAndStatisticsElseThrow(QUIZ_ID)).thenReturn(quiz);

        tools = new QuizVariantTools(QUIZ_ID, "job-1", jobService, quizExerciseRepository, quizExerciseService, objectMapper);
    }

    /** Serializes the fixture question and lets the caller mutate the tree the way a model reply would. */
    private String questionJsonWith(Consumer<ObjectNode> mutation) throws Exception {
        ObjectNode node = objectMapper.valueToTree(question);
        node.put("type", "drag-and-drop");
        mutation.accept(node);
        return objectMapper.writeValueAsString(node);
    }

    /**
     * A null drag item must come back as a model-facing error rather than an escaping exception — in the batch
     * tool an exception would abort the remaining edits and discard the ones that already succeeded.
     */
    @Test
    void shouldRejectANullDragItemInsteadOfFailingTheWholeEdit() throws Exception {
        String json = questionJsonWith(node -> node.putArray("dragItems").addNull());

        String result = tools.updateQuestion(0, json);

        assertThat(result).startsWith("Error: ");
        verify(quizExerciseService, never()).save(any());
    }

    @Test
    void shouldRejectRemovingThePictureOfARetainedDragItem() throws Exception {
        String json = questionJsonWith(node -> {
            ArrayNode dragItems = (ArrayNode) node.get("dragItems");
            for (var item : dragItems) {
                if (item.has("pictureFilePath")) {
                    ((ObjectNode) item).remove("pictureFilePath");
                }
            }
        });

        String result = tools.updateQuestion(0, json);

        assertThat(result).startsWith("Error: ").contains("image path");
        verify(quizExerciseService, never()).save(any());
    }

    @Test
    void shouldRejectMovingAPictureToAnotherDragItem() throws Exception {
        String json = questionJsonWith(node -> {
            ArrayNode dragItems = (ArrayNode) node.get("dragItems");
            for (var item : dragItems) {
                ObjectNode dragItem = (ObjectNode) item;
                // Same set of paths, different owner — the subset rule alone would accept this.
                dragItem.put("pictureFilePath", dragItem.get("id").asLong() == 2L ? PICTURE_PATH : null);
            }
        });

        String result = tools.updateQuestion(0, json);

        assertThat(result).startsWith("Error: ").contains("image path");
        verify(quizExerciseService, never()).save(any());
    }
}
