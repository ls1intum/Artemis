package de.tum.in.www1.artemis.service.listeners;

import javax.persistence.PostPersist;
import javax.persistence.PostRemove;
import javax.persistence.PostUpdate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.service.messaging.InstanceMessageSendService;

/**
 * Listener for updates on {@link Result} entities to update the {@link de.tum.in.www1.artemis.domain.scores.ParticipantScore}.
 * @see de.tum.in.www1.artemis.service.scheduled.ParticipantScoreSchedulerService
 */
@Component
public class ResultListener {

    private InstanceMessageSendService instanceMessageSendService;

    public ResultListener() {
    }

    @Autowired
    public ResultListener(@Lazy InstanceMessageSendService instanceMessageSendService) {
        this.instanceMessageSendService = instanceMessageSendService;
    }

    /**
     * This callback method is called after a result is created, updated or deleted.
     * It will forward the event to the messaging service to process it for the participant scores.
     * @param result the result that was modified
     */
    @PostPersist
    @PostUpdate
    @PostRemove
    public void updateParticipantScore(Result result) {
        instanceMessageSendService.sendResultSchedule(result.getId());
    }
}
