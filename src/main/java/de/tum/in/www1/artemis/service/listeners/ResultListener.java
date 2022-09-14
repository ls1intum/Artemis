package de.tum.in.www1.artemis.service.listeners;

import javax.persistence.PostPersist;
import javax.persistence.PostRemove;
import javax.persistence.PostUpdate;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.service.messaging.InstanceMessageSendService;

@Component
public class ResultListener {

    private final InstanceMessageSendService instanceMessageSendService;

    public ResultListener(@Lazy InstanceMessageSendService instanceMessageSendService) {
        this.instanceMessageSendService = instanceMessageSendService;
    }

    @PostPersist
    @PostUpdate
    @PostRemove
    public void updateParticipantScore(Result result) {
        instanceMessageSendService.sendResultSchedule(result.getId());
    }
}
