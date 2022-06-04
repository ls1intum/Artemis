package de.tum.in.www1.artemis.service.scheduled.cache.quiz;

import static de.tum.in.www1.artemis.service.util.TimeLogUtil.formatDurationFrom;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

import com.hazelcast.config.Config;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.cp.IAtomicReference;
import com.hazelcast.scheduledexecutor.*;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.QuizMode;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizBatch;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizSubmission;
import de.tum.in.www1.artemis.domain.quiz.SubmittedAnswer;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.QuizMessagingService;
import de.tum.in.www1.artemis.service.QuizStatisticService;
import de.tum.in.www1.artemis.service.scheduled.cache.Cache;

@Service
public class QuizScheduleService {

    private static final Logger log = LoggerFactory.getLogger(QuizScheduleService.class);

    private static final String HAZELCAST_PROCESS_CACHE_HANDLER = QuizProcessCacheTask.HAZELCAST_PROCESS_CACHE_TASK + "-handler";

    private final IScheduledExecutorService threadPoolTaskScheduler;

    private final IAtomicReference<ScheduledTaskHandler> scheduledProcessQuizSubmissions;

    private final StudentParticipationRepository studentParticipationRepository;

    private final UserRepository userRepository;

    private final QuizSubmissionRepository quizSubmissionRepository;

    private final QuizMessagingService quizMessagingService;

    private final QuizStatisticService quizStatisticService;

    private final SimpMessageSendingOperations messagingTemplate;

    private final QuizCache quizCache;

    private final QuizExerciseRepository quizExerciseRepository;

    public QuizScheduleService(SimpMessageSendingOperations messagingTemplate, StudentParticipationRepository studentParticipationRepository, UserRepository userRepository,
            QuizSubmissionRepository quizSubmissionRepository, HazelcastInstance hazelcastInstance, QuizExerciseRepository quizExerciseRepository,
            QuizMessagingService quizMessagingService, QuizStatisticService quizStatisticService) {
        this.messagingTemplate = messagingTemplate;
        this.studentParticipationRepository = studentParticipationRepository;
        this.userRepository = userRepository;
        this.quizSubmissionRepository = quizSubmissionRepository;
        this.quizExerciseRepository = quizExerciseRepository;
        this.quizMessagingService = quizMessagingService;
        this.quizStatisticService = quizStatisticService;
        this.scheduledProcessQuizSubmissions = hazelcastInstance.getCPSubsystem().getAtomicReference(HAZELCAST_PROCESS_CACHE_HANDLER);
        this.threadPoolTaskScheduler = hazelcastInstance.getScheduledExecutorService(Constants.HAZELCAST_QUIZ_SCHEDULER);
        this.quizCache = new QuizCache(hazelcastInstance);
    }

