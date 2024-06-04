package de.tum.in.www1.artemis.service.quiz;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_SCHEDULING;
import static de.tum.in.www1.artemis.domain.enumeration.QuizAction.START_NOW;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseLifecycle;
import de.tum.in.www1.artemis.domain.enumeration.QuizMode;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.quiz.QuizBatch;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizSubmission;
import de.tum.in.www1.artemis.repository.ParticipationRepository;
import de.tum.in.www1.artemis.repository.QuizBatchRepository;
import de.tum.in.www1.artemis.repository.QuizExerciseRepository;
import de.tum.in.www1.artemis.repository.QuizSubmissionRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.scheduled.ScheduleService;
import de.tum.in.www1.artemis.service.util.Tuple;

@Profile(PROFILE_SCHEDULING)
@Service
public class QuizScheduleService {

    private static final Logger log = LoggerFactory.getLogger(QuizScheduleService.class);

    private final QuizMessagingService quizMessagingService;

    private final QuizExerciseRepository quizExerciseRepository;

    private final ScheduleService scheduleService;

    private final QuizBatchRepository quizBatchRepository;

    private final QuizStatisticService quizStatisticService;

    private final ParticipationRepository participationRepository;

    private final ResultRepository resultRepository;

    private final QuizSubmissionRepository quizSubmissionRepository;

    public QuizScheduleService(QuizMessagingService quizMessagingService, QuizExerciseRepository quizExerciseRepository, ScheduleService scheduleService,
            QuizBatchRepository quizBatchRepository, QuizStatisticService quizStatisticService, ParticipationRepository participationRepository, ResultRepository resultRepository,
            QuizSubmissionRepository quizSubmissionRepository) {
        this.quizMessagingService = quizMessagingService;
        this.quizExerciseRepository = quizExerciseRepository;
        this.scheduleService = scheduleService;
        this.quizBatchRepository = quizBatchRepository;
        this.quizStatisticService = quizStatisticService;
        this.participationRepository = participationRepository;
        this.resultRepository = resultRepository;
        this.quizSubmissionRepository = quizSubmissionRepository;
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
            if (quizBatch.isPresent() && quizBatch.get().getStartTime() != null) {
                if (quizBatch.get().getStartTime().isAfter(ZonedDateTime.now())) {
                    scheduleService.scheduleTask(quizExercise, quizBatch.get(), ExerciseLifecycle.START, () -> executeQuizStartNowTask(quizExerciseId));
                }
                scheduleQuizStatistics(quizExercise);
            }
        }
    }

    public void scheduleQuizStatistics(QuizExercise quizExercise) {
        var quizBatch = quizExercise.getQuizBatches().stream().findAny();
        if (quizExercise.getDueDate() != null) {
            scheduleService.scheduleTask(quizExercise, quizBatch.get(), ExerciseLifecycle.DUE,
                    Set.of(new Tuple<>(quizExercise.getDueDate().plusSeconds(5), () -> calculateAllResults(quizExercise.getId(), quizBatch.get().getId()))));
        }
    }

    private void calculateAllResults(long quizExerciseId, long quizBatchId) {
        QuizExercise quizExercise = quizExerciseRepository.findByIdWithQuestionsAndStatisticsElseThrow(quizExerciseId);
        QuizBatch quizBatch = quizExercise.getQuizBatches().stream().filter(batch -> batch.getId().equals(quizBatchId)).findAny().orElse(null);
        log.info("Calculating results for quiz {}", quizExercise.getId());
        participationRepository.findByExerciseId(quizExercise.getId()).forEach(participation -> {
            participation.setExercise(quizExercise);
            Optional<QuizSubmission> quizSubmissionOptional = quizSubmissionRepository.findWithEagerSubmittedAnswersByParticipationId(participation.getId());

            if (quizSubmissionOptional.isEmpty()) {
                return;
            }
            QuizSubmission quizSubmission = quizSubmissionOptional.get();

            if (quizSubmission.isSubmitted()) {
                if (quizSubmission.getType() == null) {
                    quizSubmission.setType(SubmissionType.MANUAL);
                }
            }
            else if (quizExercise.isQuizEnded() || quizBatch != null && quizBatch.isEnded()) {
                quizSubmission.setSubmitted(true);
                quizSubmission.setType(SubmissionType.TIMEOUT);
                quizSubmission.setSubmissionDate(ZonedDateTime.now());
            }

            Result result = new Result().participation(participation);
            result.setRated(true);
            result.setAssessmentType(AssessmentType.AUTOMATIC);
            result.setCompletionDate(quizSubmission.getSubmissionDate());
            result.setSubmission(quizSubmission);

            quizSubmission.calculateAndUpdateScores(quizExercise.getQuizQuestions());
            result.evaluateQuizSubmission();

            quizSubmissionRepository.save(quizSubmission);
            resultRepository.save(result);
        });
        quizStatisticService.recalculateStatistics(quizExercise);
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
