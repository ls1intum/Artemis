package de.tum.cit.aet.artemis.assessment;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import jakarta.persistence.PostPersist;
import jakarta.persistence.PostUpdate;
import jakarta.persistence.PreRemove;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.assessment.domain.ParticipantScore;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.service.ParticipantScoreScheduleService;
import de.tum.cit.aet.artemis.core.service.messaging.InstanceMessageSendService;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.UserStoryExercise;

/**
 * Listener for updates on {@link Result} entities to update the {@link ParticipantScore}.
 * <p>
 * This class uses {@code @Lazy} on the constructor parameter because JPA entity listeners are
 * instantiated by Hibernate during EntityManagerFactory construction, before the full Spring
 * context is available. The lazy proxy breaks the circular dependency chain that would otherwise
 * occur (EntityManagerFactory → ResultListener → Services → Repositories → EntityManagerFactory).
 * <p>
 * Note: This is an intentional exception to the architecture rule that forbids {@code @Lazy} on
 * parameters. JPA entity listeners are a special case where this pattern is necessary.
 *
 * @see ParticipantScoreScheduleService
 */
@Profile(PROFILE_CORE)
@Component
@Lazy
public class ResultListener {

    private static final Logger log = LoggerFactory.getLogger(ResultListener.class);

    private final InstanceMessageSendService instanceMessageSendService;

    public ResultListener(@Lazy InstanceMessageSendService instanceMessageSendService) {
        this.instanceMessageSendService = instanceMessageSendService;
    }

    /**
     * This callback method is called after a result is created or updated.
     * It will forward the event to the messaging service to process it for the participant scores.
     *
     * @param result the result that was modified
     */
    @PostPersist
    @PostUpdate
    public void createOrUpdateResult(Result result) {
        if (result.getSubmission() != null && result.getSubmission().getParticipation() instanceof StudentParticipation participation) {
            instanceMessageSendService.sendParticipantScoreSchedule(participation.getExercise().getId(), participation.getParticipant().getId(), null);
            scheduleMilestoneScore(participation);
        }
    }

    /**
     * This callback method is called before a result is deleted.
     * It will forward the event to the messaging service to process it for the participant scores.
     *
     * @param result the result that is about to be deleted
     */
    @PreRemove
    public void removeResult(Result result) {
        // We can not retrieve the participation in a @PostRemove callback, so we use @PreRemove here
        // Then, we pass the result id to the scheduler to assure it is not used during the calculation of the new score
        // If the participation does not exist, we assume it will be deleted as well (no need to update the score in that case)
        if (result.getSubmission() != null && result.getSubmission().getParticipation() instanceof StudentParticipation participation && participation.getParticipant() != null) {
            instanceMessageSendService.sendParticipantScoreSchedule(participation.getExercise().getId(), participation.getParticipant().getId(), result.getId());
            // The milestone aggregate recomputes from whatever results still exist, so a deletion needs no special
            // handling beyond being told to run again.
            scheduleMilestoneScore(participation);
        }
    }

    /**
     * When a {@link UserStoryExercise} result changes, the aggregated score on the group's milestone exercise - the sum
     * of the student's user story points minus the group's static code analysis penalty - is out of date and has to be
     * recomputed.
     * <p>
     * Deliberately restricted to user story results: the recomputation writes the milestone's own result, so reacting to
     * milestone results here would make it re-trigger itself forever. The check is safe on a lazily loaded participation
     * because {@code Participation.exercise} is deliberately {@code EAGER} so that subclass checks work (see the note on
     * the field).
     * <p>
     * <b>This method runs inside the flush that is persisting the result, so it must not touch the database.</b> Neither
     * the story's owning group (a lazy association) nor anything else may be resolved here: a query re-enters the
     * session while Hibernate is iterating its own action queue, which fails the flush and rolls the result back,
     * leaving the student's build with a submission and no result. Only the story's id is passed on;
     * {@code MilestoneScoreScheduleService} resolves the owning milestone on a scheduler thread.
     * <p>
     * A milestone group is individual-participation only (see {@code ParticipationService}), so a team participation is
     * ignored - it can never contribute to a milestone aggregate.
     *
     * @param participation the participation of the result that changed
     */
    private void scheduleMilestoneScore(StudentParticipation participation) {
        if (!(participation.getExercise() instanceof UserStoryExercise userStoryExercise)) {
            return;
        }
        try {
            participation.getStudent().ifPresent(student -> instanceMessageSendService.sendMilestoneScoreSchedule(userStoryExercise.getId(), student.getId()));
        }
        catch (Exception exception) {
            // Never let a scheduling problem cost a student their result: this runs inside the flush that persists it.
            // The milestone is simply left stale until the minutely sweep in MilestoneScoreScheduleService finds it.
            log.warn("Could not schedule a milestone score update for user story exercise {}", userStoryExercise.getId(), exception);
        }
    }
}
