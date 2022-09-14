package de.tum.in.www1.artemis.service.scheduled;

import static de.tum.in.www1.artemis.service.util.RoundingUtil.roundScoreSpecifiedByCourseSettings;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ScheduledFuture;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.scores.ParticipantScore;
import de.tum.in.www1.artemis.domain.scores.StudentScore;
import de.tum.in.www1.artemis.domain.scores.TeamScore;
import de.tum.in.www1.artemis.repository.ParticipantScoreRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;

@Service
@Profile("scheduling")
public class ParticipantScoreSchedulerService {

    private final Logger logger = LoggerFactory.getLogger(ParticipantScoreSchedulerService.class);

    private final TaskScheduler scheduler;

    private final Environment env;

    private final Map<Long, ScheduledFuture<?>> scheduledParticipantScores = new HashMap<>();

    private Optional<Instant> lastSchedulingRun = Optional.empty();

    private final ParticipantScoreRepository participantScoreRepository;

    private final ResultRepository resultRepository;

    public ParticipantScoreSchedulerService(@Qualifier("taskScheduler") TaskScheduler scheduler, Environment env, ParticipantScoreRepository participantScoreRepository,
            ResultRepository resultRepository) {
        this.scheduler = scheduler;
        this.env = env;
        this.participantScoreRepository = participantScoreRepository;
        this.resultRepository = resultRepository;
    }

    @PostConstruct
    public void startSchedule() {
        try {
            Collection<String> activeProfiles = Arrays.asList(env.getActiveProfiles());

            if (!activeProfiles.contains("scheduling")) {
                // only execute this on server with active scheduling profile
                return;
            }

            scheduleOutdatedParticipantScores();
        }
        catch (Exception e) {
            logger.error("Failed to start ParticipantScoreScheduler", e);
        }
    }

    @Scheduled(cron = "0 0 * * * *")
    protected void scheduleOutdatedParticipantScores() {
        SecurityUtils.setAuthorizationObject();

        // Find all results that were added after the last run (on startup: last time we modified a participant score)
        var latestRun = lastSchedulingRun.orElseGet(() -> participantScoreRepository.getLatestModifiedDate().orElse(Instant.now()));
        // Remove one minute, because we query the database now but update the participant scores async in the future
        var latestRunDateTime = ZonedDateTime.from(latestRun.atZone(ZoneId.systemDefault())).minus(1, ChronoUnit.MINUTES);
        var resultsToProcess = resultRepository.findByCompletionDateGreaterThanEqual(latestRunDateTime);
        resultsToProcess.forEach(result -> {
            scheduleTask(result.getId());
        });
        lastSchedulingRun = Optional.of(Instant.now());
    }

    public void scheduleTask(Long resultId) {
        if (scheduledParticipantScores.containsKey(resultId)) {
            // We already have a scheduled task for this result
            return;
        }

        var schedulingTime = ZonedDateTime.now().plus(1, ChronoUnit.SECONDS);
        var future = scheduler.schedule(() -> this.executeTask(resultId), schedulingTime.toInstant());
        scheduledParticipantScores.put(resultId, future);
        logger.info("Schedule task for {} at {}.", resultId, schedulingTime);
    }

