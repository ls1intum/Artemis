package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE_AND_SCHEDULING;
import static de.tum.cit.aet.artemis.core.config.StartupDelayConfig.PARTICIPATION_SCORES_SCHEDULE_DELAY_SEC;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.exercise.repository.MilestoneExerciseGroupRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;

/**
 * Keeps every student's aggregated milestone score up to date as their user story results change.
 * <p>
 * Deliberately modelled on {@code ParticipantScoreScheduleService}, which solves the same problem class ("a result
 * changed, recompute a value derived from it") and whose concurrency design this reuses wholesale:
 * <ul>
 * <li><b>Debouncing.</b> One build fans a result out to every user story of a group, so a single push produces one
 * event per story for the same (milestone, student) pair. Scheduling replaces and cancels any pending task for that
 * pair, so the burst collapses into a single recomputation shortly after the last story result is committed.</li>
 * <li><b>Striped locks.</b> Two tasks for the same pair can still overlap when {@code cancel(true)} cannot interrupt a
 * task that is already running, so execution is serialised per pair.</li>
 * <li><b>Full recomputation.</b> Each run derives the score from current state rather than applying a delta, so a run
 * that is late cannot write an older value - it simply reads what is there now. Together with the striped lock that is
 * what removes the need for any "is this task stale" guard: runs for one pair are serialised, and each of them reads
 * fresh.</li>
 * <li><b>Cron fallback.</b> Events can be lost (restart, broker hiccup), so a minutely sweep re-schedules every user
 * story result modified since the last sweep.</li>
 * </ul>
 * Only the scheduling node runs this ({@code PROFILE_CORE_AND_SCHEDULING}), which is what makes the in-process locks
 * sufficient - the same guarantee participant scores rely on, and the reason no distributed lock is taken here.
 *
 * @see MilestoneScoreService
 * @see de.tum.cit.aet.artemis.assessment.ResultListener
 */
@Lazy
@Service
@Profile(PROFILE_CORE_AND_SCHEDULING)
public class MilestoneScoreScheduleService {

    /**
     * How long a scheduled recomputation waits before running, giving the rest of a fan-out's story results time to land
     * so the burst collapses into one run.
     */
    public static int DEFAULT_WAITING_TIME_FOR_SCHEDULED_TASKS = 500;

    private static final Logger log = LoggerFactory.getLogger(MilestoneScoreScheduleService.class);

    private static final int NUM_LOCK_STRIPES = 32;

    private final Object[] lockStripes = IntStream.range(0, NUM_LOCK_STRIPES).mapToObj(i -> new Object()).toArray();

    private final Map<MilestoneScoreId, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    /**
     * Lookups that have been handed to the scheduler but have not yet produced their debounced recomputation. Tracked so
     * {@link #isIdle()} does not report "nothing to do" in the window between an event arriving and its task existing.
     */
    private final AtomicInteger pendingResolutions = new AtomicInteger();

    private Optional<Instant> lastScheduledRun = Optional.empty();

    private final TaskScheduler scheduler;

    private final MilestoneScoreService milestoneScoreService;

    private final MilestoneExerciseGroupRepository milestoneExerciseGroupRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    public MilestoneScoreScheduleService(@Qualifier("taskScheduler") TaskScheduler scheduler, MilestoneScoreService milestoneScoreService,
            MilestoneExerciseGroupRepository milestoneExerciseGroupRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository) {
        this.scheduler = scheduler;
        this.milestoneScoreService = milestoneScoreService;
        this.milestoneExerciseGroupRepository = milestoneExerciseGroupRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
    }

    /**
     * The key a recomputation is debounced and locked on: one student's score on one milestone exercise.
     *
     * @param milestoneExerciseId the id of the milestone exercise
     * @param studentId           the id of the student
     */
    private record MilestoneScoreId(Long milestoneExerciseId, Long studentId) {
    }

    /**
     * @return true if no recomputation is pending, which is what tests wait on before asserting a milestone score
     */
    public boolean isIdle() {
        return !isRunning.get() || (scheduledTasks.isEmpty() && pendingResolutions.get() == 0);
    }

