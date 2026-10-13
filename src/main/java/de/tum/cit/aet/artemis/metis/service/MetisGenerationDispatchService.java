package de.tum.cit.aet.artemis.metis.service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.util.TimeUtil;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.metis.config.MetisConfiguration;
import de.tum.cit.aet.artemis.metis.config.MetisEnabled;
import de.tum.cit.aet.artemis.metis.config.MetisProperties;
import de.tum.cit.aet.artemis.metis.domain.MetisCheck;
import de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus;
import de.tum.cit.aet.artemis.metis.domain.MetisFailureReason;
import de.tum.cit.aet.artemis.metis.dto.MetisActiveExerciseDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisCheckCandidateDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisCheckOwnerDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisExerciseFactsDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisGenerateResultDTO;
import de.tum.cit.aet.artemis.metis.repository.MetisCheckRepository;
import de.tum.cit.aet.artemis.metis.repository.MetisExerciseConfigRepository;
import de.tum.cit.aet.artemis.metis.repository.MetisQuestionRepository;
import de.tum.cit.aet.artemis.metis.service.MetisAnswerRatingService.BackfillOutcome;
import de.tum.cit.aet.artemis.metis.service.MetisQuestionGenerationService.Outcome;

/**
 * Decides which comprehension checks exist and when their questions are generated, and hands the work to the Metis executor.
 * <p>
 * The scheduling node calls {@link #dispatch(ZonedDateTime)} on every tick of its poller. A tick first sweeps the exercises with an enabled check whose dates
 * still lie within the horizon: it puts checks whose effective due date moved into the future again back into the queue (an extension), creates the missing
 * checks of every eligible participation whose effective due date has passed, and puts checks that were skipped for missing consent back into the queue once
 * the student consents. Checks of an exercise are only created once the node has seen its configuration enabled for {@code enable-delay-minutes}, so a
 * settings form that is still being filled in after the due date does not go live; exercises that are enabled when the node starts count as settled.
 * <p>
 * Then it claims work for the free slots of the executor ({@code max-concurrent-tasks}): first answers whose live rating failed and that are old enough for the
 * backfill, then the pending checks, the earliest student window first. A finishing task claims the next work itself, so the slots never wait for the next
 * tick while work is left. An answer whose backfill rating failed waits a few minutes before its next attempt. No work is claimed while the endpoint is not
 * configured, while a circuit breaker pauses after the endpoint was unavailable (60 seconds, doubling up to 15 minutes), or while live ratings on this node
 * wait for a permit of the LLM.
 * <p>
 * The state kept here (the slots, the circuit, the failure counters and when an exercise was first seen enabled) is meaningful on the scheduling node only
 * and lives in memory: after a restart, {@link #resetAfterStartup()} puts the checks a crash left in generation back into the queue and starts afresh.
 * {@link #generateNow(long)}, which any node may serve, only inserts and requeues checks and never generates itself.
 */
@Conditional(MetisEnabled.class)
@Lazy
@Service
public class MetisGenerationDispatchService {

    private static final Logger log = LoggerFactory.getLogger(MetisGenerationDispatchService.class);

    /** The pause of the circuit breaker after the first failure of the endpoint; it doubles with every further failure. */
    static final Duration INITIAL_BACKOFF = Duration.ofSeconds(60);

    /** The longest pause of the circuit breaker. */
    static final Duration MAX_BACKOFF = Duration.ofMinutes(15);

    /** After this many failed ratings of one stored answer, the backfill gives up on it until the next restart. */
    static final int MAX_RATING_FAILURES = 3;

    /**
     * How long the backfill waits before it rates an answer again whose rating failed. Without the wait, the claim that a finishing task chains would pick the
     * same answer at once, and all attempts would follow each other within seconds.
     */
    static final Duration RATING_RETRY_DELAY = Duration.ofMinutes(5);

    /** How many claimable checks one claim reads; it claims the first one it wins against other claimers. */
    private static final int CLAIM_CANDIDATES = 3;

    /** How often the dispatcher repeats the warning that the endpoint is not configured. */
    private static final Duration NOT_CONFIGURED_WARNING_INTERVAL = Duration.ofHours(1);

    /** An id that no question has, so that the list of excluded questions of the backfill query is never empty. */
    private static final long NO_QUESTION_ID = -1L;

