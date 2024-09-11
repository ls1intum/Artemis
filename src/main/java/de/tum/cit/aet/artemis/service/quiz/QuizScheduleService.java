package de.tum.cit.aet.artemis.service.quiz;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SCHEDULING;
import static de.tum.cit.aet.artemis.core.config.StartupDelayConfig.QUIZ_EXERCISE_SCHEDULE_DELAY_SEC;
import static de.tum.cit.aet.artemis.domain.enumeration.QuizAction.START_NOW;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.domain.enumeration.ExerciseLifecycle;
import de.tum.cit.aet.artemis.domain.enumeration.QuizMode;
import de.tum.cit.aet.artemis.quiz.domain.QuizBatch;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.repository.QuizBatchRepository;
import de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository;
import de.tum.cit.aet.artemis.service.ProfileService;
import de.tum.cit.aet.artemis.service.scheduled.ScheduleService;
import de.tum.cit.aet.artemis.service.util.Tuple;

@Profile(PROFILE_SCHEDULING)
@Service
public class QuizScheduleService {

    private static final Logger log = LoggerFactory.getLogger(QuizScheduleService.class);

    private final QuizMessagingService quizMessagingService;

    private final QuizExerciseRepository quizExerciseRepository;

    private final ScheduleService scheduleService;

    private final QuizBatchRepository quizBatchRepository;

    private final TaskScheduler scheduler;

    private final ProfileService profileService;

    private final QuizSubmissionService quizSubmissionService;

    public QuizScheduleService(QuizMessagingService quizMessagingService, QuizExerciseRepository quizExerciseRepository, QuizBatchRepository quizBatchRepository,
            ScheduleService scheduleService, @Qualifier("taskScheduler") TaskScheduler scheduler, ProfileService profileService, QuizSubmissionService quizSubmissionService) {
        this.quizMessagingService = quizMessagingService;
        this.quizExerciseRepository = quizExerciseRepository;
        this.scheduleService = scheduleService;
        this.quizBatchRepository = quizBatchRepository;
        this.scheduler = scheduler;
        this.profileService = profileService;
        this.quizSubmissionService = quizSubmissionService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void applicationReady() {
        // schedule the task after the application has started to avoid delaying the start of the application
        scheduler.schedule(this::scheduleRunningExercisesOnStartup, Instant.now().plusSeconds(QUIZ_EXERCISE_SCHEDULE_DELAY_SEC));
    }

    /**
     * Start scheduler of quiz and update the quiz exercise in the hash map
     *
     * @param quizExerciseId the id of the quiz exercise that should be scheduled for being started automatically
     */
    public void scheduleQuizStart(final long quizExerciseId) {
        final var quizExercise = quizExerciseRepository.findWithEagerBatchesByIdOrElseThrow(quizExerciseId);
        scheduleQuizStart(quizExercise);
    }

    /**
     * Start scheduler of quiz and update the quiz exercise in the hash map
     *
     * @param quizExercise the quiz exercise that should be scheduled for being started automatically
     */
    public void scheduleQuizStart(QuizExercise quizExercise) {
        // first remove and cancel old scheduledFuture if it exists
        cancelScheduledQuizStart(quizExercise.getId());
        if (quizExercise.getQuizMode() == QuizMode.SYNCHRONIZED) {
            // TODO: quiz cleanup: it should be possible to schedule quiz batches in BATCHED mode
            var quizBatch = quizExercise.getQuizBatches().stream().findAny();
            if (quizBatch.isPresent() && quizBatch.get().getStartTime() != null) {
                if (quizBatch.get().getStartTime().isAfter(ZonedDateTime.now())) {
                    scheduleService.scheduleTask(quizExercise, quizBatch.get(), ExerciseLifecycle.START, () -> executeQuizStartNowTask(quizExercise.getId()));
                }
            }
        }
        scheduleCalculateAllResults(quizExercise);
    }

    /**
     * schedule the calculation of the results for all participations of the given quiz exercise
     *
     * @param quizExercise the quiz exercise for which the results should be calculated
     */
    public void scheduleCalculateAllResults(QuizExercise quizExercise) {
        if (quizExercise.getDueDate() != null && !quizExercise.isQuizEnded()) {
            // we only schedule the task if the quiz is not over yet
            scheduleService.scheduleTask(quizExercise, ExerciseLifecycle.DUE,
                    Set.of(new Tuple<>(quizExercise.getDueDate().plusSeconds(5), () -> quizSubmissionService.calculateAllResults(quizExercise.getId()))));
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

    /**
     * Schedule all quiz exercises that will start in the future on application startup
     */
    public void scheduleRunningExercisesOnStartup() {
        try {
            if (profileService.isDevActive()) {
                // only execute this on production server, i.e. when the prod profile is active
                // NOTE: if you want to test this locally, please comment it out, but do not commit the changes
                return;
            }
            SecurityUtils.setAuthorizationObject();

            List<QuizExercise> exercisesToBeScheduled = quizExerciseRepository.findAllToBeScheduled(ZonedDateTime.now());
            exercisesToBeScheduled.forEach(this::scheduleQuizStart);

            log.info("Scheduled {} quiz exercises.", exercisesToBeScheduled.size());
        }
        catch (Exception e) {
            log.error("Failed to start QuizScheduleService", e);
        }
    }
}
