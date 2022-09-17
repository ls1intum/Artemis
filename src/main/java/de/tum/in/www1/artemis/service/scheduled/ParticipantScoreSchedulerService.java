package de.tum.in.www1.artemis.service.scheduled;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ScheduledFuture;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Result;
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

    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new HashMap<>();

    private Optional<Instant> lastScheduledRun = Optional.empty();

    private final ParticipantScoreRepository participantScoreRepository;

    private final StudentScoreRepository studentScoreRepository;

    private final TeamScoreRepository teamScoreRepository;

    private final ParticipationRepository participationRepository;

    private final ResultRepository resultRepository;

    public ParticipantScoreSchedulerService(@Qualifier("taskScheduler") TaskScheduler scheduler, ParticipantScoreRepository participantScoreRepository,
            StudentScoreRepository studentScoreRepository, TeamScoreRepository teamScoreRepository, ParticipationRepository participationRepository,
            ResultRepository resultRepository) {
        this.scheduler = scheduler;
        this.participantScoreRepository = participantScoreRepository;
        this.studentScoreRepository = studentScoreRepository;
        this.teamScoreRepository = teamScoreRepository;
        this.participationRepository = participationRepository;
        this.resultRepository = resultRepository;
    }

    public Map<Long, ScheduledFuture<?>> getScheduledTasks() {
        return scheduledTasks;
    }

    /**
     * Schedule all outdated participant scores when the service is started.
     */
    @PostConstruct
    public void startSchedule() {
        try {
            scheduleParticipationsToProgress();
        }
        catch (Exception e) {
            logger.error("Failed to start ParticipantScoreScheduler", e);
        }
    }

    /**
     * Before shutdown, cancel all running or scheduled tasks.
     */
    @PreDestroy
    public void stopSchedule() {
        // Stop all running tasks, we will reschedule them on startup again
        scheduledTasks.values().forEach(future -> {
            future.cancel(true);
        });
        scheduledTasks.clear();
    }

    /**
     * Every minute, query for modified results and run a task to update the participant scores.
     * We schedule all results that were created/updated since the last run of the cron job.
     * In the runner, we check if the participant score was modified after the result, then we can skip it.
     * (That means the listener did its job. This cron is just for extra safety.)
     */
    @Scheduled(cron = "0 * * * * *")
    protected void scheduleParticipationsToProgress() {
        SecurityUtils.setAuthorizationObject();

        // Find all results that were added after the last run (on startup: last time we modified a participant score)
        var latestRun = lastScheduledRun.orElseGet(() -> participantScoreRepository.getLatestModifiedDate().orElse(Instant.now()));
        // Update last run time before we continue with time-consuming operations
        lastScheduledRun = Optional.of(Instant.now());

        var resultsToProcess = resultRepository.findAllByLastModifiedDateAfter(latestRun);
        resultsToProcess.forEach(result -> {
            scheduleTask(result.getParticipation().getId());
        });
    }

    /**
     * Every hour, query for outdated participant scores (where one of the results is null) and update them.
     * This can only happen if a result was deleted, but the participant score was not updated.
     * (Possibly, the main instance with the scheduler was down at that time.)
     */
    @Scheduled(cron = "0 0 * * * *")
    protected void cleanupOutdatedParticipantScores() {
        SecurityUtils.setAuthorizationObject();

        var participantScoresToProcess = participantScoreRepository.findAllOutdatedWithExercise();
        participantScoresToProcess.forEach(this::updateParticipantScore);
    }

    /**
     * Schedule a task to update the participant score for the given result.
     * @param participationId the id of the result that was created/updated/deleted
     * @see de.tum.in.www1.artemis.service.messaging.InstanceMessageReceiveService#processScheduleResult(Long)
     */
    public void scheduleTask(Long participationId) {
        if (scheduledTasks.containsKey(participationId)) {
            // We already have a scheduled task for this result
            return;
        }

        var schedulingTime = ZonedDateTime.now().plus(500, ChronoUnit.MILLIS);
        var future = scheduler.schedule(() -> this.executeTask(participationId), schedulingTime.toInstant());
        scheduledTasks.put(participationId, future);
        logger.info("Schedule task for participation {} at {}.", participationId, schedulingTime);
    }

    /**
     * Execute the task to update the participant score for the given result.
     * @param participationId the id of the result that was created/updated/deleted
     */
    private void executeTask(Long participationId) {
        logger.debug("Processing participation {} to update participant scores.", participationId);
        try {
            SecurityUtils.setAuthorizationObject();

            var participation = participationRepository.findById(participationId).orElse(null);

            if (!(participation instanceof StudentParticipation studentParticipation)) {
                logger.warn("Participation {} not found or not a student participation.", participationId);
                return;
            }

            Optional<ParticipantScore> participantScore;
            if (studentParticipation.getExercise().isTeamMode()) {
                participantScore = teamScoreRepository.findTeamScoreByExerciseAndTeam(studentParticipation.getExercise(), studentParticipation.getTeam().get()).map(score -> score);
            }
            else {
                participantScore = studentScoreRepository.findStudentScoreByExerciseAndUser(studentParticipation.getExercise(), studentParticipation.getStudent().get())
                        .map(score -> score);
            }

            logger.debug(String.valueOf(participantScore));

            // The result was updated or created, we need to create or update the associated participant score
            var score = participantScore.orElseGet(() -> {
                if (studentParticipation.getExercise().isTeamMode()) {
                    var teamScore = new TeamScore();
                    teamScore.setTeam(studentParticipation.getTeam().get());
                    teamScore.setExercise(studentParticipation.getExercise());
                    return teamScore;
                }
                else {
                    var studentScore = new StudentScore();
                    studentScore.setUser(studentParticipation.getStudent().get());
                    studentScore.setExercise(studentParticipation.getExercise());
                    return studentScore;
                }
            });

            updateParticipantScore(score);
        }
        catch (Exception e) {
            logger.error("Exception while processing participation {} for participant scores: {}", participationId, e);
        }
        finally {
            scheduledTasks.remove(participationId);
        }
    }

    /**
     * Updates the given participant score by fetching the last (rated) results from the database.
     * If both no result and no rated result is found, the participant score is deleted.
     * @param participantScore The participant score to update (with the exercise eager loaded)
     */
    private void updateParticipantScore(ParticipantScore participantScore) {
        var lastRatedResult = getLastRatedResultForParticipantScore(participantScore).orElse(null);
        setLastRatedAttributes(participantScore, lastRatedResult, participantScore.getExercise());

        var lastResult = getLastResultForParticipantScore(participantScore).orElse(null);
        setLastAttributes(participantScore, lastResult, participantScore.getExercise());

        // Persist the changes or delete the participant score if it is not needed anymore
        if (participantScore.getLastRatedResult() == null && participantScore.getLastResult() == null) {
            participantScoreRepository.delete(participantScore);
            logger.debug("Deleted participant score {}.", participantScore.getId());
        }
        else {
            participantScore = participantScoreRepository.save(participantScore);
            logger.debug("Updated participant score {}.", participantScore.getId());
            logger.debug(String.valueOf(participantScore));
        }
    }

    /**
     * Get the result that can replace the currently set last result for a participant score
     *
     * @author Stefan Waldhauser
     * @param participantScore participant score
     * @return optional of new result
     */
    private Optional<Result> getLastResultForParticipantScore(ParticipantScore participantScore) {
        List<Result> resultOrdered;
        if (participantScore instanceof StudentScore studentScore) {
            resultOrdered = resultRepository
                    .getResultsOrderedByParticipationIdLegalSubmissionIdResultIdDescForStudent(participantScore.getExercise().getId(), studentScore.getUser().getId()).stream()
                    .toList();
        }
        else if (participantScore instanceof TeamScore teamScore) {
            resultOrdered = resultRepository
                    .getResultsOrderedByParticipationIdLegalSubmissionIdResultIdDescForTeam(participantScore.getExercise().getId(), teamScore.getTeam().getId()).stream().toList();
        }
        else {
            return Optional.empty();
        }
        // the new last result (result with the highest id of submission with the highest id) will be at the beginning of the list
        return resultOrdered.isEmpty() ? Optional.empty() : Optional.of(resultOrdered.get(0));
    }

    /**
     * Get the result that can replace the currently set last rated result for a participant score
     *
     * @author Stefan Waldhauser
     * @param participantScore participant score
     * @return optional of new result
     */
    private Optional<Result> getLastRatedResultForParticipantScore(ParticipantScore participantScore) {
        List<Result> ratedResultsOrdered;
        if (participantScore instanceof StudentScore studentScore) {
            ratedResultsOrdered = resultRepository
                    .getRatedResultsOrderedByParticipationIdLegalSubmissionIdResultIdDescForStudent(participantScore.getExercise().getId(), studentScore.getUser().getId()).stream()
                    .toList();
        }
        else if (participantScore instanceof TeamScore teamScore) {
            ratedResultsOrdered = resultRepository
                    .getRatedResultsOrderedByParticipationIdLegalSubmissionIdResultIdDescForTeam(participantScore.getExercise().getId(), teamScore.getTeam().getId()).stream()
                    .toList();
        }
        else {
            return Optional.empty();
        }
        // the new last rated result (rated result with the highest id of submission with the highest id) will be at the beginning of the list
        return ratedResultsOrdered.isEmpty() ? Optional.empty() : Optional.of(ratedResultsOrdered.get(0));
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
}