    /**
     * Start accepting work once the application is up. Mirrors {@code ParticipantScoreScheduleService.startup}: the bean
     * is lazy, so an {@code @EventListener} would not fire.
     */
    @PostConstruct
    public void startup() {
        scheduler.schedule(() -> {
            isRunning.set(true);
            try {
                // this should never prevent the application start of Artemis
                sweepForMissedResults();
            }
            catch (Exception ex) {
                log.error("Cannot schedule milestone score service", ex);
            }
        }, Instant.now().plusSeconds(PARTICIPATION_SCORES_SCHEDULE_DELAY_SEC));
    }

    public void activate() {
        isRunning.set(true);
    }

    /**
     * Before shutdown, cancel all running or scheduled tasks. The cron sweep picks them up again after a restart.
     */
    @PreDestroy
    public void shutdown() {
        isRunning.set(false);
        scheduledTasks.values().forEach(future -> future.cancel(true));
        scheduledTasks.clear();
        pendingResolutions.set(0);
    }

    /**
     * Fallback for lost events: every minute, re-schedule every milestone whose user story results changed since the
     * previous sweep.
     */
    @Scheduled(cron = "0 * * * * *")
    protected void scheduleMissedResults() {
        SecurityUtils.setAuthorizationObject();
        if (isRunning.get()) {
            sweepForMissedResults();
        }
    }

    /**
     * Schedules a recomputation for every (milestone, student) pair whose user story results were modified since the
     * last sweep. Idempotent by construction: a pair that is already pending is simply rescheduled.
     */
    public void sweepForMissedResults() {
        if (!isRunning.get()) {
            log.debug("Cannot sweep for missed results, because the milestone score service is not running");
            return;
        }
        var since = lastScheduledRun.orElseGet(Instant::now);
        lastScheduledRun = Optional.of(Instant.now());

        var targets = milestoneExerciseGroupRepository.findMilestoneScoreTargetsForUserStoryResultsModifiedAfter(since);
        targets.forEach(target -> scheduleTask(target.milestoneExerciseId(), target.studentId()));
        log.debug("Swept {} milestone score targets modified after {}.", targets.size(), since);
    }

    /**
     * Schedules a recomputation for the milestone group owning the given user story, which is how result events reach
     * this service ({@code ResultListener} sees one user story result at a time and deliberately does not resolve the
     * owning group itself).
     * <p>
     * Resolving the milestone here rather than in the caller is what makes debouncing work: one build produces one
     * result per story of a group, and those all have to collapse onto the same key.
     *
     * @param userStoryExerciseId the id of the user story exercise whose result changed
     * @param studentId           the id of the student
     */
    public void scheduleForUserStory(@NonNull Long userStoryExerciseId, @NonNull Long studentId) {
        if (!isRunning.get()) {
            log.debug("Cannot schedule milestone score task, because the service is not running");
            return;
        }
        // This is reached from ResultListener, a JPA entity listener, so it executes *inside the flush* that is
        // persisting the result. Querying here re-enters the session while Hibernate is iterating its own action queue,
        // which fails the flush with a ConcurrentModificationException and rolls the result back - the student's build
        // then has a submission and no result at all. Hand the lookup to the scheduler so this path stays memory-only,
        // exactly like ParticipantScoreScheduleService.scheduleTask, and let it run on its own session after commit.
        pendingResolutions.incrementAndGet();
        scheduler.schedule(() -> {
            try {
                SecurityUtils.setAuthorizationObject();
                milestoneExerciseGroupRepository.findMilestoneExerciseIdByUserStoryExerciseId(userStoryExerciseId)
                        .ifPresent(milestoneExerciseId -> scheduleTask(milestoneExerciseId, studentId));
            }
            catch (Exception exception) {
                log.error("Could not resolve the milestone owning user story exercise {}:", userStoryExerciseId, exception);
            }
            finally {
                pendingResolutions.decrementAndGet();
            }
        }, Instant.now());
    }

