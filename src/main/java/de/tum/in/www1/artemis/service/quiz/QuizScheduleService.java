package de.tum.in.www1.artemis.service.quiz;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_SCHEDULING;
import static de.tum.in.www1.artemis.config.StartupDelayConfig.QUIZ_EXERCISE_SCHEDULE_DELAY_SEC;
import static de.tum.in.www1.artemis.domain.enumeration.QuizAction.START_NOW;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseLifecycle;
import de.tum.in.www1.artemis.domain.enumeration.QuizMode;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizBatch;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizSubmission;
import de.tum.in.www1.artemis.domain.quiz.SubmittedAnswer;
import de.tum.in.www1.artemis.repository.QuizBatchRepository;
import de.tum.in.www1.artemis.repository.QuizExerciseRepository;
import de.tum.in.www1.artemis.repository.QuizSubmissionRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.WebsocketMessagingService;
import de.tum.in.www1.artemis.service.scheduled.ScheduleService;
import de.tum.in.www1.artemis.service.util.Tuple;
import tech.jhipster.config.JHipsterConstants;

@Profile(PROFILE_SCHEDULING)
@Service
public class QuizScheduleService {

    private static final Logger log = LoggerFactory.getLogger(QuizScheduleService.class);

    private final QuizMessagingService quizMessagingService;

    private final QuizExerciseRepository quizExerciseRepository;

    private final ScheduleService scheduleService;

    private final QuizBatchRepository quizBatchRepository;

    private final QuizStatisticService quizStatisticService;

    private final StudentParticipationRepository studentParticipationRepository;

    private final ResultRepository resultRepository;

    private final QuizSubmissionRepository quizSubmissionRepository;

    private final TaskScheduler scheduler;

    private final Environment env;

    private final WebsocketMessagingService websocketMessagingService;

    public QuizScheduleService(QuizMessagingService quizMessagingService, QuizExerciseRepository quizExerciseRepository, ScheduleService scheduleService,
            QuizBatchRepository quizBatchRepository, QuizStatisticService quizStatisticService, StudentParticipationRepository studentParticipationRepository,
            ResultRepository resultRepository, QuizSubmissionRepository quizSubmissionRepository, Environment env, @Qualifier("taskScheduler") TaskScheduler scheduler,
            WebsocketMessagingService websocketMessagingService) {
        this.quizMessagingService = quizMessagingService;
        this.quizExerciseRepository = quizExerciseRepository;
        this.scheduleService = scheduleService;
        this.quizBatchRepository = quizBatchRepository;
        this.quizStatisticService = quizStatisticService;
        this.studentParticipationRepository = studentParticipationRepository;
        this.resultRepository = resultRepository;
        this.quizSubmissionRepository = quizSubmissionRepository;
        this.scheduler = scheduler;
        this.env = env;
        this.websocketMessagingService = websocketMessagingService;
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
        if (quizExercise.getDueDate() != null && !quizExercise.isQuizEnded()) {
            // we only schedule the task if the quiz is not over yet
            scheduleService.scheduleTask(quizExercise, ExerciseLifecycle.DUE,
                    Set.of(new Tuple<>(quizExercise.getDueDate().plusSeconds(5), () -> calculateAllResults(quizExercise.getId()))));
        }
    }

    /**
     * Calculate the results for all participations of the given quiz exercise
     *
     * @param quizExerciseId the id of the quiz exercise for which the results should be calculated
     */
    public void calculateAllResults(long quizExerciseId) {
        QuizExercise quizExercise = quizExerciseRepository.findByIdWithQuestionsAndStatisticsElseThrow(quizExerciseId);
        log.info("Calculating results for quiz {}", quizExercise.getId());
        studentParticipationRepository.findByExerciseId(quizExercise.getId()).forEach(participation -> {
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
            else if (quizExercise.isQuizEnded()) {
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
            sendQuizResultToUser(quizExerciseId, participation);
        });
        quizStatisticService.recalculateStatistics(quizExercise);
        // notify users via websocket about new results for the statistics, filter out solution information
        quizExercise.filterForStatisticWebsocket();
        websocketMessagingService.sendMessage("/topic/statistic/" + quizExercise.getId(), quizExercise);
    }

    private void sendQuizResultToUser(long quizExerciseId, StudentParticipation participation) {
        // TODO: we should convert this into a DTO instead of removing data from the entity
        var user = participation.getParticipantIdentifier();
        removeUnnecessaryObjectsBeforeSendingToClient(participation);
        websocketMessagingService.sendMessageToUser(user, "/topic/exercise/" + quizExerciseId + "/participation", participation);
    }

    private void removeUnnecessaryObjectsBeforeSendingToClient(StudentParticipation participation) {
        if (participation.getExercise() != null) {
            var quizExercise = (QuizExercise) participation.getExercise();
            // we do not need the course and lectures
            quizExercise.setCourse(null);
            // students should not see statistics
            // TODO: this would be useful, but leads to problems when the quiz schedule service wants to access the statistics again later on
            // quizExercise.setQuizPointStatistic(null);
            // quizExercise.getQuizQuestions().forEach(quizQuestion -> quizQuestion.setQuizQuestionStatistic(null));
        }
        // submissions are part of results, so we do not need them twice
        participation.setSubmissions(null);
        participation.setParticipant(null);
        if (participation.getResults() != null && !participation.getResults().isEmpty()) {
            QuizSubmission quizSubmission = (QuizSubmission) participation.getResults().iterator().next().getSubmission();
            if (quizSubmission != null && quizSubmission.getSubmittedAnswers() != null) {
                for (SubmittedAnswer submittedAnswer : quizSubmission.getSubmittedAnswers()) {
                    if (submittedAnswer.getQuizQuestion() != null) {
                        // we do not need all information of the questions again, they are already stored in the exercise
                        var question = submittedAnswer.getQuizQuestion();
                        submittedAnswer.setQuizQuestion(question.copyQuestionId());
                    }
                }
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

    @EventListener(ApplicationReadyEvent.class)
    public void applicationReady() {
        // schedule the task after the application has started to avoid delaying the start of the application
        scheduler.schedule(this::scheduleRunningExercisesOnStartup, Instant.now().plusSeconds(QUIZ_EXERCISE_SCHEDULE_DELAY_SEC));
    }

    /**
     * Schedule all quiz exercises that will start in the future on application startup
     */
    public void scheduleRunningExercisesOnStartup() {
        try {
            Collection<String> activeProfiles = Arrays.asList(env.getActiveProfiles());
            if (activeProfiles.contains(JHipsterConstants.SPRING_PROFILE_DEVELOPMENT)) {
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
