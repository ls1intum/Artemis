package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PostConstruct;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import de.tum.cit.aet.artemis.admin.domain.LLMServiceType;
import de.tum.cit.aet.artemis.admin.repository.LLMTokenUsageTraceRepository;
import de.tum.cit.aet.artemis.core.exception.TooManyRequestsAlertException;
import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;

/**
 * Admission-control token budgets for Hyperion generation. Limits are disabled when configured as {@code 0}, so installations can opt in per deployment.
 */
@Lazy
@Service
@Conditional(HyperionExerciseGenerationEnabled.class)
public class HyperionGenerationBudgetService {

    private static final String ENTITY_NAME = "hyperionExerciseGeneration";

    private static final String RESERVATION_MAP_NAME = "hyperion-generation-token-budget-reservations";

    private static final String RESERVATION_LOCK_KEY = "__budget-lock__";

    private static final long RESERVATION_LOCK_WAIT_SECONDS = 2;

    private static final Logger log = LoggerFactory.getLogger(HyperionGenerationBudgetService.class);

    private final LLMTokenUsageTraceRepository tokenUsageTraceRepository;

    @Nullable
    private final HazelcastInstance hazelcastInstance;

    private final Duration budgetWindow;

    private final long maxTokensPerUser;

    private final long maxTokensPerCourse;

    private final long maxTokensGlobal;

    private final long maxTokensPerJob;

    private final Duration reservationTtl;

    private IMap<String, TokenBudgetReservation> reservationMap;

    @Autowired
    public HyperionGenerationBudgetService(LLMTokenUsageTraceRepository tokenUsageTraceRepository, @Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance,
            @Value("${artemis.hyperion.agent.token-budget-window:PT24H}") Duration budgetWindow,
            @Value("${artemis.hyperion.agent.admission-max-tokens-per-user:12000000}") long maxTokensPerUser,
            @Value("${artemis.hyperion.agent.admission-max-tokens-per-course:120000000}") long maxTokensPerCourse,
            @Value("${artemis.hyperion.agent.admission-max-tokens-global:600000000}") long maxTokensGlobal,
            @Value("${artemis.hyperion.agent.max-tokens-per-job:3000000}") long maxTokensPerJob,
            @Value("${artemis.hyperion.agent.max-job-duration:PT30M}") Duration maxJobDuration) {
        validateConfiguration(budgetWindow, maxTokensPerUser, maxTokensPerCourse, maxTokensGlobal, maxTokensPerJob);
        this.tokenUsageTraceRepository = tokenUsageTraceRepository;
        this.hazelcastInstance = hazelcastInstance;
        this.budgetWindow = budgetWindow;
        this.maxTokensPerUser = maxTokensPerUser;
        this.maxTokensPerCourse = maxTokensPerCourse;
        this.maxTokensGlobal = maxTokensGlobal;
        this.maxTokensPerJob = maxTokensPerJob;
        Duration activeReservationTtl = reservationTtl(maxJobDuration);
        this.reservationTtl = budgetWindow == null || activeReservationTtl.compareTo(budgetWindow) >= 0 ? activeReservationTtl : budgetWindow;
    }

    private static void validateConfiguration(Duration budgetWindow, long maxTokensPerUser, long maxTokensPerCourse, long maxTokensGlobal, long maxTokensPerJob) {
        if (maxTokensPerUser < 0 || maxTokensPerCourse < 0 || maxTokensGlobal < 0 || maxTokensPerJob <= 0) {
            throw new IllegalArgumentException("Hyperion token budgets must not be negative and max-tokens-per-job must be positive");
        }
        boolean budgetEnabled = maxTokensPerUser > 0 || maxTokensPerCourse > 0 || maxTokensGlobal > 0;
        if (budgetEnabled && (budgetWindow == null || budgetWindow.isZero() || budgetWindow.isNegative())) {
            throw new IllegalArgumentException("artemis.hyperion.agent.token-budget-window must be positive when an admission budget is enabled");
        }
        validateBudgetFitsOneJob("admission-max-tokens-per-user", maxTokensPerUser, maxTokensPerJob);
        validateBudgetFitsOneJob("admission-max-tokens-per-course", maxTokensPerCourse, maxTokensPerJob);
        validateBudgetFitsOneJob("admission-max-tokens-global", maxTokensGlobal, maxTokensPerJob);
    }

    private static void validateBudgetFitsOneJob(String budgetName, long budget, long maxTokensPerJob) {
        if (budget > 0 && maxTokensPerJob > budget) {
            throw new IllegalArgumentException("artemis.hyperion.agent." + budgetName
                    + " must be at least artemis.hyperion.agent.max-tokens-per-job so one generation can be admitted without under-reserving its allowed usage");
        }
    }