    private void executeTask(Long resultId) {
        logger.info("Processing {} at {}.", resultId, ZonedDateTime.now());
        SecurityUtils.setAuthorizationObject();

        var result = resultRepository.findByIdWithEagerParticipationAndSubmissionsAndExercise(resultId).orElse(null);

        if (!(result.getParticipation() instanceof StudentParticipation participation)) {
            // We are only interested in student participations
            return;
        }

        var participantScore = participantScoreRepository.findByResultIdWithEagerResults(resultId);

        if (result == null) {
            // The result was deleted, we need to check if we have a participant score for it
            participantScore.ifPresent(score -> {
                // There are two possibilities now:
                // A) Another (older) result exists for the exercise and the student/team -> update participant score
                // B) No other result exists for the exercise and the student/team -> remove participant score

                var updatedScore = updateParticipantScore(resultId, score, participation.getExercise());
                if (updatedScore.getLastRatedResult() == null && updatedScore.getLastResult() == null) {
                    // Case B) No other results exist, so we delete the participant score
                    participantScoreRepository.delete(updatedScore);
                }
                else {
                    // Case A) The participant score was updated with another (older) result, so we save it
                    participantScoreRepository.save(updatedScore);
                }
            });
            return;
        }

        // The result was updated or created, we need to create or update the associated participant score
        var score = participantScore.orElseGet(() -> {
            if (participation.getExercise().isTeamMode()) {
                var teamScore = new TeamScore();
                teamScore.setTeam(participation.getTeam().get());
                teamScore.setExercise(participation.getExercise());
                return teamScore;
            }
            else {
                var studentScore = new StudentScore();
                studentScore.setUser(participation.getStudent().get());
                studentScore.setExercise(participation.getExercise());
                return studentScore;
            }
        });

        participantScoreRepository.save(updateParticipantScore(resultId, score, participation.getExercise()));

        scheduledParticipantScores.remove(resultId);
    }

    private ParticipantScore updateParticipantScore(Long resultId, ParticipantScore participantScore, Exercise exercise) {
        if (participantScore.getLastRatedResult() == null || participantScore.getLastRatedResult().getId().equals(resultId)) {
            var lastRatedResult = getNewLastRatedResultForParticipantScore(participantScore).orElse(null);
            setLastRatedAttributes(participantScore, lastRatedResult, exercise);
        }
        if (participantScore.getLastResult() == null || participantScore.getLastResult().getId().equals(resultId)) {
            var lastResult = getNewLastResultForParticipantScore(participantScore).orElse(null);
            setLastAttributes(participantScore, lastResult, exercise);
        }
        return participantScore;
    }

    /**
     * Get the result that can replace the currently set last result for a participant score
     *
     * @author Stefan Waldhauser
     * @param participantScore participant score
     * @return optional of new result
     */
    private Optional<Result> getNewLastResultForParticipantScore(ParticipantScore participantScore) {
        List<Result> resultOrdered;
        if (participantScore.getClass().equals(StudentScore.class)) {
            StudentScore studentScore = (StudentScore) participantScore;
            resultOrdered = resultRepository
                    .getResultsOrderedByParticipationIdLegalSubmissionIdResultIdDescForStudent(participantScore.getExercise().getId(), studentScore.getUser().getId()).stream()
                    .toList();
        }
        else {
            TeamScore teamScore = (TeamScore) participantScore;
            resultOrdered = resultRepository
                    .getResultsOrderedByParticipationIdLegalSubmissionIdResultIdDescForTeam(participantScore.getExercise().getId(), teamScore.getTeam().getId()).stream().toList();
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
    private Optional<Result> getNewLastRatedResultForParticipantScore(ParticipantScore participantScore) {
        List<Result> ratedResultsOrdered;
        if (participantScore.getClass().equals(StudentScore.class)) {
            StudentScore studentScore = (StudentScore) participantScore;
            ratedResultsOrdered = resultRepository
                    .getRatedResultsOrderedByParticipationIdLegalSubmissionIdResultIdDescForStudent(participantScore.getExercise().getId(), studentScore.getUser().getId()).stream()
                    .toList();
        }
        else {
            TeamScore teamScore = (TeamScore) participantScore;
            ratedResultsOrdered = resultRepository
                    .getRatedResultsOrderedByParticipationIdLegalSubmissionIdResultIdDescForTeam(participantScore.getExercise().getId(), teamScore.getTeam().getId()).stream()
                    .toList();
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
            associatedParticipantScore.setLastPoints(
                    roundScoreSpecifiedByCourseSettings(newLastResult.getScore() * 0.01 * exercise.getMaxPoints(), exercise.getCourseViaExerciseGroupOrCourseMember()));
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
            associatedParticipantScore.setLastRatedPoints(
                    roundScoreSpecifiedByCourseSettings(newLastRatedResult.getScore() * 0.01 * exercise.getMaxPoints(), exercise.getCourseViaExerciseGroupOrCourseMember()));
        }
    }
}
