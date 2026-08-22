package de.tum.cit.aet.artemis.quiz.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.quiz.domain.AnswerOption;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropMapping;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropMappingSelection;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropSubmittedAnswerSelection;
import de.tum.cit.aet.artemis.quiz.domain.DropLocation;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceSubmittedAnswerSelection;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSpot;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSubmittedAnswerSelection;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerTextSelection;
import de.tum.cit.aet.artemis.quiz.dto.QuizPointStatisticDTO;
import de.tum.cit.aet.artemis.quiz.dto.QuizPointStatisticsDTO;
import de.tum.cit.aet.artemis.quiz.dto.QuizQuestionStatisticDTO;
import de.tum.cit.aet.artemis.quiz.dto.QuizQuestionStatisticResponseDTO;
import de.tum.cit.aet.artemis.quiz.dto.QuizStatisticProjections.QuestionAggregate;
import de.tum.cit.aet.artemis.quiz.dto.QuizStatisticsOverviewDTO;
import de.tum.cit.aet.artemis.quiz.repository.QuizStatisticsRepository;

/**
 * Calculates quiz statistics on demand from results and submitted-answer selections.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class QuizStatisticsService {

    private static final Duration NOTIFICATION_DEBOUNCE = Duration.ofSeconds(1);

    private final QuizStatisticsRepository quizStatisticsRepository;

    private final WebsocketMessagingService websocketMessagingService;

    private final TaskScheduler taskScheduler;

    private final Set<Long> pendingNotifications = ConcurrentHashMap.newKeySet();

    public QuizStatisticsService(QuizStatisticsRepository quizStatisticsRepository, WebsocketMessagingService websocketMessagingService,
            @Qualifier("taskScheduler") TaskScheduler taskScheduler) {
        this.quizStatisticsRepository = quizStatisticsRepository;
        this.websocketMessagingService = websocketMessagingService;
        this.taskScheduler = taskScheduler;
    }

    /**
     * Calculates the participant and fully-correct counters for every question.
     *
     * @param quizExercise the loaded quiz exercise
     * @return the quiz exercise with overview statistics
     */
    public QuizStatisticsOverviewDTO getOverview(QuizExercise quizExercise) {
        long quizExerciseId = quizExercise.getId();
        Map<Long, long[]> countersByQuestion = new HashMap<>();
        for (QuizQuestion question : quizExercise.getQuizQuestions()) {
            countersByQuestion.put(question.getId(), new long[4]);
        }
        quizStatisticsRepository.findQuestionAggregatesForQuiz(quizExerciseId).forEach(aggregate -> {
            long[] counters = countersByQuestion.get(aggregate.getQuestionId());
            if (counters == null) {
                return;
            }
            int offset = Boolean.TRUE.equals(aggregate.getRated()) ? 0 : 1;
            counters[offset] = aggregate.getParticipantCount();
            counters[2 + offset] = aggregate.getCorrectCount();
        });

        Map<Long, QuizQuestionStatisticDTO> statisticsByQuestion = new HashMap<>();
        for (QuizQuestion question : quizExercise.getQuizQuestions()) {
            statisticsByQuestion.put(question.getId(), QuizQuestionStatisticDTO.of(question, countersByQuestion.get(question.getId()), null));
        }
        return QuizStatisticsOverviewDTO.of(quizExercise, statisticsByQuestion);
    }

    /**
     * Calculates the quiz's integer point-bucket histogram.
     *
     * @param quizExercise the loaded quiz exercise
     * @return the quiz exercise with point statistics
     */
    public QuizPointStatisticsDTO getPointStatistic(QuizExercise quizExercise) {
        long quizExerciseId = quizExercise.getId();
        double overallPoints = quizExercise.getOverallQuizPoints();
        Map<Double, long[]> countersByPoints = new HashMap<>();
        for (double points = 0.0; points <= overallPoints; points++) {
            countersByPoints.put(points, new long[2]);
        }

        long ratedResultCount = 0;
        long unratedResultCount = 0;
        for (var bucket : quizStatisticsRepository.findPointStatistic(quizExerciseId)) {
            double points = Math.round(overallPoints * bucket.getScore() / 100);
            long[] counters = countersByPoints.computeIfAbsent(points, ignored -> new long[2]);
            int index = Boolean.TRUE.equals(bucket.getRated()) ? 0 : 1;
            counters[index] += bucket.getParticipantCount();
            if (index == 0) {
                ratedResultCount += bucket.getParticipantCount();
            }
            else {
                unratedResultCount += bucket.getParticipantCount();
            }
        }

        QuizPointStatisticDTO pointStatistic = QuizPointStatisticDTO.of(countersByPoints, ratedResultCount, unratedResultCount);
        return QuizPointStatisticsDTO.of(quizExercise, pointStatistic);
    }

    /**
     * Calculates all counters for one question.
     *
     * @param quizExercise the loaded quiz exercise
     * @param questionId   the id of the question
     * @return the quiz exercise with the requested question statistic
     */
    public QuizQuestionStatisticResponseDTO getQuestionStatistic(QuizExercise quizExercise, long questionId) {
        long quizExerciseId = quizExercise.getId();
        QuizQuestion question = quizExercise.getQuizQuestions().stream().filter(candidate -> candidate.getId() != null && candidate.getId() == questionId).findFirst()
                .orElseThrow(() -> new EntityNotFoundException("QuizQuestion with id " + questionId + " not found in quiz exercise " + quizExerciseId));
        long[] counters = questionAggregate(questionId, question.getPoints());
        Map<Long, long[]> componentCounters = switch (question) {
            case MultipleChoiceQuestion multipleChoiceQuestion -> multipleChoiceCounters(multipleChoiceQuestion);
            case DragAndDropQuestion dragAndDropQuestion -> dragAndDropCounters(dragAndDropQuestion);
            case ShortAnswerQuestion shortAnswerQuestion -> shortAnswerCounters(shortAnswerQuestion);
            default -> throw new IllegalArgumentException("Unsupported quiz question type " + question.getClass().getName());
        };
        QuizQuestionStatisticDTO statistic = QuizQuestionStatisticDTO.of(question, counters, componentCounters);
        return QuizQuestionStatisticResponseDTO.of(quizExercise, questionId, statistic);
    }

    /**
     * Notifies open statistics pages that their on-demand data changed. The payload is intentionally only the exercise id;
     * each page reloads its own endpoint.
     *
     * @param quizExerciseId the id of the changed quiz exercise
     */
    public void notifyStatisticsChanged(long quizExerciseId) {
        if (!pendingNotifications.add(quizExerciseId)) {
            return;
        }
        taskScheduler.schedule(() -> {
            pendingNotifications.remove(quizExerciseId);
            websocketMessagingService.sendMessage("/topic/statistic/" + quizExerciseId, quizExerciseId);
        }, Instant.now().plus(NOTIFICATION_DEBOUNCE));
    }

    private long[] questionAggregate(long questionId, double questionPoints) {
        long[] counters = new long[4];
        for (QuestionAggregate aggregate : quizStatisticsRepository.findQuestionAggregate(questionId, questionPoints)) {
            int offset = Boolean.TRUE.equals(aggregate.getRated()) ? 0 : 1;
            counters[offset] = aggregate.getParticipantCount();
            counters[2 + offset] = aggregate.getCorrectCount();
        }
        return counters;
    }

    private Map<Long, long[]> multipleChoiceCounters(MultipleChoiceQuestion question) {
        Map<Long, long[]> countersByAnswer = new HashMap<>();
        for (AnswerOption answerOption : question.getAnswerOptions()) {
            countersByAnswer.put(answerOption.getId(), new long[2]);
        }
        quizStatisticsRepository.findSelectionsForQuestion(question.getId()).forEach(row -> {
            if (!(row.getSelection() instanceof MultipleChoiceSubmittedAnswerSelection selection)) {
                return;
            }
            int index = Boolean.TRUE.equals(row.getRated()) ? 0 : 1;
            Set<Long> countedAnswerOptions = new HashSet<>();
            for (Long answerOptionId : selection.getSelectedOptionIds()) {
                if (!countedAnswerOptions.add(answerOptionId)) {
                    continue;
                }
                long[] counters = countersByAnswer.get(answerOptionId);
                if (counters != null) {
                    counters[index]++;
                }
            }
        });
        return countersByAnswer;
    }

    private Map<Long, long[]> dragAndDropCounters(DragAndDropQuestion question) {
        Map<Long, Set<Long>> correctDragItemsByDropLocation = new HashMap<>();
        Map<Long, long[]> countersByDropLocation = new HashMap<>();
        for (DropLocation dropLocation : question.getDropLocations()) {
            correctDragItemsByDropLocation.put(dropLocation.getId(), new HashSet<>());
            countersByDropLocation.put(dropLocation.getId(), new long[2]);
        }
        for (DragAndDropMapping mapping : question.getCorrectMappings()) {
            Set<Long> correctDragItems = correctDragItemsByDropLocation.get(mapping.getDropLocation().getId());
            if (correctDragItems != null) {
                correctDragItems.add(mapping.getDragItem().getId());
            }
        }
        quizStatisticsRepository.findSelectionsForQuestion(question.getId()).forEach(row -> {
            if (!(row.getSelection() instanceof DragAndDropSubmittedAnswerSelection selection)) {
                return;
            }
            Map<Long, Long> submittedDragItemByDropLocation = new HashMap<>();
            for (DragAndDropMappingSelection mapping : selection.getMappings()) {
                submittedDragItemByDropLocation.putIfAbsent(mapping.dropLocationId(), mapping.dragItemId());
            }
            int index = Boolean.TRUE.equals(row.getRated()) ? 0 : 1;
            countersByDropLocation.forEach((dropLocationId, counters) -> {
                Set<Long> correctDragItems = correctDragItemsByDropLocation.getOrDefault(dropLocationId, Set.of());
                Long submittedDragItem = submittedDragItemByDropLocation.get(dropLocationId);
                boolean correct = correctDragItems.isEmpty() && submittedDragItem == null || submittedDragItem != null && correctDragItems.contains(submittedDragItem);
                if (correct) {
                    counters[index]++;
                }
            });
        });
        return countersByDropLocation;
    }

    private Map<Long, long[]> shortAnswerCounters(ShortAnswerQuestion question) {
        Map<Long, long[]> countersBySpot = new HashMap<>();
        for (ShortAnswerSpot spot : question.getSpots()) {
            countersBySpot.put(spot.getId(), new long[2]);
        }
        quizStatisticsRepository.findSelectionsForQuestion(question.getId()).forEach(row -> {
            if (!(row.getSelection() instanceof ShortAnswerSubmittedAnswerSelection selection)) {
                return;
            }
            int index = Boolean.TRUE.equals(row.getRated()) ? 0 : 1;
            Set<Long> countedSpots = new HashSet<>();
            for (ShortAnswerTextSelection submittedText : selection.getSubmittedTexts()) {
                if (!countedSpots.add(submittedText.getSpotId())) {
                    continue;
                }
                long[] counters = countersBySpot.get(submittedText.getSpotId());
                if (counters != null && Boolean.TRUE.equals(submittedText.getIsCorrect())) {
                    counters[index]++;
                }
            }
        });
        return countersBySpot;
    }
}