    /**
     * Configures Hazelcast for the QuizScheduleService before the HazelcastInstance is created.
     *
     * @param config the {@link Config} the QuizScheduleService-specific configuration should be added to
     */
    public static void configureHazelcast(Config config) {
        QuizCache.configureHazelcast(config);
        // Pool size default 16, increased capacity (as we could have many quizzes) and default durability for now
        config.getScheduledExecutorConfig(Constants.HAZELCAST_QUIZ_SCHEDULER).setPoolSize(16).setCapacity(1000).setDurability(1);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void applicationReady() {
        // activate Quiz Schedule Service
        SecurityUtils.setAuthorizationObject();
        startSchedule(5 * 1000);                          // every 5 seconds
    }

    /**
     * add a quizSubmission to the submissionHashMap
     *
     * @param quizExerciseId the quizExerciseId of the quiz the submission belongs to (first Key)
     * @param username       the username of the user, who submitted the submission (second Key)
     * @param quizSubmission the quizSubmission, which should be added (Value)
     */
    public void updateSubmission(Long quizExerciseId, String username, QuizSubmission quizSubmission) {
        if (quizSubmission != null && quizExerciseId != null && username != null) {
            ((QuizExerciseCache) quizCache.getTransientWriteCacheFor(quizExerciseId)).getSubmissions().put(username, quizSubmission);
        }
    }

    /**
     * add a result to resultHashMap for a statistic-update
     * this should only be invoked once, when the quiz was submitted
     *
     * @param quizExerciseId the quizExerciseId of the quiz the result belongs to (first Key)
     * @param result the result, which should be added
     */
    public void addResultForStatisticUpdate(Long quizExerciseId, Result result) {
        log.debug("add result for statistic update for quiz {}: {}", quizExerciseId, result);
        if (quizExerciseId != null && result != null) {
            ((QuizExerciseCache) quizCache.getTransientWriteCacheFor(quizExerciseId)).getResults().put(result.getId(), result);
        }
    }

    /**
     * add a participation to participationHashMap to send them back to the user when the quiz ends
     *
     * @param quizExerciseId        the quizExerciseId of the quiz the result belongs to (first Key)
     * @param participation the result, which should be added
     */
    private void addParticipation(Long quizExerciseId, StudentParticipation participation) {
        if (quizExerciseId != null && participation != null) {
            ((QuizExerciseCache) quizCache.getTransientWriteCacheFor(quizExerciseId)).getParticipations().put(participation.getParticipantIdentifier(), participation);
        }
    }

    /**
     * get a cached quizSubmission by quizExerciseId and username
     *
     * @param quizExerciseId   the quizExerciseId of the quiz the submission belongs to (first Key)
     * @param username the username of the user, who submitted the submission (second Key)
     * @return the quizSubmission, with the given quizExerciseId and username -> return an empty QuizSubmission if there is no quizSubmission -> return null if the quizExerciseId or if the
     *         username is null
     */
    public QuizSubmission getQuizSubmission(Long quizExerciseId, String username) {
        if (quizExerciseId == null || username == null) {
            return null;
        }
        QuizSubmission quizSubmission = ((QuizExerciseCache) quizCache.getReadCacheFor(quizExerciseId)).getSubmissions().get(username);
        if (quizSubmission != null) {
            return quizSubmission;
        }
        // return an empty quizSubmission if the maps contain no mapping for the keys
        return new QuizSubmission().submittedAnswers(new HashSet<>());
    }

    /**
     * get a cached participation by quizExerciseId and username
     *
     * @param quizExerciseId   the quizExerciseId of the quiz, the participation belongs to (first Key)
     * @param username the username of the user, the participation belongs to (second Key)
     * @return the participation with the given quizExerciseId and username -> return null if there is no participation -> return null if the quizExerciseId or if the username is null
     */
    @Nullable
    public StudentParticipation getParticipation(Long quizExerciseId, String username) {
        if (quizExerciseId == null || username == null) {
            return null;
        }
        return ((QuizExerciseCache) quizCache.getReadCacheFor(quizExerciseId)).getParticipations().get(username);
    }

    /**
     * get a cached quiz exercise by quizExerciseId
     *
     * @param quizExerciseId   the quizExerciseId of the quiz
     * @return the QuizExercise with the given quizExerciseId -> return null if no quiz with the given id exists
     */
    public QuizExercise getQuizExercise(Long quizExerciseId) {
        if (quizExerciseId == null) {
            return null;
        }
        QuizExercise quizExercise = ((QuizExerciseCache) quizCache.getReadCacheFor(quizExerciseId)).getExercise();
        if (quizExercise == null) {
            quizExercise = quizExerciseRepository.findOneWithQuestionsAndStatistics(quizExerciseId);
            if (quizExercise != null) {
                updateQuizExercise(quizExercise);
            }
        }
        return quizExercise;
    }

    /**
     * cache the quiz exercise for faster retrieval during the quiz
     *
     * @param quizExercise should include questions and statistics without Hibernate proxies!
     */
    public void updateQuizExercise(QuizExercise quizExercise) {
        quizCache.updateQuizExercise(quizExercise);
    }

    /**
     * Checks if the scheduler has completed processing of all submissions for a quiz exercise and all results are available in the database.
     *
     * @param quizExerciseId the id of the quiz to check
     * @return if processing of the quiz has finished
     */
    public boolean finishedProcessing(Long quizExerciseId) {
        return ((QuizExerciseCache) quizCache.getReadCacheFor(quizExerciseId)).getSubmissions().isEmpty();
    }

    /**
     * Start scheduler of quiz schedule service
     *
     * @param delayInMillis gap for which the QuizScheduleService should run repeatedly
     */
    public void startSchedule(long delayInMillis) {
        if (scheduledProcessQuizSubmissions.isNull()) {
            try {
                var scheduledFuture = threadPoolTaskScheduler.scheduleAtFixedRate(new QuizProcessCacheTask(), 0, delayInMillis, TimeUnit.MILLISECONDS);
                scheduledProcessQuizSubmissions.set(scheduledFuture.getHandler());
                log.info("QuizScheduleService was started to run repeatedly with {} second delay.", delayInMillis / 1000.0);
            }
            catch (@SuppressWarnings("unused") DuplicateTaskException e) {
                log.warn("Quiz process cache task already registered");
                // this is expected if we run on multiple nodes
            }

            // schedule quiz start for all existing quizzes that are planned to start in the future
            List<QuizExercise> quizExercises = quizExerciseRepository.findAllPlannedToStartInTheFuture();
            log.info("Found {} quiz exercises with planned start in the future", quizExercises.size());
            for (QuizExercise quizExercise : quizExercises) {
                if (quizExercise.isCourseExercise()) {
                    // only schedule quiz exercises in courses, not in exams
                    // Note: the quiz exercise does not include questions and statistics, so we pass the id
                    scheduleQuizStart(quizExercise.getId());
                }
            }
        }
        else {
            log.info("Cannot start quiz exercise schedule service, it is already RUNNING");
        }
    }

    /**
     * stop scheduler
     */
    public void stopSchedule() {
        if (!scheduledProcessQuizSubmissions.isNull()) {
            log.info("Try to stop quiz schedule service");
            var scheduledFuture = threadPoolTaskScheduler.getScheduledFuture(scheduledProcessQuizSubmissions.get());
            try {
                // if the task has been disposed, this will throw a StaleTaskException
                boolean cancelSuccess = scheduledFuture.cancel(false);
                scheduledFuture.dispose();
                scheduledProcessQuizSubmissions.set(null);
                log.info("Stop Quiz Schedule Service was successful: {}", cancelSuccess);
            }
            catch (@SuppressWarnings("unused") StaleTaskException e) {
                log.info("Stop Quiz Schedule Service already disposed/cancelled");
                // has already been disposed (sadly there is no method to check that)
            }
            for (Cache quizCache : quizCache.getAllCaches()) {
                if (((QuizExerciseCache) quizCache).getQuizStart() != null) {
                    cancelScheduledQuizStart(((QuizExerciseCache) quizCache).getExerciseId());
                }
            }
            threadPoolTaskScheduler.shutdown();
            threadPoolTaskScheduler.destroy();
        }
        else {
            log.debug("Cannot stop quiz exercise schedule service, it was already STOPPED");
        }
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
                // schedule sending out filtered quiz over websocket
                try {
                    long delay = Duration.between(ZonedDateTime.now(), quizBatch.get().getStartTime()).toMillis();
                    var scheduledFuture = threadPoolTaskScheduler.schedule(new QuizStartTask(quizExerciseId), delay, TimeUnit.MILLISECONDS);
                    // save scheduled future in HashMap
                    quizCache.performCacheWrite(quizExerciseId, quizExerciseCache -> {
                        ((QuizExerciseCache) quizExerciseCache).setQuizStart(List.of(scheduledFuture.getHandler()));
                        return quizExerciseCache;
                    });
                }
                catch (@SuppressWarnings("unused") DuplicateTaskException e) {
                    log.debug("Quiz {} task already registered", quizExerciseId);
                    // this is expected if we run on multiple nodes
                }
            }
        }
        // Do that at the end because this runs asynchronously and could interfere with the cache write above
        updateQuizExercise(quizExercise);
    }

    /**
     * cancels the quiz start for the given exercise id, e.g. because the quiz was deleted or the quiz start date was changed
     *
     * @param quizExerciseId the quiz exercise for which the quiz start should be canceled
     */
    public void cancelScheduledQuizStart(Long quizExerciseId) {
        ((QuizExerciseCache) quizCache.getReadCacheFor(quizExerciseId)).getQuizStart().forEach(taskHandler -> {
            IScheduledFuture<?> scheduledFuture = threadPoolTaskScheduler.getScheduledFuture(taskHandler);
            try {
                // if the task has been disposed, this will throw a StaleTaskException
                boolean taskNotDone = !scheduledFuture.isDone();
                boolean cancelSuccess = false;
                if (taskNotDone) {
                    cancelSuccess = scheduledFuture.cancel(false);
                }
                scheduledFuture.dispose();
                if (taskNotDone) {
                    log.info("Stop scheduled quiz start for quiz {} was successful: {}", quizExerciseId, cancelSuccess);
                }
            }
            catch (@SuppressWarnings("unused") StaleTaskException e) {
                log.info("Stop scheduled quiz start for quiz {} already disposed/cancelled", quizExerciseId);
                // has already been disposed (sadly there is no method to check that)
            }
        });
        quizCache.performCacheWriteIfPresent(quizExerciseId, cachedQuiz -> {
            ((QuizExerciseCache) cachedQuiz).setQuizStart(QuizExerciseCache.getEmptyQuizStartList());
            return cachedQuiz;
        });
    }

    /**
     * Internal method to start and send the {@link QuizExercise} to the clients when called
     */
    void executeQuizStartNowTask(Long quizExerciseId) {
        quizCache.performCacheWriteIfPresent(quizExerciseId, quizExerciseCache -> {
            ((QuizExerciseCache) quizExerciseCache).getQuizStart().clear();
            log.debug("Removed quiz {} start tasks", quizExerciseId);
            return quizExerciseCache;
        });
        log.debug("Sending quiz {} start", quizExerciseId);
        QuizExercise quizExercise = quizExerciseRepository.findByIdWithQuestionsAndStatisticsElseThrow(quizExerciseId);
        updateQuizExercise(quizExercise);
        if (quizExercise.getQuizMode() != QuizMode.SYNCHRONIZED) {
            throw new IllegalStateException();
        }

        // TODO: quiz cleanup: We create a batch that has just started here because we can't access QuizBatchService here because of dependencies
        var quizBatch = new QuizBatch();
        quizBatch.setQuizExercise(quizExercise);
        quizBatch.setStartTime(ZonedDateTime.now());
        quizExercise.setQuizBatches(Set.of(quizBatch));
        quizMessagingService.sendQuizExerciseToSubscribedClients(quizExercise, quizBatch, "start-now");
    }

    /**
     * Clears all cached quiz data for all quiz exercises for quizzes.
     * <p>
     * This will cause cached submissions, participations and results to be lost!
     */
    public void clearAllQuizData() {
        quizCache.clear();
    }

    /**
     * Clears all quiz data for one specific quiz exercise for quizzes
     * <p>
     * This will cause cached submissions, participations and results to be lost!
     * @param quizExerciseId refers to one specific quiz exercise for which the data should be cleared
     */
    public void clearQuizData(Long quizExerciseId) {
        quizCache.removeAndClear(quizExerciseId);
    }

    /**
     * // @formatter:off
     * 1. Check cached submissions for new submissions with “isSubmitted() == true”
     *      a. Process each Submission (set submissionType to “SubmissionType.MANUAL”) and create Participation and Result and save them to Database (DB WRITE)
     *      b. Remove processed Submissions from SubmissionHashMap and write Participation with Result into ParticipationHashMap and write Result into ResultHashMap
     * 2. If Quiz has ended:
     *      a. Process all cached Submissions that belong to this quiz i. set “isSubmitted” to “true” and submissionType to “SubmissionType.TIMEOUT”
     *          ii. Create Participation and Result and save to Database (DB WRITE)
     *          iii. Remove processed Submissions from cache and write the Participations with Result and the Results into the cache
     *      b. Send out cached Participations (including QuizExercise and Result) from to each participant and remove them from the cache (WEBSOCKET SEND)
     * 3. Update Statistics with Results from ResultHashMap (DB READ and DB WRITE) and remove from cache
     * 4. Send out new Statistics to instructors (WEBSOCKET SEND)
     */
    public void processCachedQuizSubmissions() {
        log.debug("Process cached quiz submissions");
        // global try-catch for error logging
        try {
            for (Cache cache : quizCache.getAllCaches()) {
                QuizExerciseCache cachedQuiz = (QuizExerciseCache) cache;
                // this way near cache is used (values will deserialize new objects)
                Long quizExerciseId = cachedQuiz.getExerciseId();
                // Get fresh QuizExercise from DB
                QuizExercise quizExercise = quizExerciseRepository.findOne(quizExerciseId);
                // check if quiz has been deleted
                if (quizExercise == null) {
                    log.debug("Remove quiz {} from resultHashMap", quizExerciseId);
                    quizCache.removeAndClear(quizExerciseId);
                    continue;
                }

                // Update cached exercise object (use the expensive operation upfront)
                quizExercise = quizExerciseRepository.findByIdWithQuestionsAndStatisticsElseThrow(quizExerciseId);
                Map<Long, QuizBatch> batchCache = quizExercise.getQuizBatches().stream().collect(Collectors.toUnmodifiableMap(QuizBatch::getId, batch -> batch));

                // ensure that attempts that were never submitted get committed to the database and saved
                // this is required to ensure that students cannot gain extra attempts this way
                for (var batch : cachedQuiz.getBatches().entrySet()) {
                    if (batchCache.get(batch.getValue()).isEnded()) {
                        cachedQuiz.getSubmissions().putIfAbsent(batch.getKey(), new QuizSubmission());
                    }
                }

                // (Boolean wrapper is safe to auto-unbox here)
                boolean hasEnded = quizExercise.isQuizEnded();
                // Note that those might not be true later on due to concurrency and a distributed system,
                // do not rely on that for actions upon the whole set, such as clear()
                boolean hasNewSubmissions = !cachedQuiz.getSubmissions().isEmpty();
                boolean hasNewParticipations = !cachedQuiz.getParticipations().isEmpty();
                boolean hasNewResults = !cachedQuiz.getResults().isEmpty();

                // Skip quizzes with no cached changes
                if (!hasNewSubmissions && !hasNewParticipations && !hasNewResults) {
                    // Remove quiz if it has ended
                    if (hasEnded) {
                        removeCachedQuiz(cachedQuiz);
                    }
                    continue;
                }

                // Save cached Submissions (this will also generate results and participations and place them in the cache)
                long start = System.nanoTime();

                // TODO avoid some distribution?

                if (hasNewSubmissions) {
                    // Create Participations and Results if the submission was submitted or if the quiz has ended and save them to Database (DB Write)
                    Map<String, QuizSubmission> submissions = cachedQuiz.getSubmissions();
                    Map<String, Long> batches = cachedQuiz.getBatches();
                    // This call will remove the processed Submission map entries itself
                    int numberOfSubmittedSubmissions = saveQuizSubmissionWithParticipationAndResultToDatabase(quizExercise, submissions, batches, batchCache);
                    // .. and likely generate new participations and results
                    if (numberOfSubmittedSubmissions > 0) {
                        // .. so we set the boolean variables here again if some were submitted
                        hasNewParticipations = true;
                        hasNewResults = true;

                        log.info("Saved {} submissions to database in {} in quiz {}", numberOfSubmittedSubmissions, formatDurationFrom(start), quizExercise.getTitle());
                    }
                }

                // Send out Participations from ParticipationHashMap to each user if the quiz has ended
                start = System.nanoTime();

                if (hasNewParticipations && hasEnded) {
                    // Send the participation with containing result and quiz back to the users via websocket and remove the participation from the ParticipationHashMap
                    Collection<Entry<String, StudentParticipation>> finishedParticipations = cachedQuiz.getParticipations().entrySet();
                    // TODO maybe find a better way to optimize the performance (use an executor service with e.g. X parallel threads)
                    finishedParticipations.parallelStream().forEach(entry -> {
                        StudentParticipation participation = entry.getValue();
                        if (participation.getParticipant() == null || participation.getParticipantIdentifier() == null) {
                            log.error("Participation is missing student (or student is missing username): {}", participation);
                        }
                        else {
                            sendQuizResultToUser(quizExerciseId, participation);
                            cachedQuiz.getParticipations().remove(entry.getKey());
                        }
                    });
                    if (!finishedParticipations.isEmpty()) {
                        log.info("Sent out {} participations in {} for quiz {}", finishedParticipations.size(), formatDurationFrom(start), quizExercise.getTitle());
                    }
                }

                // Update Statistics with Results (DB Read and DB Write) and remove the results from the cache
                start = System.nanoTime();

                if (hasNewResults) {
                    // Fetch a new quiz exercise here including deeper attribute paths (this is relatively expensive, so we only do that if necessary)
                    try {
                        // Get a Set because QuizStatisticService needs one (currently)
                        Set<Result> newResultsForQuiz = Set.copyOf(cachedQuiz.getResults().values());
                        // Update the statistics
                        quizStatisticService.updateStatistics(newResultsForQuiz, quizExercise);
                        log.info("Updated statistics with {} new results in {} for quiz {}", newResultsForQuiz.size(), formatDurationFrom(start), quizExercise.getTitle());
                        // Remove only processed results
                        for (Result result : newResultsForQuiz) {
                            cachedQuiz.getResults().remove(result.getId());
                        }
                    }
                    catch (Exception e) {
                        log.error("Exception in StatisticService.updateStatistics(): {}", e.getMessage(), e);
                    }
                }
            }
        }
        catch (Exception e) {
            log.error("Exception in Quiz Schedule: {}", e.getMessage(), e);
        }
    }

    public void joinQuizBatch(QuizExercise quizExercise, QuizBatch quizBatch, User user) {
        log.debug("join user {} to batch {} for quiz {}", user, quizBatch, quizExercise.getId());
        ((QuizExerciseCache)quizCache.getTransientWriteCacheFor(quizExercise.getId())).getBatches().put(user.getLogin(), quizBatch.getId());
    }

    public Optional<Long> getQuizBatchForStudentByLogin(QuizExercise quizExercise, String login) {
        return Optional.ofNullable(((QuizExerciseCache)quizCache.getReadCacheFor(quizExercise.getId())).getBatches().get(login));
    }

    private void removeCachedQuiz(QuizExerciseCache cachedQuiz) {
        cancelScheduledQuizStart(cachedQuiz.getExerciseId());
        quizCache.remove(cachedQuiz.getExerciseId());
    }

    private void sendQuizResultToUser(long quizExerciseId, StudentParticipation participation) {
        var user = participation.getParticipantIdentifier();
        removeUnnecessaryObjectsBeforeSendingToClient(participation);
        messagingTemplate.convertAndSendToUser(user, "/topic/exercise/" + quizExerciseId + "/participation", participation);
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
     * check if the user submitted the submission or if the quiz has ended: if true: -> Create Participation and Result and save to Database (DB Write) Remove processed Submissions
     * from SubmissionHashMap and write Participations with Result into ParticipationHashMap and Results into ResultHashMap
     *
     * @param quizExercise      the quiz which should be checked
     * @param userSubmissionMap a Map with all submissions for the given quizExercise mapped by the username
     * @param userBatchMap      a Map of the username to quiz batch id for the given quizExercise
     * @param batchCache        a Map of all the batches for the given quizExercise
     * @return                  the number of processed submissions (submit or timeout)
     */
    private int saveQuizSubmissionWithParticipationAndResultToDatabase(@NotNull QuizExercise quizExercise, Map<String, QuizSubmission> userSubmissionMap, Map<String, Long> userBatchMap, Map<Long, QuizBatch> batchCache) {

        int count = 0;

        for (String username : userSubmissionMap.keySet()) {
            try {
                // first case: the user submitted the quizSubmission
                QuizSubmission quizSubmission = userSubmissionMap.get(username);
                QuizBatch quizBatch = batchCache.get(userBatchMap.getOrDefault(username, 0L));
                if (quizSubmission.isSubmitted()) {
                    if (quizSubmission.getType() == null) {
                        quizSubmission.setType(SubmissionType.MANUAL);
                    }
                } // second case: the quiz or batch has ended
                else if (quizExercise.isQuizEnded() || quizBatch != null && quizBatch.isEnded()) {
                    quizSubmission.setSubmitted(true);
                    quizSubmission.setType(SubmissionType.TIMEOUT);
                    quizSubmission.setSubmissionDate(ZonedDateTime.now());
                }
                else {
                    // the quiz is running and the submission was not yet submitted.
                    continue;
                }

                if (quizBatch != null) {
                    // record which batch the submission belongs to
                    quizSubmission.setQuizBatch(quizBatch.getId());
                }

                count++;
                // Create Participation and Result and save to Database (DB Write)
                // Remove processed Submissions from SubmissionHashMap and write Participations with Result into ParticipationHashMap and Results into ResultHashMap

                StudentParticipation participation = new StudentParticipation();
                // TODO: when this is set earlier for the individual quiz start of a student, we don't need to set this here anymore
                participation.setInitializationDate(quizSubmission.getSubmissionDate());
                Optional<User> user = userRepository.findOneByLogin(username);
                user.ifPresent(participation::setParticipant);
                // add the quizExercise to the participation
                participation.setExercise(quizExercise);
                participation.setInitializationState(InitializationState.FINISHED);

                // create new result
                Result result = new Result().participation(participation);
                result.setRated(true);
                result.setAssessmentType(AssessmentType.AUTOMATIC);
                result.setCompletionDate(quizSubmission.getSubmissionDate());
                result.setSubmission(quizSubmission);

                // calculate scores and update result and submission accordingly
                quizSubmission.calculateAndUpdateScores(quizExercise);
                result.evaluateQuizSubmission();

                // add result to participation
                participation.addResult(result);

                // add submission to participation
                participation.setSubmissions(Set.of(quizSubmission));

                // NOTE: we save (1) participation and (2) submission (in this particular order) here individually so that one exception (e.g. duplicated key) cannot
                // destroy multiple student answers
                participation = studentParticipationRepository.save(participation);
                quizSubmission.addResult(result);
                quizSubmission.setParticipation(participation);
                // this automatically saves the results due to CascadeType.ALL
                quizSubmission = quizSubmissionRepository.save(quizSubmission);

                log.info("Successfully saved submission in quiz " + quizExercise.getTitle() + " for user " + username);

                // reconnect entities after save
                participation.setSubmissions(Set.of(quizSubmission));
                participation.setResults(Set.of(result));
                result.setSubmission(quizSubmission);
                result.setParticipation(participation);

                // no point in keeping the participation around for non-synchronized modes where the due date may only be in a week
                if (quizExercise.getQuizMode() == QuizMode.SYNCHRONIZED) {
                    // add the participation to the participationHashMap for the send out at the end of the quiz
                    addParticipation(quizExercise.getId(), participation);
                }

                // remove the submission only after the participation has been added to the participation hashmap to avoid duplicated key exceptions for multiple participations for
                // the same user
                userSubmissionMap.remove(username);
                // clean up the batch association
                userBatchMap.remove(username);

                // add the result of the participation resultHashMap for the statistic-Update
                addResultForStatisticUpdate(quizExercise.getId(), result);
            }
            catch (ConstraintViolationException constraintViolationException) {
                log.error("ConstraintViolationException in saveQuizSubmissionWithParticipationAndResultToDatabase() for user {} in quiz {}: {}", username, quizExercise.getId(), constraintViolationException.getMessage(), constraintViolationException);
                // We got a ConstraintViolationException -> The "User-Quiz" pair is already saved in the database, but for some reason was not removed from the maps
                // We remove it from the maps now to prevent this error from occurring again
                // We do NOT add it to the participation map, as this should have been done already earlier (when the entry was added to the database)

                userSubmissionMap.remove(username);

                // clean up the batch association
                userBatchMap.remove(username);
            }
            catch (Exception e) {
                log.error("Exception in saveQuizSubmissionWithParticipationAndResultToDatabase() for user {} in quiz {}: {}", username, quizExercise.getId(), e.getMessage(), e);
            }
        }
        return count;
    }
}