    /**
     * Schedules a recomputation for every student of a milestone group. Used when the milestone's own {@code maxPoints}
     * changed (a user story was added, removed, moved, or repointed), which makes every student's score - a percentage
     * of that number - stale at once.
     * <p>
     * Students are enumerated through the milestone's own participations rather than the stories': a student who shares
     * the group's repository always has one, and it is the participation that carries the result being rewritten.
     *
     * @param milestoneExerciseId the id of the milestone exercise whose group to recompute
     */
    public void scheduleForGroup(@NonNull Long milestoneExerciseId) {
        if (!isRunning.get()) {
            log.debug("Cannot schedule milestone score tasks for a group, because the service is not running");
            return;
        }
        // Enumerated on the scheduler rather than on the caller's thread, for the same reason as
        // scheduleForUserStory: this is reached synchronously from MilestoneExercisePointsService while an exercise is
        // being created, updated or deleted, and a scheduling concern has no business issuing queries in the middle of
        // that.
        pendingResolutions.incrementAndGet();
        scheduler.schedule(() -> {
            try {
                SecurityUtils.setAuthorizationObject();
                var participations = programmingExerciseStudentParticipationRepository.findAllByExerciseIdAndRepositoryUriIsNotNullAndTestRunFalse(milestoneExerciseId);
                participations.forEach(participation -> participation.getStudent().ifPresent(student -> scheduleTask(milestoneExerciseId, student.getId())));
                log.debug("Scheduled milestone score tasks for {} participants of milestone exercise {}.", participations.size(), milestoneExerciseId);
            }
            catch (Exception exception) {
                log.error("Could not enumerate the participants of milestone exercise {}:", milestoneExerciseId, exception);
            }
            finally {
                pendingResolutions.decrementAndGet();
            }
        }, Instant.now());
    }

    /**
     * Schedules a recomputation of the given student's score on the given milestone exercise, replacing any pending one
     * for the same pair.
     *
     * @param milestoneExerciseId the id of the milestone exercise whose aggregate to recompute
     * @param studentId           the id of the student
     */
    public void scheduleTask(@NonNull Long milestoneExerciseId, @NonNull Long studentId) {
        if (!isRunning.get()) {
            log.debug("Cannot schedule milestone score task, because the service is not running");
            return;
        }
        final var milestoneScoreId = new MilestoneScoreId(milestoneExerciseId, studentId);
        var schedulingTime = ZonedDateTime.now().plus(DEFAULT_WAITING_TIME_FOR_SCHEDULED_TASKS, ChronoUnit.MILLIS);
        scheduledTasks.compute(milestoneScoreId, (key, existingTask) -> {
            if (existingTask != null) {
                existingTask.cancel(true);
            }
            // Capture this task's own future so executeTask() can remove exactly this map entry when it finishes
            // (see the compare-and-remove in its finally block).
            AtomicReference<ScheduledFuture<?>> ownFuture = new AtomicReference<>();
            ScheduledFuture<?> future = scheduler.schedule(() -> executeTask(milestoneScoreId, ownFuture.get()), schedulingTime.toInstant());
            ownFuture.set(future);
            return future;
        });
        log.debug("Scheduled milestone score task for milestone exercise {} and student {} at {}.", milestoneExerciseId, studentId, schedulingTime);
    }

    private void executeTask(MilestoneScoreId milestoneScoreId, ScheduledFuture<?> thisTask) {
        // Serialise per milestone+student: cancel(true) cannot stop a task that is already past its interruptible point,
        // so two runs for the same pair can otherwise overlap and race on the same milestone result row.
        synchronized (lockStripes[Math.floorMod(milestoneScoreId.hashCode(), NUM_LOCK_STRIPES)]) {
            long start = System.currentTimeMillis();
            try {
                SecurityUtils.setAuthorizationObject();
                Optional<Result> updated = milestoneScoreService.recalculate(milestoneScoreId.milestoneExerciseId(), milestoneScoreId.studentId());
                updated.ifPresent(result -> log.debug("Updated milestone score for milestone exercise {} and student {} to {}.", milestoneScoreId.milestoneExerciseId(),
                        milestoneScoreId.studentId(), result.getScore()));
            }
            catch (Exception exception) {
                log.error("Exception while aggregating the milestone score for milestone exercise {} and student {}:", milestoneScoreId.milestoneExerciseId(),
                        milestoneScoreId.studentId(), exception);
            }
            finally {
                // Compare-and-remove: if scheduleTask() replaced this entry with a newer task while this one was
                // running, only that newer task's own invocation may remove the entry. Removing unconditionally would
                // let a superseded task evict a newer, not-yet-run task, making isIdle() report true while the
                // milestone score is still stale.
                scheduledTasks.remove(milestoneScoreId, thisTask);
            }
            log.debug("Aggregating milestone exercise {} for student {} took {} ms.", milestoneScoreId.milestoneExerciseId(), milestoneScoreId.studentId(),
                    System.currentTimeMillis() - start);
        }
    }
}
