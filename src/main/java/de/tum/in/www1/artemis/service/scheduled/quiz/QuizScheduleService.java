package de.tum.in.www1.artemis.service.scheduled.quiz;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

import com.hazelcast.config.*;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.scheduledexecutor.*;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.SubmittedAnswer;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizSubmission;
import de.tum.in.www1.artemis.repository.QuizSubmissionRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.service.QuizExerciseService;
import de.tum.in.www1.artemis.service.QuizStatisticService;
import de.tum.in.www1.artemis.service.UserService;

@Service
public class QuizScheduleService {

    static final Logger log = LoggerFactory.getLogger(QuizScheduleService.class);

    static final String HAZELCAST_CACHE_PARTICIPATIONS = "-participations";

    static final String HAZELCAST_CACHE_SUBMISSIONS = "-submissions";

    static final String HAZELCAST_CACHE_RESULTS = "-results";

    private static final String HAZELCAST_QUIZ_START_TASK = "-start";

    private static final String HAZELCAST_PROCESS_CACHE_TASK = Constants.HAZELCAST_QUIZ_PREFIX + "process-cache";

    /**
     * Mainly for testing, default is false
     * <p>
     * (using local cache only does not seems to provide any visible performance benefits)
     */
    private static final boolean USE_LOCAL_CACHE_ONLY = false;

    private ConcurrentMap<Long, QuizExerciseCache> cachedQuizExercises;

    private IScheduledExecutorService threadPoolTaskScheduler;

    private IScheduledFuture<?> scheduledProcessQuizSubmissions;

    private final StudentParticipationRepository studentParticipationRepository;

    private final ResultRepository resultRepository;

    private final UserService userService;

    private final QuizSubmissionRepository quizSubmissionRepository;

    private QuizExerciseService quizExerciseService;

    private QuizStatisticService quizStatisticService;

    private SimpMessageSendingOperations messagingTemplate;

    private HazelcastInstance hazelcastInstance;

    public QuizScheduleService(SimpMessageSendingOperations messagingTemplate, StudentParticipationRepository studentParticipationRepository, ResultRepository resultRepository,
            UserService userService, QuizSubmissionRepository quizSubmissionRepository, HazelcastInstance hazelcastInstance) {
        this.messagingTemplate = messagingTemplate;
        this.studentParticipationRepository = studentParticipationRepository;
        this.resultRepository = resultRepository;
        this.userService = userService;
        this.quizSubmissionRepository = quizSubmissionRepository;
        this.hazelcastInstance = hazelcastInstance;
        if (USE_LOCAL_CACHE_ONLY) {
            cachedQuizExercises = new ConcurrentHashMap<>();
        }
        else {
            cachedQuizExercises = hazelcastInstance.getMap(Constants.HAZELCAST_EXERCISE_CACHE);
        }
    }

