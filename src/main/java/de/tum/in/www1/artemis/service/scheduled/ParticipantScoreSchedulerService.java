package de.tum.in.www1.artemis.service.scheduled;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.Team;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.participation.Participant;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.scores.ParticipantScore;
import de.tum.in.www1.artemis.domain.scores.StudentScore;
import de.tum.in.www1.artemis.domain.scores.TeamScore;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.util.RoundingUtil;

/**
 * Scheduled service for the calculation of the participant scores.
 * Note: Only active on the main instance with "scheduling" profile.
 * <p>
 * The approach is two-sided, to make the participant scores eventually consistent within seconds without overloading the database.
 * Using a listener on the {@link Result} entity, changes are detected and forwarded (via the broker if not on the main instance) to this service.
 * This method is fast, but not 100% reliable. Therefore, a cron job regularly checks for invalid participant scores and updates them.
 * In all cases, using asynchronous scheduled tasks speeds up all requests that modify results.
 * @see de.tum.in.www1.artemis.service.listeners.ResultListener
 */
@Service
@Profile("scheduling")
public class ParticipantScoreSchedulerService {

    private final Logger logger = LoggerFactory.getLogger(ParticipantScoreSchedulerService.class);

    private final TaskScheduler scheduler;

    private final Map<Integer, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    private Optional<Instant> lastScheduledRun = Optional.empty();

    private final ParticipantScoreRepository participantScoreRepository;

    private final StudentScoreRepository studentScoreRepository;

    private final TeamScoreRepository teamScoreRepository;

    private final ExerciseRepository exerciseRepository;

    private final ResultRepository resultRepository;

    private final UserRepository userRepository;

    private final TeamRepository teamRepository;

    public ParticipantScoreSchedulerService(@Qualifier("taskScheduler") TaskScheduler scheduler, ParticipantScoreRepository participantScoreRepository,
            StudentScoreRepository studentScoreRepository, TeamScoreRepository teamScoreRepository, ExerciseRepository exerciseRepository, ResultRepository resultRepository,
            UserRepository userRepository, TeamRepository teamRepository) {
        this.scheduler = scheduler;
        this.participantScoreRepository = participantScoreRepository;
        this.studentScoreRepository = studentScoreRepository;
        this.teamScoreRepository = teamScoreRepository;
        this.exerciseRepository = exerciseRepository;
        this.resultRepository = resultRepository;
        this.userRepository = userRepository;
        this.teamRepository = teamRepository;
    }

    /**
     * Check if the scheduler has tasks to be executed or is idle.
     * @return true if the scheduler is idle, false otherwise
     */
    public boolean isIdle() {
        return scheduledTasks.isEmpty();
    }

    /**
     * Schedule all outdated participant scores when the service is started.
     */
    @PostConstruct
    public void startup() {
        scheduleTasks();
    }

    /**
     * Before shutdown, cancel all running or scheduled tasks.
     */
    @PreDestroy
    public void shutdown() {
        // Stop all running tasks, we will reschedule them on startup again
        scheduledTasks.values().forEach(future -> {
            future.cancel(true);
        });
        scheduledTasks.clear();
    }

    /**
     * Every minute, query for modified results and schedule a task to update the participant scores.
     * We schedule all results that were created/updated since the last run of the cron job.
     * Additionally, we schedule all participant scores that are outdated/invalid.
     */
    @Scheduled(cron = "0 * * * * *")
    protected void scheduleTasks() {
        logger.info("Schedule tasks to process...");
        SecurityUtils.setAuthorizationObject();

        // Find all results that were added after the last run (on startup: last time we modified a participant score)
        var latestRun = lastScheduledRun.orElseGet(() -> participantScoreRepository.getLatestModifiedDate().orElse(Instant.now()));
        // Update last run time before we continue with time-consuming operations
        lastScheduledRun = Optional.of(Instant.now());

        var resultsToProcess = resultRepository.findAllByLastModifiedDateAfter(latestRun);
        resultsToProcess.forEach(result -> {
            if (result.getParticipation() instanceof StudentParticipation studentParticipation) {
                var lastModified = result.getLastModifiedDate() == null ? Instant.now() : result.getLastModifiedDate();
                scheduleTask(studentParticipation.getExercise().getId(), studentParticipation.getParticipant().getId(), lastModified, null);
            }
        });

        // Find all outdated participant scores where the last result is null (because it was deleted)
        var participantScoresToProcess = participantScoreRepository.findAllOutdated();
        participantScoresToProcess.forEach(participantScore -> {
            if (participantScore instanceof TeamScore teamScore) {
                scheduleTask(teamScore.getExercise().getId(), teamScore.getTeam().getId(), Instant.now(), null);
            }
            else if (participantScore instanceof StudentScore studentScore) {
                scheduleTask(studentScore.getExercise().getId(), studentScore.getUser().getId(), Instant.now(), null);
            }
        });

        logger.info("Scheduled processing of {} results and {} participant scores.", resultsToProcess.size(), participantScoresToProcess.size());
    }

