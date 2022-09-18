package de.tum.in.www1.artemis.service.listeners;

import javax.persistence.PostPersist;
import javax.persistence.PostUpdate;
import javax.persistence.PreRemove;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.service.messaging.InstanceMessageSendService;

/**
 * Listener for updates on {@link Result} entities to update the {@link de.tum.in.www1.artemis.domain.scores.ParticipantScore}.
 * @see de.tum.in.www1.artemis.service.scheduled.ParticipantScoreSchedulerService
 */
@Component
public class ResultListener {

    private final Logger logger = LoggerFactory.getLogger(ResultListener.class);

    private InstanceMessageSendService instanceMessageSendService;

    /**
     * Empty constructor for Spring.
     */
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
    @PreRemove
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void updateParticipantScore(Result result) {
        logger.debug("ResultListener.updateParticipantScore");
        if (result.getParticipation() instanceof StudentParticipation participation) {
            logger.debug(participation.getExercise().getId() + " - " + participation.getParticipant().getId());
            instanceMessageSendService.sendResultSchedule(participation.getExercise().getId(), participation.getParticipant().getId());
        }
        else {
            logger.error("Broken :(");
        }
    }
}
