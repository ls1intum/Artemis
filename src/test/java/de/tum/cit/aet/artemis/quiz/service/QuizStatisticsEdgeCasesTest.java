package de.tum.cit.aet.artemis.quiz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService;
import de.tum.cit.aet.artemis.quiz.domain.AnswerOption;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropMapping;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropMappingSelection;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropSubmittedAnswerSelection;
import de.tum.cit.aet.artemis.quiz.domain.DragItem;
import de.tum.cit.aet.artemis.quiz.domain.DropLocation;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceSubmittedAnswerSelection;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSpot;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSubmittedAnswerSelection;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerTextSelection;
import de.tum.cit.aet.artemis.quiz.domain.SubmittedAnswerSelection;
import de.tum.cit.aet.artemis.quiz.repository.QuizStatisticProjections.RatedSelection;
import de.tum.cit.aet.artemis.quiz.repository.QuizStatisticsRepository;

/** Verifies that malformed or stale selections cannot inflate component statistics. */
class QuizStatisticsEdgeCasesTest {

    private final QuizStatisticsRepository repository = mock(QuizStatisticsRepository.class);

    private final JsonMapper mapper = JsonMapper.builder().build();

    private QuizStatisticsService service;

    @BeforeEach
    void setUp() {
        service = new QuizStatisticsService(repository, mock(WebsocketMessagingService.class), mock(TaskScheduler.class), new LocalDataProviderService());
    }

    @Test
    void countsMultipleChoiceOptionsOncePerSelectionAndIgnoresRemovedOptionsAndWrongTypes() {
        var question = new MultipleChoiceQuestion();
        var first = new AnswerOption();
        first.setId(10L);
        var second = new AnswerOption();
        second.setId(20L);
        question.setAnswerOptions(List.of(first, second));
        var rated = new MultipleChoiceSubmittedAnswerSelection();
        rated.setSelectedOptionIds(List.of(10L, 10L, 99L));
        var unrated = new MultipleChoiceSubmittedAnswerSelection();
        unrated.setSelectedOptionIds(List.of(10L, 20L));
        when(repository.findSelectionsForQuestion(7L)).thenReturn(
                List.of(selection(true, 2.0, rated), selection(false, 1.0, unrated), selection(true, 0.0, new ShortAnswerSubmittedAnswerSelection()), selection(false, 2.0, null)));

        var statistic = service.getQuestionStatistic(quiz(question), 7L).quizQuestionStatistic();

        assertThat(statistic.participantsRated()).isEqualTo(2);
        assertThat(statistic.participantsUnrated()).isEqualTo(2);
        assertThat(statistic.ratedCorrectCounter()).isOne();
        assertThat(statistic.unRatedCorrectCounter()).isOne();
        assertThat(counters(mapper.valueToTree(statistic), "answerCounters", "answerId")).containsExactlyInAnyOrderEntriesOf(Map.of(10L, List.of(1, 1), 20L, List.of(0, 1)));
    }

    @Test
    void countsOnlyCorrectDragItemsAndTreatsUnmappedEmptyLocationsAsCorrect() {
        var question = new DragAndDropQuestion();
        var mapped = new DropLocation();
        mapped.setId(10L);
        var empty = new DropLocation();
        empty.setId(20L);
        var item = new DragItem();
        item.setId(30L);
        question.setDropLocations(List.of(mapped, empty));
        question.setDragItems(List.of(item));
        var correct = new DragAndDropMapping();
        correct.setDropLocation(mapped);
        correct.setDragItem(item);
        question.setCorrectMappings(Set.of(correct));
        var rated = new DragAndDropSubmittedAnswerSelection();
        rated.setMappings(List.of(new DragAndDropMappingSelection(30L, 10L), new DragAndDropMappingSelection(99L, 10L)));
        var wrong = new DragAndDropSubmittedAnswerSelection();
        wrong.setMappings(List.of(new DragAndDropMappingSelection(99L, 10L), new DragAndDropMappingSelection(30L, 20L)));
        var blank = new DragAndDropSubmittedAnswerSelection();
        when(repository.findSelectionsForQuestion(7L)).thenReturn(List.of(selection(true, 2.0, rated), selection(false, 0.0, wrong), selection(false, null, blank),
                selection(true, 0.0, new MultipleChoiceSubmittedAnswerSelection())));

        var statistic = service.getQuestionStatistic(quiz(question), 7L).quizQuestionStatistic();

        assertThat(statistic.type()).isEqualTo("drag-and-drop");
        assertThat(statistic.participantsRated()).isEqualTo(2);
        assertThat(statistic.participantsUnrated()).isEqualTo(2);
        assertThat(statistic.ratedCorrectCounter()).isOne();
        assertThat(statistic.unRatedCorrectCounter()).isZero();
        assertThat(counters(mapper.valueToTree(statistic), "dropLocationCounters", "dropLocationId"))
                .containsExactlyInAnyOrderEntriesOf(Map.of(10L, List.of(1, 0), 20L, List.of(1, 1)));
    }