    /**
     * Schedule a task to update the participant score for the given combination of exercise and participant.
     * @param exerciseId the id of the exercise
     * @param participantId the id of the participant (user or team, determined by the exercise)
     * @param resultIdToBeDeleted the id of the result that is about to be deleted (or null, if result is created/updated)
     */
    public void scheduleTask(@NotNull Long exerciseId, @NotNull Long participantId, Long resultIdToBeDeleted) {
        scheduleTask(exerciseId, participantId, Instant.now(), resultIdToBeDeleted);
    }

    /**
     * Schedule a task to update the participant score for the given combination of exercise and participant.
     * @param exerciseId the id of the exercise
     * @param participantId the id of the participant (user or team, determined by the exercise)
     * @param resultLastModified the last modified date of the result that triggered the update
     * @param resultIdToBeDeleted the id of the result that is about to be deleted (or null, if result is created/updated)
     */
    private void scheduleTask(Long exerciseId, Long participantId, Instant resultLastModified, Long resultIdToBeDeleted) {
        var participantScoreId = new ParticipantScoreId(exerciseId, participantId);
        if (scheduledTasks.containsKey(participantScoreId.hashCode())) {
            // We already have a scheduled task for this result
            return;
        }

        var schedulingTime = ZonedDateTime.now().plus(500, ChronoUnit.MILLIS);
        var future = scheduler.schedule(() -> this.executeTask(exerciseId, participantId, resultLastModified, resultIdToBeDeleted), schedulingTime.toInstant());
        scheduledTasks.put(participantScoreId.hashCode(), future);
        logger.debug("Scheduled task for exercise {} and participant {} at {}.", exerciseId, participantId, schedulingTime);
    }

