package de.tum.cit.aet.artemis.assessment.listener;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import jakarta.persistence.PostPersist;
import jakarta.persistence.PostUpdate;
import jakarta.persistence.PreRemove;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.assessment.domain.ParticipantScore;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.service.ParticipantScoreScheduleService;
import de.tum.cit.aet.artemis.core.service.messaging.InstanceMessageSendService;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;

/**
 * Listener for updates on {@link Result} entities to update the {@link ParticipantScore}.
 *
 * @see ParticipantScoreScheduleService
 */
@Profile(PROFILE_CORE)
@Component
public class ResultListener {

    private InstanceMessageSendService instanceMessageSendService;

    public ResultListener() {
        // Empty constructor for Spring
    }

    @Autowired // ok
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
        if (result.getParticipation() instanceof StudentParticipation participation) {
            instanceMessageSendService.sendParticipantScoreSchedule(participation.getExercise().getId(), participation.getParticipant().getId(), null);
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
        if (result.getParticipation() instanceof StudentParticipation participation) {
            instanceMessageSendService.sendParticipantScoreSchedule(participation.getExercise().getId(), participation.getParticipant().getId(), result.getId());
        }
    }
}