    public HyperionGenerationBudgetService(LLMTokenUsageTraceRepository tokenUsageTraceRepository, Duration budgetWindow, long maxTokensPerUser, long maxTokensPerCourse,
            long maxTokensGlobal) {
        this.tokenUsageTraceRepository = tokenUsageTraceRepository;
        this.hazelcastInstance = null;
        this.budgetWindow = budgetWindow;
        this.maxTokensPerUser = maxTokensPerUser;
        this.maxTokensPerCourse = maxTokensPerCourse;
        this.maxTokensGlobal = maxTokensGlobal;
        this.maxTokensPerJob = 0;
        this.reservationTtl = Duration.ZERO;
    }

    @PostConstruct
    public void init() {
        if (hazelcastInstance != null) {
            reservationMap = hazelcastInstance.getMap(RESERVATION_MAP_NAME);
        }
    }

    /**
     * Fails fast before starting a new expensive generation run if any configured rolling token budget is already exhausted.
     *
     * @param userId   the requesting user's id, or null if unavailable
     * @param courseId the course id, or null if unavailable
     */
    public void assertWithinBudgets(@Nullable Long userId, @Nullable Long courseId) {
        assertWithinBudgets(userId, courseId, 0);
    }

    /**
     * Reserves the configured worst-case token allowance for a newly admitted job. The reservation is released when the async job finishes; a TTL is a crash safety net.
     *
     * @param userId   the requesting user's id, or null if unavailable
     * @param courseId the course id, or null if unavailable
     * @return the reservation to attach to the job
     */
    public BudgetReservation reserveGenerationBudget(@Nullable Long userId, @Nullable Long courseId) {
        if (maxTokensPerUser <= 0 && maxTokensPerCourse <= 0 && maxTokensGlobal <= 0) {
            return BudgetReservation.none();
        }
        if (reservationMap == null || maxTokensPerJob <= 0) {
            assertWithinBudgets(userId, courseId);
            return BudgetReservation.none();
        }
        boolean locked = false;
        try {
            locked = reservationMap.tryLock(RESERVATION_LOCK_KEY, RESERVATION_LOCK_WAIT_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                throw admissionBusy();
            }
            assertWithinBudgets(userId, courseId, maxTokensPerJob);
            String id = UUID.randomUUID().toString();
            long ttlSeconds = Math.max(1L, reservationTtl.toSeconds());
            reservationMap.set(id, new TokenBudgetReservation(userId, courseId, maxTokensPerJob), ttlSeconds, TimeUnit.SECONDS);
            return new BudgetReservation(id);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw admissionBusy();
        }
        catch (RuntimeException e) {
            if (e instanceof TooManyRequestsAlertException) {
                throw e;
            }
            log.warn("Hyperion generation budget reservation failed; failing admission closed: {}", e.getMessage());
            throw admissionBusy();
        }
        finally {
            if (locked) {
                try {
                    reservationMap.unlock(RESERVATION_LOCK_KEY);
                }
                catch (RuntimeException e) {
                    log.warn("Could not unlock Hyperion generation budget reservation admission lock: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * Releases a previously admitted generation budget reservation.
     *
     * @param reservationId the reservation id to release, or null when no reservation was made
     */
    public void releaseReservation(@Nullable String reservationId) {
        if (reservationId == null || reservationMap == null) {
            return;
        }
        try {
            reservationMap.remove(reservationId);
        }
        catch (RuntimeException e) {
            log.warn("Could not release Hyperion generation budget reservation {}; TTL cleanup will release it later: {}", reservationId, e.getMessage());
        }
    }

    /**
     * Retains the worst-case debit when exact provider usage could not be recorded.
     *
     * @param reservationId the reservation to retain
     * @return whether no reservation was required or the reservation still exists
     */
    public boolean retainReservationForBudgetWindow(@Nullable String reservationId) {
        if (reservationId == null || reservationMap == null) {
            return true;
        }
        try {
            boolean retained = reservationMap.setTtl(reservationId, Math.max(1L, budgetWindow.toSeconds()), TimeUnit.SECONDS);
            if (!retained) {
                log.error("Could not retain uncertain Hyperion token reservation {} because it no longer exists", reservationId);
            }
            return retained;
        }
        catch (RuntimeException e) {
            log.warn("Could not extend uncertain Hyperion token reservation {} through the budget window: {}", reservationId, e.getMessage());
            return false;
        }
    }

    /**
     * Refreshes an active reservation's crash-only TTL while its owning worker is still heartbeating.
     *
     * @param reservationId the reservation id to refresh, or null when no reservation was made
     * @return whether no reservation was required or the reservation still exists
     */
    public boolean refreshReservation(@Nullable String reservationId) {
        if (reservationId == null || reservationMap == null) {
            return true;
        }
        try {
            boolean refreshed = reservationMap.setTtl(reservationId, Math.max(1L, reservationTtl.toSeconds()), TimeUnit.SECONDS);
            if (!refreshed) {
                log.error("Could not refresh Hyperion generation budget reservation {} because it no longer exists", reservationId);
            }
            return refreshed;
        }
        catch (RuntimeException e) {
            log.warn("Could not refresh Hyperion generation budget reservation {}; its existing TTL remains in effect: {}", reservationId, e.getMessage());
            return false;
        }
    }

    private void assertWithinBudgets(@Nullable Long userId, @Nullable Long courseId, long additionalReservedTokens) {
        if (maxTokensPerUser <= 0 && maxTokensPerCourse <= 0 && maxTokensGlobal <= 0) {
            return;
        }
        ZonedDateTime since = ZonedDateTime.now().minus(budgetWindow);
        ReservationTotals reservations = reservationTotals(userId, courseId);
        if (maxTokensPerUser > 0 && userId != null) {
            long used = tokenUsageTraceRepository.sumTokensSinceForUser(LLMServiceType.HYPERION, GenerationJobService.GENERATION_PIPELINE_ID, userId, since)
                    + reservations.forUser() + additionalReservedTokens;
            if (exceedsBudget(used, maxTokensPerUser, additionalReservedTokens)) {
                throw budgetExceeded("Your Hyperion generation token budget is currently exhausted. Please try again later.");
            }
        }
        if (maxTokensPerCourse > 0 && courseId != null) {
            long used = tokenUsageTraceRepository.sumTokensSinceForCourse(LLMServiceType.HYPERION, GenerationJobService.GENERATION_PIPELINE_ID, courseId, since)
                    + reservations.forCourse() + additionalReservedTokens;
            if (exceedsBudget(used, maxTokensPerCourse, additionalReservedTokens)) {
                throw budgetExceeded("The course Hyperion generation token budget is currently exhausted. Please try again later.");
            }
        }
        if (maxTokensGlobal > 0) {
            long used = tokenUsageTraceRepository.sumTokensSince(LLMServiceType.HYPERION, GenerationJobService.GENERATION_PIPELINE_ID, since) + reservations.global()
                    + additionalReservedTokens;
            if (exceedsBudget(used, maxTokensGlobal, additionalReservedTokens)) {
                throw budgetExceeded("The global Hyperion generation token budget is currently exhausted. Please try again later.");
            }
        }
    }

    private static boolean exceedsBudget(long usedIncludingAdditionalReservation, long maxTokens, long additionalReservedTokens) {
        return additionalReservedTokens > 0 ? usedIncludingAdditionalReservation > maxTokens : usedIncludingAdditionalReservation >= maxTokens;
    }

    private ReservationTotals reservationTotals(@Nullable Long userId, @Nullable Long courseId) {
        if (reservationMap == null) {
            return ReservationTotals.empty();
        }
        long forUser = 0;
        long forCourse = 0;
        long global = 0;
        for (TokenBudgetReservation reservation : reservationMap.values()) {
            global += reservation.tokens();
            if (userId != null && userId.equals(reservation.userId())) {
                forUser += reservation.tokens();
            }
            if (courseId != null && courseId.equals(reservation.courseId())) {
                forCourse += reservation.tokens();
            }
        }
        return new ReservationTotals(forUser, forCourse, global);
    }

    private static Duration reservationTtl(Duration maxJobDuration) {
        if (maxJobDuration == null || maxJobDuration.isZero() || maxJobDuration.isNegative()) {
            return Duration.ofHours(2);
        }
        return maxJobDuration.plusMinutes(5);
    }

    private static TooManyRequestsAlertException budgetExceeded(String message) {
        return new TooManyRequestsAlertException(message, ENTITY_NAME, "generationTokenBudgetExceeded");
    }

    private static TooManyRequestsAlertException admissionBusy() {
        return new TooManyRequestsAlertException("Hyperion generation admission is temporarily busy. Please try again in a moment.", ENTITY_NAME, "generationAdmissionBusy");
    }

    public record BudgetReservation(@Nullable String id) {

        public static BudgetReservation none() {
            return new BudgetReservation(null);
        }
    }

    private record ReservationTotals(long forUser, long forCourse, long global) {

        static ReservationTotals empty() {
            return new ReservationTotals(0, 0, 0);
        }
    }

    private record TokenBudgetReservation(@Nullable Long userId, @Nullable Long courseId, long tokens) implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;
    }
}