    /**
     * Execute the task to update the participant score for the given combination of exercise and participant.
     * @param exerciseId the id of the exercise
     * @param participantId the id of the participant (user or team, determined by the exercise)
     * @param resultLastModified the last modified date of the result that triggered the update
     * @param resultIdToBeDeleted the id of the result that is about to be deleted (optional)
     */
    private void executeTask(Long exerciseId, Long participantId, Instant resultLastModified, Long resultIdToBeDeleted) {
        long start = System.currentTimeMillis();
        logger.info("Processing exercise {} and participant {} to update participant scores.", exerciseId, participantId);
        try {
            SecurityUtils.setAuthorizationObject();

            var exercise = exerciseRepository.findById(exerciseId).orElse(null);
            if (exercise == null) {
                // If the exercise was deleted, we can delete all participant scores for it as well
                logger.debug("Exercise {} no longer exists, deleting participant score.", exerciseId);
                participantScoreRepository.deleteAllByExerciseId(exerciseId);
                return;
            }

            Participant participant;
            Optional<ParticipantScore> participantScore;
            if (exercise.isTeamMode()) {
                // Fetch the team and its score for the given exercise
                participant = teamRepository.findByIdElseThrow(participantId);
                participantScore = teamScoreRepository.findByExercise_IdAndTeam_Id(exerciseId, participantId).map(score -> score);
            }
            else {
                // Fetch the student and its score for the given exercise
                participant = userRepository.findByIdElseThrow(participantId);
                participantScore = studentScoreRepository.findByExercise_IdAndUser_Id(exerciseId, participantId).map(score -> score);
            }

            if (participantScore.isPresent()) {
                var lastModified = participantScore.get().getLastModifiedDate();
                if (lastModified != null && lastModified.isAfter(resultLastModified)) {
                    // The participant score was already updated after the last modified date of the result that this task
                    // We assume we already processed the result with the last task that ran and therefore skip the processing
                    logger.debug("Participant score {} is already up-to-date, skipping.", participantScore.get().getId());
                    return;
                }
            }
            else {
                if (resultIdToBeDeleted != null) {
                    // A participant score for this exercise/participant combination does not exist and this task was triggered because a result will be deleted
                    // It is very likely that the whole participation or exercise is about to be deleted and their participant scores were already removed
                    // We do not need to do anything in that case
                    logger.debug("Result {} will be deleted and participant score for its participation is already gone, skipping.", resultIdToBeDeleted);
                    return;
                }
            }

            // Either use the existing participant score or create a new one
            var score = participantScore.orElseGet(() -> {
                if (participant instanceof Team team) {
                    var teamScore = new TeamScore();
                    teamScore.setTeam(team);
                    teamScore.setExercise(exercise);
                    return teamScore;
                }
                else if (participant instanceof User user) {
                    var studentScore = new StudentScore();
                    studentScore.setUser(user);
                    studentScore.setExercise(exercise);
                    return studentScore;
                }
                else {
                    return null;
                }
            });

            // Now do the heavy lifting and calculate the latest score based on all results for this exercise
            // The result that is about to be deleted is excluded from the calculation
            if (resultIdToBeDeleted != null) {
                updateParticipantScore(score, resultIdToBeDeleted);
            }
            else {
                updateParticipantScore(score);
            }
        }
        catch (Exception e) {
            logger.error("Exception while processing participant score for exercise {} and participant {} for participant scores:", exerciseId, participantId, e);
        }
        finally {
            scheduledTasks.remove(new ParticipantScoreId(exerciseId, participantId).hashCode());
        }
        long end = System.currentTimeMillis();
        logger.info("Updating the participant score for exercise {} and participant {} took {} ms.", exerciseId, participantId, end - start);
    }

    /**
     * Updates the given participant score by fetching the last (rated) results from the database.
     * If both no result and no rated result is found, the participant score is deleted.
     * @param participantScore The participant score to update (with the exercise eager loaded)
     * @param resultIdsToIgnore A list of result ids to ignore when calculating the score
     */
    private void updateParticipantScore(ParticipantScore participantScore, Long... resultIdsToIgnore) {
        var lastRatedResult = getLastRatedResultForParticipantScore(participantScore, resultIdsToIgnore).orElse(null);
        setLastRatedAttributes(participantScore, lastRatedResult, participantScore.getExercise());

        var lastResult = getLastResultForParticipantScore(participantScore, resultIdsToIgnore).orElse(null);
        setLastAttributes(participantScore, lastResult, participantScore.getExercise());

        // Persist the changes or delete the participant score if it is not needed anymore
        if (participantScore.getLastRatedResult() == null && participantScore.getLastResult() == null) {
            if (participantScore.getId() != null) {
                // Delete the participant score if it exists in the database
                participantScoreRepository.delete(participantScore);
                logger.debug("Deleted participant score {}.", participantScore.getId());
            }
        }
        else {
            participantScoreRepository.save(participantScore);
            logger.debug("Updated participant score {}.", participantScore.getId());
        }
    }

