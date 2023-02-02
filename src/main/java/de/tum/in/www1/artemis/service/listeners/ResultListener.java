package de.tum.in.www1.artemis.service.listeners;

import javax.persistence.PostPersist;
import javax.persistence.PostUpdate;
import javax.persistence.PreRemove;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.service.messaging.InstanceMessageSendService;
import de.tum.in.www1.artemis.service.scheduled.ParticipantScoreScheduleService;

/**
 * Listener for updates on {@link Result} entities to update the {@link de.tum.in.www1.artemis.domain.scores.ParticipantScore}.
 *
 * @see ParticipantScoreScheduleService
 */
@Component
public class ResultListener {

    private InstanceMessageSendService instanceMessageSendService;

    public ResultListener() {
        // Empty constructor for Spring
    }

    @Autowired
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
