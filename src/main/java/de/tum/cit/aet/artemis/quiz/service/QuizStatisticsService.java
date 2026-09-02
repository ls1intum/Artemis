package de.tum.cit.aet.artemis.quiz.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
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
import de.tum.cit.aet.artemis.quiz.dto.QuizStatisticCounterDTO;
import de.tum.cit.aet.artemis.quiz.dto.QuizStatisticsOverviewDTO;
import de.tum.cit.aet.artemis.quiz.repository.QuizStatisticProjections.RatedSelection;
import de.tum.cit.aet.artemis.quiz.repository.QuizStatisticsRepository;

/**
 * Calculates quiz statistics on demand from results and submitted-answer selections.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class QuizStatisticsService {

    private static final Duration NOTIFICATION_DEBOUNCE = Duration.ofSeconds(1);

    private static final String NOTIFICATION_DEBOUNCE_MAP = "quiz-statistics-notification-debounce";

    private final QuizStatisticsRepository quizStatisticsRepository;

    private final WebsocketMessagingService websocketMessagingService;

    private final TaskScheduler taskScheduler;

    private final DistributedMap<Long, String> pendingNotifications;

    public QuizStatisticsService(QuizStatisticsRepository quizStatisticsRepository, WebsocketMessagingService websocketMessagingService,
            @Qualifier("taskScheduler") TaskScheduler taskScheduler, DistributedDataProvider distributedDataProvider) {
        this.quizStatisticsRepository = quizStatisticsRepository;
        this.websocketMessagingService = websocketMessagingService;
        this.taskScheduler = taskScheduler;
        this.pendingNotifications = distributedDataProvider.getExpiringMap(NOTIFICATION_DEBOUNCE_MAP, NOTIFICATION_DEBOUNCE);
    }

    /**
     * Calculates the participant and fully-correct counters for every question.
     *
     * @param quizExercise the loaded quiz exercise
     * @return the quiz exercise with overview statistics
     */
    public QuizStatisticsOverviewDTO getOverview(QuizExercise quizExercise) {
        long quizExerciseId = quizExercise.getId();
        CounterPair participantCounts = new CounterPair();
        quizStatisticsRepository.findPointStatistic(quizExerciseId).forEach(bucket -> participantCounts.add(bucket.getRated(), bucket.getParticipantCount()));
        Map<Long, QuestionCounters> countersByQuestion = new HashMap<>();
        for (QuizQuestion question : quizExercise.getQuizQuestions()) {
            countersByQuestion.put(question.getId(), new QuestionCounters());
        }
        quizStatisticsRepository.findQuestionAggregatesForQuiz(quizExerciseId).forEach(aggregate -> {
            QuestionCounters counters = countersByQuestion.get(aggregate.getQuestionId());
            if (counters != null) {
                counters.set(aggregate.getRated(), aggregate.getParticipantCount(), aggregate.getCorrectCount());
            }
        });

        Map<Long, QuizQuestionStatisticDTO> statisticsByQuestion = new HashMap<>();
        for (QuizQuestion question : quizExercise.getQuizQuestions()) {
            statisticsByQuestion.put(question.getId(), countersByQuestion.get(question.getId()).toDto(question, null));
        }
        return QuizStatisticsOverviewDTO.of(quizExercise, statisticsByQuestion, participantCounts.rated, participantCounts.unrated);
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
        long roundedOverallPoints = Math.round(overallPoints);
        Map<Double, CounterPair> countersByPoints = new HashMap<>();
        for (long points = 0; points <= roundedOverallPoints; points++) {
            countersByPoints.put((double) points, new CounterPair());
        }

        CounterPair resultCounts = new CounterPair();
        for (var bucket : quizStatisticsRepository.findPointStatistic(quizExerciseId)) {
            double points = Math.round(overallPoints * bucket.getScore() / 100);
            countersByPoints.computeIfAbsent(points, ignored -> new CounterPair()).add(bucket.getRated(), bucket.getParticipantCount());
            resultCounts.add(bucket.getRated(), bucket.getParticipantCount());
        }

        Map<Double, QuizStatisticCounterDTO> counterDTOsByPoints = new HashMap<>();
        countersByPoints.forEach((points, counters) -> counterDTOsByPoints.put(points, counters.toDto()));
        QuizPointStatisticDTO pointStatistic = QuizPointStatisticDTO.of(counterDTOsByPoints, resultCounts.rated, resultCounts.unrated);
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
        List<RatedSelection> selections = quizStatisticsRepository.findSelectionsForQuestion(questionId);
        QuestionCounters counters = questionAggregate(selections, question.getPoints());
        Map<Long, CounterPair> componentCounters = switch (question) {
            case MultipleChoiceQuestion multipleChoiceQuestion -> multipleChoiceCounters(multipleChoiceQuestion, selections);
            case DragAndDropQuestion dragAndDropQuestion -> dragAndDropCounters(dragAndDropQuestion, selections);
            case ShortAnswerQuestion shortAnswerQuestion -> shortAnswerCounters(shortAnswerQuestion, selections);
            default -> throw new IllegalArgumentException("Unsupported quiz question type " + question.getClass().getName());
        };
        QuizQuestionStatisticDTO statistic = counters.toDto(question, toCounterDTOs(componentCounters));
        return QuizQuestionStatisticResponseDTO.of(quizExercise, question, statistic);
    }

    /**
     * Notifies open statistics pages that their on-demand data changed. The payload is intentionally only the exercise id;
     * each page reloads its own endpoint.
     *
     * @param quizExerciseId the id of the changed quiz exercise
     */
    public void notifyStatisticsChanged(long quizExerciseId) {
        String claim = UUID.randomUUID().toString();
        if (pendingNotifications.putIfAbsent(quizExerciseId, claim) != null) {
            return;
        }
        try {
            taskScheduler.schedule(() -> {
                pendingNotifications.remove(quizExerciseId, claim);
                websocketMessagingService.sendMessage("/topic/statistic/" + quizExerciseId, quizExerciseId);
            }, Instant.now().plus(NOTIFICATION_DEBOUNCE));
        }
        catch (RuntimeException exception) {
            pendingNotifications.remove(quizExerciseId, claim);
            throw exception;
        }
    }

    private static QuestionCounters questionAggregate(List<RatedSelection> selections, double questionPoints) {
        QuestionCounters counters = new QuestionCounters();
        for (RatedSelection selection : selections) {
            Double scoreInPoints = selection.getScoreInPoints();
            counters.increment(selection.getRated(), scoreInPoints != null && scoreInPoints >= questionPoints);
        }
        return counters;
    }

    private static Map<Long, CounterPair> multipleChoiceCounters(MultipleChoiceQuestion question, List<RatedSelection> selections) {
        Map<Long, CounterPair> countersByAnswer = new HashMap<>();
        for (AnswerOption answerOption : question.getAnswerOptions()) {
            countersByAnswer.put(answerOption.getId(), new CounterPair());
        }
        selections.forEach(row -> {
            if (!(row.getSelection() instanceof MultipleChoiceSubmittedAnswerSelection selection)) {
                return;
            }
            Set<Long> countedAnswerOptions = new HashSet<>();
            for (Long answerOptionId : selection.getSelectedOptionIds()) {
                if (!countedAnswerOptions.add(answerOptionId)) {
                    continue;
                }
                CounterPair counters = countersByAnswer.get(answerOptionId);
                if (counters != null) {
                    counters.increment(row.getRated());
                }
            }
        });
        return countersByAnswer;
    }

    private static Map<Long, CounterPair> dragAndDropCounters(DragAndDropQuestion question, List<RatedSelection> selections) {
        Map<Long, Set<Long>> correctDragItemsByDropLocation = new HashMap<>();
        Map<Long, CounterPair> countersByDropLocation = new HashMap<>();
        for (DropLocation dropLocation : question.getDropLocations()) {
            correctDragItemsByDropLocation.put(dropLocation.getId(), new HashSet<>());
            countersByDropLocation.put(dropLocation.getId(), new CounterPair());
        }
        for (DragAndDropMapping mapping : question.getCorrectMappings()) {
            Set<Long> correctDragItems = correctDragItemsByDropLocation.get(mapping.getDropLocation().getId());
            if (correctDragItems != null) {
                correctDragItems.add(mapping.getDragItem().getId());
            }
        }
        selections.forEach(row -> {
            if (!(row.getSelection() instanceof DragAndDropSubmittedAnswerSelection selection)) {
                return;
            }
            Map<Long, Long> submittedDragItemByDropLocation = new HashMap<>();
            for (DragAndDropMappingSelection mapping : selection.getMappings()) {
                submittedDragItemByDropLocation.putIfAbsent(mapping.dropLocationId(), mapping.dragItemId());
            }
            countersByDropLocation.forEach((dropLocationId, counters) -> {
                Set<Long> correctDragItems = correctDragItemsByDropLocation.getOrDefault(dropLocationId, Set.of());
                Long submittedDragItem = submittedDragItemByDropLocation.get(dropLocationId);
                boolean correct = (correctDragItems.isEmpty() && submittedDragItem == null) || (submittedDragItem != null && correctDragItems.contains(submittedDragItem));
                if (correct) {
                    counters.increment(row.getRated());
                }
            });
        });
        return countersByDropLocation;
    }

    private static Map<Long, CounterPair> shortAnswerCounters(ShortAnswerQuestion question, List<RatedSelection> selections) {
        Map<Long, CounterPair> countersBySpot = new HashMap<>();
        for (ShortAnswerSpot spot : question.getSpots()) {
            countersBySpot.put(spot.getId(), new CounterPair());
        }
        selections.forEach(row -> {
            if (!(row.getSelection() instanceof ShortAnswerSubmittedAnswerSelection selection)) {
                return;
            }
            Set<Long> countedSpots = new HashSet<>();
            for (ShortAnswerTextSelection submittedText : selection.getSubmittedTexts()) {
                if (!countedSpots.add(submittedText.getSpotId())) {
                    continue;
                }
                CounterPair counters = countersBySpot.get(submittedText.getSpotId());
                if (counters != null && Boolean.TRUE.equals(submittedText.getIsCorrect())) {
                    counters.increment(row.getRated());
                }
            }
        });
        return countersBySpot;
    }

    private static Map<Long, QuizStatisticCounterDTO> toCounterDTOs(Map<Long, CounterPair> countersByComponent) {
        Map<Long, QuizStatisticCounterDTO> counterDTOs = HashMap.newHashMap(countersByComponent.size());
        countersByComponent.forEach((id, counters) -> counterDTOs.put(id, counters.toDto()));
        return counterDTOs;
    }

    private static final class CounterPair {

        private long rated;

        private long unrated;

        private void add(boolean ratedBucket, long value) {
            if (ratedBucket) {
                rated += value;
            }
            else {
                unrated += value;
            }
        }

        private void increment(boolean ratedBucket) {
            add(ratedBucket, 1);
        }

        private QuizStatisticCounterDTO toDto() {
            return QuizStatisticCounterDTO.of(rated, unrated);
        }
    }

    private static final class QuestionCounters {

        private long ratedParticipants;

        private long unratedParticipants;

        private long ratedCorrect;

        private long unratedCorrect;

        private void set(boolean ratedBucket, long participantCount, long correctCount) {
            if (ratedBucket) {
                ratedParticipants = participantCount;
                ratedCorrect = correctCount;
            }
            else {
                unratedParticipants = participantCount;
                unratedCorrect = correctCount;
            }
        }

        private void increment(boolean ratedBucket, boolean correct) {
            if (ratedBucket) {
                ratedParticipants++;
                if (correct) {
                    ratedCorrect++;
                }
            }
            else {
                unratedParticipants++;
                if (correct) {
                    unratedCorrect++;
                }
            }
        }

        private QuizQuestionStatisticDTO toDto(QuizQuestion question, Map<Long, QuizStatisticCounterDTO> componentStatistics) {
            return QuizQuestionStatisticDTO.of(question, ratedParticipants, unratedParticipants, ratedCorrect, unratedCorrect, componentStatistics);
        }
    }
}