    /**
     * Configures Hazelcast for the QuizScheduleService before the HazelcastInstance is created.
     *
     * @param config the {@link Config} the QuizScheduleService-specific configuration should be added to
     */
    public static void configureHazelcast(Config config) {
        QuizExerciseCache.registerSerializers(config);
        // Pool size default 16, increased capacity (as we could have many quizzes) and default durability for now
        config.getScheduledExecutorConfig(Constants.HAZELCAST_QUIZ_SCHEDULER).setPoolSize(16).setCapacity(1000).setDurability(1);
        // Important to avoid continuous serialization and de-serialization and the implications on transient fields of QuizExerciseCache
        EvictionConfig evictionConfig = new EvictionConfig() //
                .setMaxSizePolicy(MaxSizePolicy.ENTRY_COUNT) //
                .setEvictionPolicy(EvictionPolicy.LRU) //
                .setSize(1000);

        NearCacheConfig nearCacheConfig = new NearCacheConfig() //
                .setName(Constants.HAZELCAST_EXERCISE_CACHE + "-local") //
                .setInMemoryFormat(InMemoryFormat.OBJECT) //
                .setSerializeKeys(true) //
                .setInvalidateOnChange(true) //
                .setTimeToLiveSeconds(3600) //
                .setMaxIdleSeconds(600) //
                .setEvictionConfig(evictionConfig) //
                .setCacheLocalEntries(true) //
                .setLocalUpdatePolicy(NearCacheConfig.LocalUpdatePolicy.INVALIDATE);
        // .setPreloaderConfig(preloaderConfig);
        config.getMapConfig(Constants.HAZELCAST_EXERCISE_CACHE).setNearCacheConfig(nearCacheConfig);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void applicationReady() {
        // activate Quiz Schedule Service
        startSchedule(5 * 1000);                          // every 5 seconds
    }

    @Autowired
    // break the dependency cycle
    public void setQuizExerciseService(QuizExerciseService quizExerciseService) {
        this.quizExerciseService = quizExerciseService;
    }

    @Autowired
    // break the dependency cycle
    public void setQuizStatisticService(QuizStatisticService quizStatisticService) {
        this.quizStatisticService = quizStatisticService;
    }

    /*
     * FIXME DOCUMENTATION of public methods!!!
     */

    /**
     * Only for reading from QuizExerciseCache
     *
     * @param quizExerciseId the id of the quiz exercise, must not be null
     */
    private QuizExerciseCache getReadCacheFor(Long quizExerciseId) {
        return cachedQuizExercises.getOrDefault(quizExerciseId, QuizExerciseCache.empty());
    }

    /**
     * Only for the modification of transient properties, e.g. the exercise and the maps.
     * <p>
     * Creates new QuizExerciseCache if required.
     *
     * @param quizExerciseId the id of the quiz exercise, must not be null
     */
    private QuizExerciseCache getTransientWriteCacheFor(Long quizExerciseId) {
        return cachedQuizExercises.computeIfAbsent(quizExerciseId, id -> {
            if (USE_LOCAL_CACHE_ONLY) {
                return new QuizExerciseLocalCache(id);
            }
            QuizExerciseDistibutedCache cachedQuiz = new QuizExerciseDistibutedCache(id);
            cachedQuiz.setHazelcastInstance(hazelcastInstance);
            return cachedQuiz;
        });
    }

    /**
     * To perform the given action on QuizExerciseCache non-transient fields.
     * <p>
     * Creates new QuizExerciseCache if required.
     *
     * @param quizExerciseId the id of the quiz exercise, must not be null
     * @param writeOperation gets non-null and has  to return non-null.
     */
    private void performCacheWrite(Long quizExerciseId, UnaryOperator<QuizExerciseCache> writeOperation) {
        log.info("Write quiz cache {}", quizExerciseId);
        cachedQuizExercises.put(quizExerciseId, writeOperation.apply(getTransientWriteCacheFor(quizExerciseId)));
    }

    /**
     * To perform the given action on QuizExerciseCache non-transient fields.
     * <p>
     * Will not execute the <code>writeOperation</code> if no QuizExerciseCache exists for the given id.
     *
     * @param quizExerciseId the id of the quiz exercise, must not be null
     * @param writeOperation gets non-null and has  to return non-null.
     */
    private void performCacheWriteIfPresent(Long quizExerciseId, UnaryOperator<QuizExerciseCache> writeOperation) {
        QuizExerciseCache cachedQuiz = getReadCacheFor(quizExerciseId);
        if (!cachedQuiz.isDummy()) {
            log.info("Write quiz cache {}", quizExerciseId);
            cachedQuizExercises.put(quizExerciseId, writeOperation.apply(cachedQuiz));
        }
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
            getTransientWriteCacheFor(quizExerciseId).getSubmissions().put(username, quizSubmission);
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
        log.debug("add result for statistic update for quiz " + quizExerciseId + ": " + result);
        if (quizExerciseId != null && result != null) {
            getTransientWriteCacheFor(quizExerciseId).getResults().put(result.getId(), result);
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
            getTransientWriteCacheFor(quizExerciseId).getParticipations().put(participation.getParticipantIdentifier(), participation);
        }
    }

    /**
     * get a quizSubmission from the submissionHashMap by quizExerciseId and username
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
        QuizSubmission quizSubmission = getReadCacheFor(quizExerciseId).getSubmissions().get(username);
        if (quizSubmission != null) {
            return quizSubmission;
        }
        // return an empty quizSubmission if the maps contain no mapping for the keys
        return new QuizSubmission().submittedAnswers(new HashSet<>());
    }

    /**
     * get a participation from the participationHashMap by quizExerciseId and username
     *
     * @param quizExerciseId   the quizExerciseId of the quiz, the participation belongs to (first Key)
     * @param username the username of the user, the participation belongs to (second Key)
     * @return the participation with the given quizExerciseId and username -> return null if there is no participation -> return null if the quizExerciseId or if the username is null
     */
    public StudentParticipation getParticipation(Long quizExerciseId, String username) {
        if (quizExerciseId == null || username == null) {
            return null;
        }
        return getReadCacheFor(quizExerciseId).getParticipations().get(username);
    }

    public QuizExercise getQuizExercise(Long quizExerciseId) {
        if (quizExerciseId == null) {
            return null;
        }
        return getReadCacheFor(quizExerciseId).getExercise();
    }

    /**
     * stores the quiz exercise in a HashMap for faster retrieval during the quiz
     * @param quizExercise should include questions and statistics without Hibernate proxies!
     */
    public void updateQuizExercise(QuizExercise quizExercise) {
        log.debug("Quiz exercise {} updated in quiz exercise map: {}", quizExercise.getId(), quizExercise);
        getTransientWriteCacheFor(quizExercise.getId()).setExercise(quizExercise);
    }

    /**
     * Start scheduler of quiz schedule service
     *
     * @param delayInMillis gap for which the QuizScheduleService should run repeatedly
     */
    public void startSchedule(long delayInMillis) {
        if (threadPoolTaskScheduler == null) {
            try {
                threadPoolTaskScheduler = hazelcastInstance.getScheduledExecutorService(Constants.HAZELCAST_QUIZ_SCHEDULER);
                scheduledProcessQuizSubmissions = threadPoolTaskScheduler.scheduleAtFixedRate(TaskUtils.named(HAZELCAST_PROCESS_CACHE_TASK, new QuizCacheTask()), 0, delayInMillis,
                        TimeUnit.MILLISECONDS);
                log.info("QuizScheduleService was started to run repeatedly with {} second delay.", delayInMillis / 1000.0);
            }
            catch (@SuppressWarnings("unused") DuplicateTaskException e) {
                log.debug("Quiz process cache task already redistered");
                // this is expected if we run on multiple nodes
            }

            // schedule quiz start for all existing quizzes that are planned to start in the future
            List<QuizExercise> quizExercises = quizExerciseService.findAllPlannedToStartInTheFuture();
            log.info("Found {} quiz exercises with planned start in the future", quizExercises.size());
            for (QuizExercise quizExercise : quizExercises) {
                // Note: the quiz exercise does not include questions and statistics, so we pass the id
                scheduleQuizStart(quizExercise.getId());
            }
        }
        else {
            log.debug("Cannot start quiz exercise schedule service, it is already RUNNING");
        }
    }

    /**
     * stop scheduler (interrupts if running)
     */
    public void stopSchedule() {
        if (threadPoolTaskScheduler != null) {
            log.info("Try to stop quiz schedule service");

            if (scheduledProcessQuizSubmissions != null && !scheduledProcessQuizSubmissions.isCancelled()) {
                boolean cancelSuccess = scheduledProcessQuizSubmissions.cancel(false);
                log.info("Stop Quiz Schedule Service was successful: " + cancelSuccess);
                scheduledProcessQuizSubmissions.dispose();
                scheduledProcessQuizSubmissions = null;
            }
            for (QuizExerciseCache cachedQuiz : cachedQuizExercises.values()) {
                if (cachedQuiz.getQuizStart() != null)
                    cancelScheduledQuizStart(cachedQuiz.getId());
            }
            threadPoolTaskScheduler.shutdown();
            threadPoolTaskScheduler.destroy();
            threadPoolTaskScheduler = null;
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
        final var quizExercise = quizExerciseService.findOneWithQuestionsAndStatistics(quizExerciseId);
        updateQuizExercise(quizExercise);

        if (quizExercise.isIsPlannedToStart() && quizExercise.getReleaseDate().isAfter(ZonedDateTime.now())) {
            // schedule sending out filtered quiz over websocket
            try {
                long delay = Duration.between(ZonedDateTime.now(), quizExercise.getReleaseDate()).toMillis();
                var scheduledFuture = threadPoolTaskScheduler.schedule(
                        TaskUtils.named(Constants.HAZELCAST_QUIZ_PREFIX + quizExerciseId + HAZELCAST_QUIZ_START_TASK, new QuizStartTask(quizExerciseId)), delay,
                        TimeUnit.MILLISECONDS);
                // save scheduled future in HashMap
                performCacheWrite(quizExercise.getId(), quizExerciseCache -> {
                    quizExerciseCache.setQuizStart(List.of(scheduledFuture.getHandler()));
                    return quizExerciseCache;
                });
            }
            catch (@SuppressWarnings("unused") DuplicateTaskException e) {
                log.debug("Quiz {} task already redistered", quizExerciseId);
                // this is expected if we run on multiple nodes
            }
        }
    }

    /**
     * cancels the quiz start for the given exercise id, e.g. because the quiz was deleted or the quiz start date was changed
     *
     * @param quizExerciseId the quiz exercise for which the quiz start should be canceled
     */
    public void cancelScheduledQuizStart(Long quizExerciseId) {
        getReadCacheFor(quizExerciseId).getQuizStart().forEach(taskHandler -> {
            IScheduledFuture<?> scheduledFuture = threadPoolTaskScheduler.getScheduledFuture(taskHandler);
            if (scheduledFuture != null) {
                if (!scheduledFuture.isCancelled() && !scheduledFuture.isDone()) {
                    boolean cancelSuccess = scheduledFuture.cancel(true);
                    log.info("Stop scheduled quiz start for quiz " + quizExerciseId + " was successful: " + cancelSuccess);
                }
                scheduledFuture.dispose();
            }
        });
        performCacheWriteIfPresent(quizExerciseId, cachedQuiz -> {
            cachedQuiz.setQuizStart(QuizExerciseCache.getEmptyQuizStartList());
            return cachedQuiz;
        });
    }

    /*
     * Clears all quiz data for all quiz exercises from the 4 hash maps for quizzes
     */
    public void clearAllQuizData() {
        cachedQuizExercises.values().forEach(QuizExerciseCache::destroy);
        cachedQuizExercises.clear();
    }

    /**
     * Clears all quiz data for one specific quiz exercise from the 4 hash maps for quizzes
     * @param quizExerciseId refers to one specific quiz exercise for which the data should be cleared
     */
    public void clearQuizData(Long quizExerciseId) {
        QuizExerciseCache quizCache = cachedQuizExercises.remove(quizExerciseId);
        if (quizCache != null) {
            quizCache.destroy();
        }
    }

    /**
     * // @formatter:off
     * 1. Check SubmissionHashMap for new submissions with “isSubmitted() == true”
     *      a. Process each Submission (set submissionType to “SubmissionType.MANUAL”) and create Participation and Result and save them to Database (DB WRITE)
     *      b. Remove processed Submissions from SubmissionHashMap and write Participation with Result into ParticipationHashMap and write Result into ResultHashMap
     * 2. If Quiz has ended:
     *      a. Process all Submissions in SubmissionHashMap that belong to this quiz i. set “isSubmitted”   to “true” and submissionType to “SubmissionType.TIMEOUT”
     *          ii. Create Participation and Result and save to Database (DB WRITE)
     *          iii. Remove processed Submissions from SubmissionHashMap and write Participations with Result into ParticipationHashMap and Results into ResultHashMap
     *      b. Send out Participations (including QuizExercise and Result) from ParticipationHashMap to each participant and remove them from ParticipationHashMap (WEBSOCKET SEND)
     * 3. Update Statistics with Results from ResultHashMap (DB READ and DB WRITE) and remove from ResultHashMap
     * 4. Send out new Statistics to instructors (WEBSOCKET SEND)
     */
    public void processCachedQuizSubmissions() {
        log.debug("Process cached quiz submissions");
        // global try-catch for error logging
        try {
            for (QuizExerciseCache cachedQuiz : cachedQuizExercises.values()) {
                // Get fresh QuizExercise from DB
                QuizExercise quizExercise = quizExerciseService.findOne(cachedQuiz.getId());
                // check if quiz has been deleted
                if (quizExercise == null) {
                    log.debug("Remove quiz " + cachedQuiz.getId() + " from resultHashMap");
                    cachedQuizExercises.remove(cachedQuiz.getId());
                    cachedQuiz.destroy();
                    continue;
                }

                // (Boolean wrapper is safe to auto-unbox here)
                boolean hasEnded = quizExercise.isEnded();
                // Note that those might not be true later on due to concurrency and a distributed system,
                // do not rely on that for actions upon the whole set, such as clear()
                boolean hasNewSubmissions = !cachedQuiz.getSubmissions().isEmpty();
                boolean hasNewParticipations = !cachedQuiz.getParticipations().isEmpty();
                boolean hasNewResults = !cachedQuiz.getResults().isEmpty();

                // Skip quizzes with no cached changes
                if (!hasNewSubmissions && !hasNewParticipations && !hasNewResults) {
                    // Remove quiz if it is not scheduled for start
                    if (hasEnded && cachedQuiz.getQuizStart().isEmpty()) {
                        cachedQuizExercises.remove(cachedQuiz.getId(), cachedQuiz);
                    }
                    continue;
                }

                // Update cached exercise object
                quizExercise = quizExerciseService.findOneWithQuestions(cachedQuiz.getId());
                cachedQuiz.setExercise(quizExercise);

                // Save cached Submissions (this will also generate results and participations and place them in the cache)
                long start = System.nanoTime();

                // TODO avoid distribution?

                if (hasNewSubmissions) {
                    // Create Participations and Results if the submission was submitted or if the quiz has ended and save them to Database (DB Write)
                    Map<String, QuizSubmission> submissions = cachedQuiz.getSubmissions();
                    // This call will remove the processed Submission map entries itself
                    int numberOfSubmittedSubmissions = saveQuizSubmissionWithParticipationAndResultToDatabase(quizExercise, submissions);
                    // .. and likely generate new participations and results
                    if (numberOfSubmittedSubmissions > 0) {
                        // .. so we set the boolean variables here again if some were submitted
                        hasNewParticipations = true;
                        hasNewResults = true;

                        log.info("Saved {} submissions to database in {} in quiz {}", numberOfSubmittedSubmissions, printDuration(start), quizExercise.getTitle());
                    }
                }

                // Send out Participations from ParticipationHashMap to each user if the quiz has ended
                start = System.nanoTime();

                if (hasNewParticipations && hasEnded) {
                    // Send the participation with containing result and quiz back to the users via websocket and remove the participation from the ParticipationHashMap
                    Collection<Entry<String, StudentParticipation>> finishedParticipations = cachedQuiz.getParticipations().entrySet();
                    // TODO maybe find a better way to optimize the performance
                    finishedParticipations.parallelStream().forEach(entry -> {
                        StudentParticipation participation = entry.getValue();
                        if (participation.getParticipant() == null || participation.getParticipantIdentifier() == null) {
                            log.error("Participation is missing student (or student is missing username): {}", participation);
                        }
                        else {
                            sendQuizResultToUser(cachedQuiz.getId(), participation);
                            cachedQuiz.getParticipations().remove(entry.getKey());
                        }
                    });
                    if (finishedParticipations.size() > 0) {
                        log.info("Sent out {} participations in {} for quiz {}", finishedParticipations.size(), printDuration(start), quizExercise.getTitle());
                    }
                }

                // Update Statistics with Results (DB Read and DB Write) and remove the results from the cache
                start = System.nanoTime();

                if (hasNewResults) {
                    // Fetch a new quiz exercise here including deeper attribute paths (this is relatively expensive, so we only do that if necessary)
                    quizExercise = quizExerciseService.findOneWithQuestionsAndStatistics(cachedQuiz.getId());
                    try {
                        // Get a Set because QuizStatisticService needs one (currently)
                        Set<Result> newResultsForQuiz = Set.copyOf(cachedQuiz.getResults().values());
                        // Update the statistics
                        quizStatisticService.updateStatistics(newResultsForQuiz, quizExercise);
                        log.info("Updated statistics with {} new results in {} for quiz {}", newResultsForQuiz.size(), printDuration(start), quizExercise.getTitle());
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

    void executeQuizStartTask(Long quizExerciseId) {
        performCacheWriteIfPresent(quizExerciseId, quizExerciseCache -> {
            quizExerciseCache.getQuizStart().clear();
            log.debug("Removed quiz {} start tasks", quizExerciseId);
            return quizExerciseCache;
        });
        log.debug("Sending quiz {} start", quizExerciseId);
        QuizExercise quizExercise = quizExerciseService.findOneWithQuestionsAndStatistics(quizExerciseId);
        quizExerciseService.sendQuizExerciseToSubscribedClients(quizExercise, "start-now");
    }

    private static String printDuration(long timeNanoStart) {
        long durationInMicroSeconds = (System.nanoTime() - timeNanoStart) / 1000;
        if (durationInMicroSeconds > 1000) {
            double durationInMilliSeconds = durationInMicroSeconds / 1000.0;
            if (durationInMilliSeconds > 1000) {
                double durationInSeconds = durationInMilliSeconds / 1000.0;
                return roundOffTo2DecPlaces(durationInSeconds) + "s";
            }
            return roundOffTo2DecPlaces(durationInMilliSeconds) + "ms";
        }
        return durationInMicroSeconds + "µs";
    }

    private static String roundOffTo2DecPlaces(double val) {
        return String.format("%.2f", val);
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
        if (participation.getResults() != null && participation.getResults().size() > 0) {
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
     * @return                  the number of processed submissions (submit or timeout)
     */
    private int saveQuizSubmissionWithParticipationAndResultToDatabase(@NotNull QuizExercise quizExercise, Map<String, QuizSubmission> userSubmissionMap) {

        int count = 0;

        for (String username : userSubmissionMap.keySet()) {
            try {
                // first case: the user submitted the quizSubmission
                QuizSubmission quizSubmission = userSubmissionMap.get(username);
                if (quizSubmission.isSubmitted()) {
                    if (quizSubmission.getType() == null) {
                        quizSubmission.setType(SubmissionType.MANUAL);
                    }
                } // second case: the quiz has ended
                else if (quizExercise.isEnded()) {
                    quizSubmission.setSubmitted(true);
                    quizSubmission.setType(SubmissionType.TIMEOUT);
                    quizSubmission.setSubmissionDate(ZonedDateTime.now());
                }
                else {
                    // the quiz is running and the submission was not yet submitted.
                    continue;
                }

                count++;
                // Create Participation and Result and save to Database (DB Write)
                // Remove processed Submissions from SubmissionHashMap and write Participations with Result into ParticipationHashMap and Results into ResultHashMap

                StudentParticipation participation = new StudentParticipation();
                // TODO: when this is set earlier for the individual quiz start of a student, we don't need to set this here anymore
                participation.setInitializationDate(quizSubmission.getSubmissionDate());
                Optional<User> user = userService.getUserByLogin(username);
                user.ifPresent(participation::setParticipant);
                // add the quizExercise to the participation
                participation.setExercise(quizExercise);
                participation.setInitializationState(InitializationState.FINISHED);

                // create new result
                Result result = new Result().participation(participation).submission(quizSubmission);
                result.setRated(true);
                result.setAssessmentType(AssessmentType.AUTOMATIC);
                result.setCompletionDate(quizSubmission.getSubmissionDate());
                result.setSubmission(quizSubmission);

                // calculate scores and update result and submission accordingly
                quizSubmission.calculateAndUpdateScores(quizExercise);
                result.evaluateSubmission();

                // add result to participation
                participation.addResult(result);

                // add submission to participation
                participation.addSubmissions(quizSubmission);

                // NOTE: we save participation, submission and result here individually so that one exception (e.g. duplicated key) cannot destroy multiple student answers
                participation = studentParticipationRepository.save(participation);
                quizSubmissionRepository.save(quizSubmission);
                result = resultRepository.save(result);

                // add the participation to the participationHashMap for the send out at the end of the quiz
                addParticipation(quizExercise.getId(), participation);

                // remove the submission only after the participation has been added to the participation hashmap to avoid duplicated key exceptions for multiple participations for
                // the same user
                userSubmissionMap.remove(username);

                // add the result of the participation resultHashMap for the statistic-Update
                addResultForStatisticUpdate(quizExercise.getId(), result);
            }
            catch (Exception e) {
                log.error("Exception in saveQuizSubmissionWithParticipationAndResultToDatabase() for user {} in quiz {}: {}", username, quizExercise.getId(), e.getMessage(), e);
            }
        }

        return count;
    }
}