    /**
     * Get the result that can replace the currently set last result for a participant score
     *
     * @author Stefan Waldhauser
     * @param participantScore the participant score to update (user/team and exercise must be set)
     * @param resultIdsToIgnore a list of ids to ignore when fetching the last result
     * @return optional of new result
     */
    private Optional<Result> getLastResultForParticipantScore(ParticipantScore participantScore, Long[] resultIdsToIgnore) {
        // the new last result (result with the highest id of submission with the highest id) will be at the beginning of the list
        Optional<Result> resultOrdered;
        if (participantScore instanceof StudentScore studentScore) {
            resultOrdered = resultRepository
                    .getResultsOrderedByParticipationIdLegalSubmissionIdResultIdDescForStudent(participantScore.getExercise().getId(), studentScore.getUser().getId()).stream()
                    .filter(result -> Arrays.stream(resultIdsToIgnore).noneMatch(id -> id.equals(result.getId()))).findFirst();
        }
        else if (participantScore instanceof TeamScore teamScore) {
            resultOrdered = resultRepository
                    .getResultsOrderedByParticipationIdLegalSubmissionIdResultIdDescForTeam(participantScore.getExercise().getId(), teamScore.getTeam().getId()).stream()
                    .filter(result -> Arrays.stream(resultIdsToIgnore).noneMatch(id -> id.equals(result.getId()))).findFirst();
        }
        else {
            return Optional.empty();
        }
        return resultOrdered;
    }

    /**
     * Get the result that can replace the currently set last rated result for a participant score
     *
     * @author Stefan Waldhauser
     * @param participantScore the participant score to update (user/team and exercise must be set)
     * @param resultIdsToIgnore a list of ids to ignore when fetching the last rated result
     * @return optional of new result
     */
    private Optional<Result> getLastRatedResultForParticipantScore(ParticipantScore participantScore, Long[] resultIdsToIgnore) {
        // the new last rated result (rated result with the highest id of submission with the highest id) will be at the beginning of the list
        Optional<Result> ratedResultsOrdered;
        if (participantScore instanceof StudentScore studentScore) {
            ratedResultsOrdered = resultRepository
                    .getRatedResultsOrderedByParticipationIdLegalSubmissionIdResultIdDescForStudent(participantScore.getExercise().getId(), studentScore.getUser().getId()).stream()
                    .filter(result -> Arrays.stream(resultIdsToIgnore).noneMatch(id -> id.equals(result.getId()))).findFirst();
        }
        else if (participantScore instanceof TeamScore teamScore) {
            ratedResultsOrdered = resultRepository
                    .getRatedResultsOrderedByParticipationIdLegalSubmissionIdResultIdDescForTeam(participantScore.getExercise().getId(), teamScore.getTeam().getId()).stream()
                    .filter(result -> Arrays.stream(resultIdsToIgnore).noneMatch(id -> id.equals(result.getId()))).findFirst();
        }
        else {
            return Optional.empty();
        }
        return ratedResultsOrdered;
    }

    /**
     * @author Stefan Waldhauser
     */
    private void setLastAttributes(ParticipantScore associatedParticipantScore, Result newLastResult, Exercise exercise) {
        associatedParticipantScore.setLastResult(newLastResult);
        if (newLastResult == null) {
            associatedParticipantScore.setLastScore(null);
            associatedParticipantScore.setLastPoints(null);
        }
        else {
            associatedParticipantScore.setLastScore(newLastResult.getScore());
            associatedParticipantScore.setLastPoints(RoundingUtil.roundScoreSpecifiedByCourseSettings(newLastResult.getScore() * 0.01 * exercise.getMaxPoints(),
                    exercise.getCourseViaExerciseGroupOrCourseMember()));
        }
    }

    /**
     * @author Stefan Waldhauser
     */
    private void setLastRatedAttributes(ParticipantScore associatedParticipantScore, Result newLastRatedResult, Exercise exercise) {
        associatedParticipantScore.setLastRatedResult(newLastRatedResult);
        if (newLastRatedResult == null) {
            associatedParticipantScore.setLastRatedScore(null);
            associatedParticipantScore.setLastRatedPoints(null);
        }
        else {
            associatedParticipantScore.setLastRatedScore(newLastRatedResult.getScore());
            associatedParticipantScore.setLastRatedPoints(RoundingUtil.roundScoreSpecifiedByCourseSettings(newLastRatedResult.getScore() * 0.01 * exercise.getMaxPoints(),
                    exercise.getCourseViaExerciseGroupOrCourseMember()));
        }
    }

    /**
     * Each participant score can be uniquely identified by the combination of exercise id and participant id.
     * @param exerciseId the id of the exercise
     * @param participantId the id of the participant (user or team, depending on the exercise's setting)
     */
    public record ParticipantScoreId(Long exerciseId, Long participantId) {
    }
}
