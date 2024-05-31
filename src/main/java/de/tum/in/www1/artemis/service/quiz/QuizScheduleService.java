package de.tum.in.www1.artemis.service.quiz;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_SCHEDULING;
import static de.tum.in.www1.artemis.domain.enumeration.QuizAction.START_NOW;

import java.time.ZonedDateTime;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.enumeration.ExerciseLifecycle;
import de.tum.in.www1.artemis.domain.enumeration.QuizMode;
import de.tum.in.www1.artemis.domain.quiz.QuizBatch;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.repository.QuizBatchRepository;
import de.tum.in.www1.artemis.repository.QuizExerciseRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.scheduled.ScheduleService;

@Profile(PROFILE_SCHEDULING)
@Service
public class QuizScheduleService {

    private static final Logger log = LoggerFactory.getLogger(QuizScheduleService.class);

    private final QuizMessagingService quizMessagingService;

    private final QuizExerciseRepository quizExerciseRepository;

    private final ScheduleService scheduleService;

    private final QuizBatchRepository quizBatchRepository;

    public QuizScheduleService(QuizMessagingService quizMessagingService, QuizExerciseRepository quizExerciseRepository, ScheduleService scheduleService,
            QuizBatchRepository quizBatchRepository) {
        this.quizMessagingService = quizMessagingService;
        this.quizExerciseRepository = quizExerciseRepository;
        this.scheduleService = scheduleService;
        this.quizBatchRepository = quizBatchRepository;
    }

    /**
     * Start scheduler of quiz and update the quiz exercise in the hash map
     *
     * @param quizExerciseId the id of the quiz exercise that should be scheduled for being started automatically
     */
    public void scheduleQuizStart(final long quizExerciseId) {
        // first remove and cancel old scheduledFuture if it exists
        cancelScheduledQuizStart(quizExerciseId);
        // reload from database to make sure there are no proxy objects
        final var quizExercise = quizExerciseRepository.findOneWithQuestionsAndStatistics(quizExerciseId);
        if (quizExercise != null && quizExercise.getQuizMode() == QuizMode.SYNCHRONIZED) {
            // TODO: quiz cleanup: it should be possible to schedule quiz batches in BATCHED mode
            var quizBatch = quizExercise.getQuizBatches().stream().findAny();
            if (quizBatch.isPresent() && quizBatch.get().getStartTime() != null && quizBatch.get().getStartTime().isAfter(ZonedDateTime.now())) {
                scheduleService.scheduleTask(quizExercise, quizBatch.get(), ExerciseLifecycle.START, () -> executeQuizStartNowTask(quizExerciseId));
            }
        }
    }

    /**
     * cancels the quiz start for the given exercise id, e.g. because the quiz was deleted or the quiz start date was changed
     *
     * @param quizExerciseId the quiz exercise for which the quiz start should be canceled
     */
    public void cancelScheduledQuizStart(Long quizExerciseId) {
        scheduleService.cancelScheduledTaskForLifecycle(quizExerciseId, ExerciseLifecycle.START);
    }

    /**
     * Internal method to start and send the {@link QuizExercise} to the clients when called
     */
    void executeQuizStartNowTask(Long quizExerciseId) {
        log.debug("Sending quiz {} start", quizExerciseId);
        QuizExercise quizExercise = quizExerciseRepository.findByIdWithQuestionsAndStatisticsElseThrow(quizExerciseId);

        if (quizExercise.getQuizMode() != QuizMode.SYNCHRONIZED) {
            throw new IllegalStateException();
        }

        // TODO: quiz cleanup: We create a batch that has just started here because we can't access QuizBatchService here because of dependencies
        var quizBatch = new QuizBatch();
        quizBatch.setQuizExercise(quizExercise);
        quizBatch.setStartTime(ZonedDateTime.now());
        quizExercise.setQuizBatches(Set.of(quizBatch));

        quizBatchRepository.save(quizBatch);

        SecurityUtils.setAuthorizationObject();
        quizMessagingService.sendQuizExerciseToSubscribedClients(quizExercise, quizBatch, START_NOW);
    }
}