    /** When an exercise that was enabled before the node started counts as first seen enabled: long enough ago to be settled. */
    private static final ZonedDateTime SETTLED_SINCE_STARTUP = ZonedDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC);

    private static final String ENTITY_NAME = "metis";

    private final MetisProperties properties;

    private final MetisExerciseConfigRepository configRepository;

    private final MetisCheckRepository checkRepository;

    private final MetisQuestionRepository questionRepository;

    private final MetisAiConsentService consentService;

    private final MetisLlmService llmService;

    private final MetisQuestionGenerationService generationService;

    private final MetisAnswerRatingService ratingService;

    private final Executor executor;

    /** The reserved slots of the executor: tasks that are queued or running, plus a claim that is about to submit one. */
    private final AtomicInteger inFlight = new AtomicInteger();

    /** The checks whose generation task is queued or running on this node. */
    private final Set<Long> checksInFlight = ConcurrentHashMap.newKeySet();

    /** The questions whose backfill rating is queued or running on this node. */
    private final Set<Long> ratingsInFlight = ConcurrentHashMap.newKeySet();

    /** The questions whose answer the backfill does not rate again before the next restart: no consent, or too many failed ratings. */
    private final Set<Long> ratingsGivenUp = ConcurrentHashMap.newKeySet();

    /** The generation attempts per check that ended with an unavailable endpoint. */
    private final Map<Long, Integer> transientFailures = new ConcurrentHashMap<>();

    /** The failed backfill ratings per question. */
    private final Map<Long, Integer> ratingFailures = new ConcurrentHashMap<>();

    /** When the backfill may rate an answer again whose last rating failed; an entry whose time has come is dropped by the next claim. */
    private final Map<Long, ZonedDateTime> ratingRetryAfter = new ConcurrentHashMap<>();

    /** When this node first saw the configuration of an exercise enabled; checks are created once that is {@code enable-delay-minutes} ago. */
    private final Map<Long, ZonedDateTime> firstSeenEnabled = new ConcurrentHashMap<>();

    /** Whether the next sweep is the first since the node started, which counts every enabled exercise as settled. */
    private volatile boolean startupSweep = true;

    /** Until when the circuit breaker pauses claims, or null while it is closed. Guarded by {@code this}. */
    @Nullable
    private ZonedDateTime pausedUntil;

    /** The pause of the next opening of the circuit breaker. Guarded by {@code this}. */
    private Duration backoff = INITIAL_BACKOFF;

    /** When the dispatcher last warned that the endpoint is not configured, or null if it never did. Guarded by {@code this}. */
    @Nullable
    private ZonedDateTime lastNotConfiguredWarning;

    public MetisGenerationDispatchService(MetisProperties properties, MetisExerciseConfigRepository configRepository, MetisCheckRepository checkRepository,
            MetisQuestionRepository questionRepository, MetisAiConsentService consentService, MetisLlmService llmService, MetisQuestionGenerationService generationService,
            MetisAnswerRatingService ratingService, @Qualifier(MetisConfiguration.METIS_TASK_EXECUTOR) Executor executor) {
        this.properties = properties;
        this.configRepository = configRepository;
        this.checkRepository = checkRepository;
        this.questionRepository = questionRepository;
        this.consentService = consentService;
        this.llmService = llmService;
        this.generationService = generationService;
        this.ratingService = ratingService;
        this.executor = executor;
    }

    /**
     * One tick of the poller on the scheduling node: the sweeps of the active exercises, then the claims. Tests call it directly with the time they froze.
     *
     * @param now the current time; every comparison of this tick uses it
     */
    public void dispatch(ZonedDateTime now) {
        ZonedDateTime cutoff = now.minusSeconds(properties.generation().delayAfterDueDateSeconds());
        List<MetisActiveExerciseDTO> activeExercises = configRepository.findActiveExercises(now.minusDays(properties.generation().horizonDays()));
        Set<Long> activeExerciseIds = new HashSet<>();
        for (MetisActiveExerciseDTO exercise : activeExercises) {
            activeExerciseIds.add(exercise.exerciseId());
            try {
                sweep(exercise, now, cutoff);
            }
            catch (RuntimeException e) {
                log.error("The Metis sweep of exercise {} failed", exercise.exerciseId(), e);
            }
        }
        startupSweep = false;
        // An exercise that was switched off and on again waits for the delay again
        firstSeenEnabled.keySet().retainAll(activeExerciseIds);
        if (!activeExerciseIds.isEmpty()) {
            try {
                requeueConsentingSkips(activeExerciseIds);
            }
            catch (RuntimeException e) {
                log.error("The Metis consent sweep failed", e);
            }
        }
        claim(now);
    }

    /**
     * Prepares the dispatcher after the scheduling node started: puts the checks that a crash or a shutdown left in generation back into the queue (only the
     * scheduling node generates, so all of them are orphans), and forgets the state that outlived the previous run: the circuit breaker, the failure counters,
     * the waits and the given-up answers of the rating backfill, and when the exercises were first seen enabled. The next sweep counts every enabled exercise as
     * settled. The slots and the tasks in flight are left alone, because tasks may still be running. Tests call it before every method, since all methods of a
     * test lane share this dispatcher.
     */
    public void resetAfterStartup() {
        int released = checkRepository.releaseAllGenerating();
        if (released > 0) {
            log.info("Put {} Metis checks that a crash or a shutdown left in generation back into the queue", released);
        }
        resetCircuit();
        transientFailures.clear();
        ratingFailures.clear();
        ratingRetryAfter.clear();
        ratingsGivenUp.clear();
        firstSeenEnabled.clear();
        startupSweep = true;
    }

    /**
     * Creates the missing checks of an exercise and puts its failed checks back into the queue, at the request of an instructor, without waiting for the
     * delays of the poller. Checks skipped for missing consent or a closed window are queued again as well, since both can change after the due date; checks
     * skipped for no submission or no own code stay as they are, since neither can. The questions are generated later by the scheduling node.
     *
     * @param exerciseId the exercise
     * @return how many checks were created and how many were put back into the queue
     * @throws BadRequestAlertException {@code metis.unsupportedExercise} if the exercise is no individual programming exercise of a course
     * @throws ConflictException        {@code metis.notEnabled} if the check is not enabled for the exercise, {@code metis.noDueDate} if it has no due date
     */
    public MetisGenerateResultDTO generateNow(long exerciseId) {
        MetisExerciseFactsDTO facts = configRepository.findExerciseFacts(exerciseId).filter(found -> found.courseId() != null && found.mode() == ExerciseMode.INDIVIDUAL)
                .orElseThrow(() -> new BadRequestAlertException("The comprehension check is available for individual programming exercises in courses only.", ENTITY_NAME,
                        "metis.unsupportedExercise"));
        if (!configRepository.existsByExerciseIdAndEnabledTrue(exerciseId)) {
            throw new ConflictException("The comprehension check is not enabled for this exercise.", ENTITY_NAME, "metis.notEnabled");
        }
        if (facts.dueDate() == null) {
            throw new ConflictException("The exercise needs a due date before questions can be generated.", ENTITY_NAME, "metis.noDueDate");
        }
        int created = createMissingChecks(exerciseId, TimeUtil.now());
        int requeued = checkRepository.requeueChecksOfExercise(exerciseId, MetisFailureReason.REQUEUED_BY_GENERATE_NOW);
        log.info("Generate now for exercise {}: created {} and requeued {} Metis checks", exerciseId, created, requeued);
        return new MetisGenerateResultDTO(created, requeued);
    }

    /**
     * Sweeps one active exercise: puts checks whose effective due date lies in the future again back into the queue, and creates the missing checks once the
     * exercise has been enabled long enough and its due date has passed the cutoff.
     */
    private void sweep(MetisActiveExerciseDTO exercise, ZonedDateTime now, ZonedDateTime cutoff) {
        long exerciseId = exercise.exerciseId();
        ZonedDateTime firstSeen = firstSeenEnabled.computeIfAbsent(exerciseId, id -> startupSweep ? SETTLED_SINCE_STARTUP : now);
        int reset = checkRepository.resetChecksWithFutureDueDate(exerciseId, now);
        if (reset > 0) {
            log.info("Put {} Metis checks of exercise {} back into the queue because their due date was extended", reset, exerciseId);
        }
        // The settings form saves on every change, so the instructor gets time to finish it before the checks of an exercise past its due date go live
        boolean settled = !now.isBefore(firstSeen.plusMinutes(properties.generation().enableDelayMinutes()));
        if (settled && !exercise.dueDate().isAfter(cutoff)) {
            int created = createMissingChecks(exerciseId, cutoff);
            if (created > 0) {
                log.info("Created {} Metis checks for exercise {}", created, exerciseId);
            }
        }
    }

    /**
     * Creates a check for every eligible participation of the exercise without one whose effective due date has passed the cutoff: pending for students who
     * consent to AI processing, skipped for the others. No repository is read here. The poller and a "generate now" on another node may insert at the same
     * time; the batch whose insert then fails was rolled back as a whole, so the candidates are read once more and fresh rows are inserted for those still
     * missing. If that fails as well, the next sweep creates the rest.
     *
     * @return the number of created checks
     */
    private int createMissingChecks(long exerciseId, ZonedDateTime cutoff) {
        List<MetisCheckCandidateDTO> candidates = checkRepository.findCheckCandidates(exerciseId, cutoff);
        if (candidates.isEmpty()) {
            return 0;
        }
        try {
            return insertChecks(candidates);
        }
        catch (DataIntegrityViolationException | ConcurrencyFailureException firstFailure) {
            log.info("Another node created Metis checks of exercise {} at the same time; reading the missing ones again", exerciseId);
            List<MetisCheckCandidateDTO> remaining = checkRepository.findCheckCandidates(exerciseId, cutoff);
            if (remaining.isEmpty()) {
                return 0;
            }
            try {
                return insertChecks(remaining);
            }
            catch (DataIntegrityViolationException | ConcurrencyFailureException secondFailure) {
                log.warn("Could not create the missing Metis checks of exercise {}; the next sweep creates them", exerciseId, secondFailure);
                return 0;
            }
        }
    }

    /**
     * Inserts one fresh check per candidate, in the order of the participations.
     */
    private int insertChecks(List<MetisCheckCandidateDTO> candidates) {
        Set<Long> consenting = consentService.consentingUsers(candidates.stream().map(MetisCheckCandidateDTO::studentId).collect(Collectors.toSet()));
        List<MetisCheck> checks = candidates.stream().map(candidate -> newCheck(candidate, consenting.contains(candidate.studentId()))).toList();
        return checkRepository.saveAll(checks).size();
    }

    private static MetisCheck newCheck(MetisCheckCandidateDTO candidate, boolean consents) {
        MetisCheck check = new MetisCheck();
        check.setParticipationId(candidate.participationId());
        if (consents) {
            check.setStatus(MetisCheckStatus.PENDING);
        }
        else {
            check.setStatus(MetisCheckStatus.SKIPPED);
            check.setFailureReason(MetisFailureReason.NO_AI_CONSENT.name());
        }
        return check;
    }

    /**
     * Puts the checks of the active exercises that were skipped for missing consent back into the queue, for every student who consents now.
     */
    private void requeueConsentingSkips(Collection<Long> exerciseIds) {
        List<MetisCheckOwnerDTO> skipped = checkRepository.findSkippedChecksWithReason(exerciseIds, MetisFailureReason.NO_AI_CONSENT.name());
        if (skipped.isEmpty()) {
            return;
        }
        Set<Long> consenting = consentService.consentingUsers(skipped.stream().map(MetisCheckOwnerDTO::studentId).collect(Collectors.toSet()));
        List<Long> checkIds = skipped.stream().filter(owner -> consenting.contains(owner.studentId())).map(MetisCheckOwnerDTO::checkId).toList();
        if (!checkIds.isEmpty()) {
            int requeued = checkRepository.requeueSkippedChecks(checkIds, MetisFailureReason.NO_AI_CONSENT.name());
            log.info("Put {} Metis checks back into the queue whose students consent to AI processing now", requeued);
        }
    }

    /**
     * Claims work for every free slot of the executor: an unrated answer for the backfill first, otherwise the pending check of the earliest student window.
     * The circuit breaker and the waiting live ratings are checked before every slot, because a task that ran synchronously may have opened the circuit, and a
     * live rating may start waiting while the loop runs.
     *
     * @param now the current time
     */
    private synchronized void claim(ZonedDateTime now) {
        if (!llmService.isConfigured()) {
            warnNotConfigured(now);
            return;
        }
        ZonedDateTime cutoff = now.minusSeconds(properties.generation().delayAfterDueDateSeconds());
        while (!isPaused(now) && llmService.interactiveWaiting() == 0 && reserveSlot()) {
            boolean handedOver = false;
            try {
                handedOver = submitNextTask(now, cutoff);
            }
            finally {
                if (!handedOver) {
                    inFlight.decrementAndGet();
                }
            }
            if (!handedOver) {
                return;
            }
        }
    }

    /**
     * Reserves a slot of the executor, unless all are taken.
     *
     * @return whether a slot was reserved
     */
    private boolean reserveSlot() {
        int maxTasks = properties.generation().maxConcurrentTasks();
        return inFlight.getAndUpdate(reserved -> reserved < maxTasks ? reserved + 1 : reserved) < maxTasks;
    }

    /**
     * Submits the next piece of work to the reserved slot.
     *
     * @return whether a task took the slot over; false if there is no work or the executor refused the task
     */
    private boolean submitNextTask(ZonedDateTime now, ZonedDateTime cutoff) {
        Optional<Long> unratedAnswer = nextUnratedAnswer(now);
        if (unratedAnswer.isPresent()) {
            return submitRating(unratedAnswer.get());
        }
        Optional<Long> claimedCheck = claimNextCheck(cutoff);
        return claimedCheck.isPresent() && submitGeneration(claimedCheck.get());
    }

    /**
     * The next answer for the rating backfill: answered and unrated, in a check started within the horizon, old enough that the request which stored it cannot
     * still wait for its rating, neither being rated nor given up on, and not waiting for another attempt after a failed rating.
     */
    private Optional<Long> nextUnratedAnswer(ZonedDateTime now) {
        Set<Long> excluded = new HashSet<>(ratingsInFlight);
        excluded.addAll(ratingsGivenUp);
        ratingRetryAfter.values().removeIf(retryAfter -> !retryAfter.isAfter(now));
        excluded.addAll(ratingRetryAfter.keySet());
        excluded.add(NO_QUESTION_ID);
        ZonedDateTime startedAfter = now.minusDays(properties.generation().horizonDays());
        ZonedDateTime answeredBefore = now.minus(properties.answerSettleTime());
        return questionRepository.findUnratedAnsweredQuestionIds(startedAfter, answeredBefore, excluded, PageRequest.of(0, 1)).stream().findFirst();
    }

    /**
     * Claims the first pending check, in the order of the students' windows, that no other claimer takes first.
     */
    private Optional<Long> claimNextCheck(ZonedDateTime cutoff) {
        for (long checkId : checkRepository.findClaimableCheckIds(cutoff, PageRequest.of(0, CLAIM_CANDIDATES))) {
            if (!checksInFlight.contains(checkId) && checkRepository.claimForGeneration(checkId) == 1) {
                return Optional.of(checkId);
            }
        }
        return Optional.empty();
    }

    /**
     * Hands the generation of a claimed check to the executor. When the executor refuses it, the check goes back into the queue.
     *
     * @return whether the executor took the task
     */
    private boolean submitGeneration(long checkId) {
        checksInFlight.add(checkId);
        boolean lastAttempt = transientFailures.getOrDefault(checkId, 0) + 1 >= properties.generation().maxTransientFailures();
        try {
            executor.execute(() -> runGeneration(checkId, lastAttempt));
            return true;
        }
        catch (RejectedExecutionException e) {
            checksInFlight.remove(checkId);
            checkRepository.releaseGeneration(checkId);
            log.warn("The Metis executor refused the generation of check {}, which goes back into the queue", checkId);
            return false;
        }
    }

    /**
     * Hands the backfill rating of a stored answer to the executor.
     *
     * @return whether the executor took the task
     */
    private boolean submitRating(long questionId) {
        ratingsInFlight.add(questionId);
        try {
            executor.execute(() -> runRating(questionId));
            return true;
        }
        catch (RejectedExecutionException e) {
            ratingsInFlight.remove(questionId);
            log.warn("The Metis executor refused the rating of the answer to question {}", questionId);
            return false;
        }
    }

    /**
     * The generation task: generates the check, records the outcome, frees the slot and claims the next work at once. It never throws, so that a synchronous
     * executor never frees the slot twice.
     */
    private void runGeneration(long checkId, boolean lastAttempt) {
        try {
            recordGeneration(checkId, generationService.generate(checkId, lastAttempt), lastAttempt);
        }
        catch (RuntimeException e) {
            log.error("The Metis generation task of check {} failed", checkId, e);
        }
        finally {
            checksInFlight.remove(checkId);
            inFlight.decrementAndGet();
            claimNext();
        }
    }

    /**
     * The backfill task: rates the stored answer, records the outcome, frees the slot and claims the next work at once. It never throws.
     */
    private void runRating(long questionId) {
        try {
            recordRating(questionId, ratingService.rateStoredAnswer(questionId));
        }
        catch (RuntimeException e) {
            log.error("The Metis rating task of question {} failed", questionId, e);
        }
        finally {
            ratingsInFlight.remove(questionId);
            inFlight.decrementAndGet();
            claimNext();
        }
    }

    /**
     * Claims the work for the slot a finishing task freed, so that the slot does not wait for the next tick.
     */
    private void claimNext() {
        try {
            claim(TimeUtil.now());
        }
        catch (RuntimeException e) {
            log.error("Claiming the next Metis task failed", e);
        }
    }

    /**
     * Acts on the outcome of a generation: a success closes the circuit breaker; an unavailable endpoint opens it and counts the attempt of the check, or, on
     * its last attempt, forgets the check, which has failed for good. The circuit opens on the last attempt as well, since the next check would meet the same
     * endpoint.
     */
    private void recordGeneration(long checkId, Outcome outcome, boolean lastAttempt) {
        switch (outcome) {
            case GENERATED -> {
                transientFailures.remove(checkId);
                closeCircuit();
            }
            case SKIPPED, FAILED -> transientFailures.remove(checkId);
            case LLM_UNAVAILABLE -> {
                if (lastAttempt) {
                    transientFailures.remove(checkId);
                }
                else {
                    transientFailures.merge(checkId, 1, Integer::sum);
                }
                openCircuit(TimeUtil.now());
            }
            case DEFERRED -> {
                // the check left the generation or went back into the queue; nothing to count
            }
        }
    }

    /**
     * Acts on the outcome of a backfill rating: an unavailable endpoint opens the circuit breaker; a failed rating is tried again after
     * {@link #RATING_RETRY_DELAY}; an answer that must not be rated, or whose rating failed {@link #MAX_RATING_FAILURES} times, is given up on until the next
     * restart.
     */
    private void recordRating(long questionId, BackfillOutcome outcome) {
        switch (outcome) {
            case RATED -> forgetRatingFailures(questionId);
            case SKIPPED -> {
                forgetRatingFailures(questionId);
                ratingsGivenUp.add(questionId);
            }
            case UNAVAILABLE -> openCircuit(TimeUtil.now());
            case FAILED -> {
                if (ratingFailures.merge(questionId, 1, Integer::sum) >= MAX_RATING_FAILURES) {
                    forgetRatingFailures(questionId);
                    ratingsGivenUp.add(questionId);
                    log.warn("The backfill gives up on rating the answer to Metis question {} after {} failures", questionId, MAX_RATING_FAILURES);
                }
                else {
                    ratingRetryAfter.put(questionId, TimeUtil.now().plus(RATING_RETRY_DELAY));
                }
            }
        }
    }

    private void forgetRatingFailures(long questionId) {
        ratingFailures.remove(questionId);
        ratingRetryAfter.remove(questionId);
    }

    /**
     * Pauses all claims for the current backoff and doubles it, up to {@link #MAX_BACKOFF}.
     *
     * @param now the current time
     */
    private synchronized void openCircuit(ZonedDateTime now) {
        pausedUntil = now.plus(backoff);
        log.warn("The Metis LLM endpoint is unavailable; generation and rating backfill pause until {}", pausedUntil);
        Duration doubled = backoff.multipliedBy(2);
        backoff = doubled.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : doubled;
    }

    /**
     * Ends a pause of the circuit breaker and resets its backoff.
     */
    private synchronized void closeCircuit() {
        if (pausedUntil != null || !backoff.equals(INITIAL_BACKOFF)) {
            log.info("The Metis LLM endpoint answers again; the circuit breaker is closed");
        }
        resetCircuit();
    }

    /**
     * Closes the circuit breaker and resets its backoff without a log line, for the reset after the startup.
     */
    private synchronized void resetCircuit() {
        pausedUntil = null;
        backoff = INITIAL_BACKOFF;
    }

    private synchronized boolean isPaused(ZonedDateTime now) {
        return pausedUntil != null && now.isBefore(pausedUntil);
    }

    /**
     * Warns that the endpoint is not configured, at most once per hour.
     */
    private synchronized void warnNotConfigured(ZonedDateTime now) {
        if (lastNotConfiguredWarning == null || !now.isBefore(lastNotConfiguredWarning.plus(NOT_CONFIGURED_WARNING_INTERVAL))) {
            lastNotConfiguredWarning = now;
            log.warn("The Metis LLM endpoint is not configured (artemis.metis.llm.base-url and model), so no questions are generated and no stored answers are rated");
        }
    }
}