    @Test
    void shortAnswersIgnoreDuplicateSpotsRemovedSpotsAndFalseOrUnknownCorrectness() {
        var question = new ShortAnswerQuestion();
        var first = new ShortAnswerSpot();
        first.setId(10L);
        var second = new ShortAnswerSpot();
        second.setId(20L);
        question.setSpots(List.of(first, second));
        var rated = new ShortAnswerSubmittedAnswerSelection();
        rated.setSubmittedTexts(List.of(new ShortAnswerTextSelection(10L, "first", true), new ShortAnswerTextSelection(10L, "duplicate", true),
                new ShortAnswerTextSelection(99L, "removed", true), new ShortAnswerTextSelection(20L, "wrong", false)));
        var unrated = new ShortAnswerSubmittedAnswerSelection();
        unrated.setSubmittedTexts(List.of(new ShortAnswerTextSelection(10L, "ungraded", null), new ShortAnswerTextSelection(20L, "correct", true)));
        when(repository.findSelectionsForQuestion(7L))
                .thenReturn(List.of(selection(true, 1.0, rated), selection(false, 2.0, unrated), selection(false, null, new DragAndDropSubmittedAnswerSelection())));

        var statistic = service.getQuestionStatistic(quiz(question), 7L).quizQuestionStatistic();

        assertThat(statistic.type()).isEqualTo("short-answer");
        assertThat(statistic.participantsRated()).isOne();
        assertThat(statistic.participantsUnrated()).isEqualTo(2);
        assertThat(statistic.ratedCorrectCounter()).isZero();
        assertThat(statistic.unRatedCorrectCounter()).isOne();
        assertThat(counters(mapper.valueToTree(statistic), "shortAnswerSpotCounters", "spotId")).containsExactlyInAnyOrderEntriesOf(Map.of(10L, List.of(1, 0), 20L, List.of(0, 1)));
    }

    @Test
    void rejectsAQuestionFromAnotherQuizAndToleratesUnpersistedQuestionsWhileSearching() {
        var quiz = quiz(new MultipleChoiceQuestion());
        quiz.addQuestion(new MultipleChoiceQuestion());

        assertThatThrownBy(() -> service.getQuestionStatistic(quiz, 88L)).isInstanceOf(EntityNotFoundException.class).hasMessageContaining("88").hasMessageContaining("42");
    }

    private static QuizExercise quiz(QuizQuestion question) {
        var quiz = new QuizExercise();
        quiz.setId(42L);
        question.setId(7L);
        question.setPoints(2);
        quiz.addQuestion(question);
        return quiz;
    }

    private static RatedSelection selection(boolean rated, Double points, SubmittedAnswerSelection answer) {
        return new Selection(rated, points, answer);
    }

    private record Selection(boolean rated, Double points, SubmittedAnswerSelection answer) implements RatedSelection {

        @Override
        public boolean getRated() {
            return rated;
        }

        @Override
        public Double getScoreInPoints() {
            return points;
        }

        @Override
        public SubmittedAnswerSelection getSelection() {
            return answer;
        }
    }

    private static Map<Long, List<Integer>> counters(JsonNode statistic, String property, String idProperty) {
        var counters = new HashMap<Long, List<Integer>>();
        for (var counter : statistic.get(property)) {
            assertThat(counter.has("ratedCounter")).isTrue();
            assertThat(counter.has("unRatedCounter")).isTrue();
            assertThat(counters.put(counter.get(idProperty).longValue(), List.of(counter.get("ratedCounter").intValue(), counter.get("unRatedCounter").intValue())))
                    .as("one statistics row per component").isNull();
        }
        return counters;
    }
}
